package com.userhunter.app.checker

import com.userhunter.app.core.AppConfig
import com.userhunter.app.core.CheckResult
import com.userhunter.app.core.CheckState
import com.userhunter.app.core.UsernameValidation
import com.userhunter.app.core.UsernameValidator
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

class TikTokUsernameChecker(
    initialClient: OkHttpClient,
    private val rateLimiter: CheckRateLimiter
) : UsernameChecker {
    @Volatile private var client: OkHttpClient = initialClient
    private val globalConcurrency = Semaphore(AppConfig.DEFAULT_CONCURRENCY)

    fun configureTimeouts(connectSeconds: Int, readSeconds: Int) {
        client = client.newBuilder()
            .connectTimeout(connectSeconds.coerceIn(5, 60).toLong(), TimeUnit.SECONDS)
            .readTimeout(readSeconds.coerceIn(5, 60).toLong(), TimeUnit.SECONDS)
            .build()
    }

    override suspend fun check(username: String): CheckResult {
        val validation = UsernameValidator.validate(username)
        if (validation !is UsernameValidation.Valid) {
            return CheckResult(
                username = UsernameValidator.normalize(username),
                state = CheckState.INVALID,
                detail = "Username validation failed; no network request was made."
            )
        }

        val normalized = validation.normalized
        return globalConcurrency.withPermit {
            val first = requestOnce(normalized)
            when (first.classification) {
                PageClassification.TAKEN -> return@withPermit CheckResult(normalized, CheckState.TAKEN, first.status, first.detail)
                PageClassification.BLOCKED -> return@withPermit CheckResult(normalized, CheckState.BLOCKED, first.status, first.detail)
                PageClassification.UNKNOWN -> return@withPermit CheckResult(normalized, CheckState.UNKNOWN, first.status, first.detail)
                PageClassification.NOT_FOUND -> Unit
            }

            delay(AppConfig.DEFAULT_CONFIRMATION_DELAY_MILLIS)
            val second = requestOnce(normalized)
            when (second.classification) {
                PageClassification.NOT_FOUND -> CheckResult(
                    normalized,
                    CheckState.AVAILABLE_LIKELY,
                    second.status,
                    "Two consecutive not-found responses. Banned, reserved, or recently deleted names may still be unclaimable."
                )
                PageClassification.TAKEN -> CheckResult(
                    normalized,
                    CheckState.TAKEN,
                    second.status,
                    "The confirmation request found an existing profile."
                )
                PageClassification.BLOCKED -> CheckResult(normalized, CheckState.BLOCKED, second.status, second.detail)
                PageClassification.UNKNOWN -> CheckResult(normalized, CheckState.UNKNOWN, second.status, second.detail)
            }
        }
    }

    private suspend fun requestOnce(username: String): ProbeResult = withContext(Dispatchers.IO) {
        rateLimiter.awaitPermit()
        val request = Request.Builder()
            .url("https://www.tiktok.com/@$username")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val parsed = TikTokPageParser.parse(response.code, body, username)
                when (parsed.classification) {
                    PageClassification.BLOCKED -> {
                        val backoff = rateLimiter.registerBlock()
                        ProbeResult(PageClassification.BLOCKED, response.code, "Temporary restriction detected; shared backoff is ${backoff}s.")
                    }
                    else -> {
                        rateLimiter.registerSuccess()
                        ProbeResult(parsed.classification, response.code, parsed.detail)
                    }
                }
            }
        } catch (error: IOException) {
            ProbeResult(PageClassification.UNKNOWN, null, error.message ?: "Network I/O error.")
        } catch (error: Exception) {
            ProbeResult(PageClassification.UNKNOWN, null, error.message ?: "Unexpected response parsing error.")
        }
    }

    private data class ProbeResult(
        val classification: PageClassification,
        val status: Int?,
        val detail: String
    )
}
