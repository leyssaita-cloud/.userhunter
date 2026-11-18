package com.userhunter.app.checker

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class PageClassification {
    NOT_FOUND,
    TAKEN,
    BLOCKED,
    UNKNOWN
}

internal data class ParsedPage(
    val classification: PageClassification,
    val detail: String
)

internal object TikTokPageParser {
    fun parse(httpStatus: Int, html: String, requestedUsername: String): ParsedPage {
        val payload = html.lowercase(Locale.ROOT)
        if (httpStatus in TikTokMarkerConfig.BLOCKED_HTTP_CODES ||
            TikTokMarkerConfig.CAPTCHA_TEXT_MARKERS.any { payload.contains(it) }
        ) {
            return ParsedPage(PageClassification.BLOCKED, "TikTok returned a temporary restriction or verification page.")
        }
        val isHttpNotFound = httpStatus in TikTokMarkerConfig.NOT_FOUND_HTTP_CODES

        val embeddedJson = TikTokMarkerConfig.USER_DATA_SCRIPT_REGEX.find(html)?.groupValues?.getOrNull(1)
        val target = requestedUsername.lowercase(Locale.ROOT)
        val foundUniqueIds = mutableListOf<String>()

        if (!embeddedJson.isNullOrBlank()) {
            try {
                collectUniqueIds(JSONObject(embeddedJson), foundUniqueIds)
            } catch (_: Exception) {
                // TikTok's payload is not a stable public schema; regex fallback follows below.
            }
        }
        if (foundUniqueIds.isEmpty() && !embeddedJson.isNullOrBlank()) {
            TikTokMarkerConfig.UNIQUE_ID_REGEX.findAll(embeddedJson)
                .mapTo(foundUniqueIds) { it.groupValues[1] }
        }

        if (foundUniqueIds.any { it.equals(target, ignoreCase = true) }) {
            return ParsedPage(PageClassification.TAKEN, "Embedded TikTok user data matched the requested username.")
        }

        val statusCode = TikTokMarkerConfig.STATUS_CODE_REGEX.find(html)?.groupValues?.getOrNull(1)
        val hasNotFoundText = TikTokMarkerConfig.NOT_FOUND_TEXT_MARKERS.any { payload.contains(it) }
        val hasNotFoundStatus = statusCode in TikTokMarkerConfig.NOT_FOUND_STATUS_CODES
        if (hasNotFoundText || hasNotFoundStatus) {
            val evidence = when {
                isHttpNotFound -> "HTTP not-found status and page payload marker"
                hasNotFoundStatus -> "embedded TikTok not-found status marker"
                else -> "not-found text in page payload"
            }
            return ParsedPage(PageClassification.NOT_FOUND, "The response contained $evidence.")
        }
        if (isHttpNotFound) {
            return ParsedPage(
                PageClassification.UNKNOWN,
                "HTTP not-found status without a confirming TikTok page marker."
            )
        }

        // A different embedded user does not prove that the requested path is occupied.
        if (foundUniqueIds.isNotEmpty()) {
            return ParsedPage(PageClassification.UNKNOWN, "The page contained user data, but it did not match the requested name.")
        }
        return ParsedPage(PageClassification.UNKNOWN, "No reliable public-profile marker was found.")
    }

    private fun collectUniqueIds(value: Any?, output: MutableList<String>) {
        when (value) {
            is JSONObject -> {
                val uniqueId = value.optString(TikTokMarkerConfig.USERNAME_JSON_FIELD, "")
                val hasProfileIdentity = value.has(TikTokMarkerConfig.USER_ID_JSON_FIELD) ||
                    value.has(TikTokMarkerConfig.SECURE_UID_JSON_FIELD)
                if (uniqueId.isNotBlank() && hasProfileIdentity) output += uniqueId
                val keys = value.keys()
                while (keys.hasNext()) collectUniqueIds(value.opt(keys.next()), output)
            }
            is JSONArray -> for (index in 0 until value.length()) collectUniqueIds(value.opt(index), output)
        }
    }
}
