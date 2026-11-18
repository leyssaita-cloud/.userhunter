package com.userhunter.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.userhunter.app.core.CheckState
import com.userhunter.app.core.CharsetOptions
import com.userhunter.app.core.CheckResult
import com.userhunter.app.core.GenerationMode
import com.userhunter.app.core.GenerationRequest
import com.userhunter.app.core.SpecialPattern
import com.userhunter.app.core.UsernameValidation
import com.userhunter.app.core.UsernameValidator
import com.userhunter.app.data.AppGraph
import com.userhunter.app.data.CheckedCacheEntity
import com.userhunter.app.data.HuntResultEntity
import com.userhunter.app.data.ResultWithFavorite
import com.userhunter.app.generator.PatternParser
import com.userhunter.app.generator.UsernameAnalyzer
import com.userhunter.app.service.HuntController
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

 data class HuntFormState(
    val lengths: Set<Int> = setOf(2, 3, 4),
    val charset: CharsetOptions = CharsetOptions(),
    val mode: GenerationMode = GenerationMode.SEQUENTIAL,
    val patternMask: String = "a??b",
    val specialPattern: SpecialPattern = SpecialPattern.PRONOUNCEABLE
) {
    fun toRequest(): GenerationRequest = GenerationRequest(
        lengths = lengths,
        charset = charset,
        mode = mode,
        patternMask = patternMask,
        specialPattern = specialPattern
    )
}

data class SelfTestState(
    val knownTaken: CheckResult? = null,
    val randomAvailable: CheckResult? = null,
    val busy: Boolean = false
) {
    val knownPassed: Boolean? get() = knownTaken?.let { it.state == CheckState.TAKEN }
    val randomPassed: Boolean? get() = randomAvailable?.let { it.state == CheckState.AVAILABLE_LIKELY }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = AppGraph.get(application)
    private val huntController = HuntController(application)

    val settings = graph.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.userhunter.app.data.AppSettings()
    )
    val results: StateFlow<List<ResultWithFavorite>> = graph.dao.observeResults().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val session = huntController.session.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    private val _form = MutableStateFlow(HuntFormState())
    val form: StateFlow<HuntFormState> = _form

    private val _quickUsername = MutableStateFlow("")
    val quickUsername: StateFlow<String> = _quickUsername

    private val _quickResult = MutableStateFlow<CheckResult?>(null)
    val quickResult: StateFlow<CheckResult?> = _quickResult

    private val _quickBusy = MutableStateFlow(false)
    val quickBusy: StateFlow<Boolean> = _quickBusy

    private val _selfTest = MutableStateFlow(SelfTestState())
    val selfTest: StateFlow<SelfTestState> = _selfTest

    private val _huntError = MutableStateFlow<String?>(null)
    val huntError: StateFlow<String?> = _huntError

    fun updateForm(transform: (HuntFormState) -> HuntFormState) {
        _form.value = transform(_form.value)
        _huntError.value = null
    }

    fun toggleLength(length: Int) = updateForm { current ->
        val updated = current.lengths.toMutableSet().apply {
            if (!add(length)) remove(length)
        }
        current.copy(lengths = updated)
    }

    fun setCharset(transform: (CharsetOptions) -> CharsetOptions) =
        updateForm { it.copy(charset = transform(it.charset)) }

    fun setQuickUsername(value: String) {
        _quickUsername.value = value
        _quickResult.value = null
    }

    fun startHunt() {
        val current = _form.value
        if (current.mode != GenerationMode.WORD_LIST && current.lengths.isEmpty()) {
            _huntError.value = "lengths"
            return
        }
        if (current.mode == GenerationMode.WORD_LIST && settings.value.importedWordListUri.isBlank()) {
            _huntError.value = "word_list"
            return
        }
        if (current.mode != GenerationMode.WORD_LIST && current.charset.characters().isEmpty()) {
            _huntError.value = "charset"
            return
        }
        if (current.mode == GenerationMode.PATTERN &&
            !PatternParser.parse(current.patternMask, current.charset).isValid
        ) {
            _huntError.value = "pattern"
            return
        }
        if (current.mode == GenerationMode.SPECIAL &&
            current.specialPattern == SpecialPattern.PRONOUNCEABLE &&
            !current.charset.letters
        ) {
            _huntError.value = "charset"
            return
        }
        viewModelScope.launch {
            huntController.start(current.toRequest(), settings.value.importedWordListUri)
        }
    }

    fun pauseHunt() = huntController.pause()
    fun resumeHunt() = huntController.resume()
    fun stopHunt() = huntController.stop()

    fun saveWordListUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            graph.settings.setWordListUri(uri.toString())
            updateForm { it.copy(mode = GenerationMode.WORD_LIST) }
        }
    }

    fun quickCheck() {
        val raw = _quickUsername.value
        val validation = UsernameValidator.validate(raw)
        if (validation !is UsernameValidation.Valid) {
            _quickResult.value = CheckResult(
                UsernameValidator.normalize(raw),
                CheckState.INVALID,
                detail = "Invalid username; the network was not contacted."
            )
            return
        }
        if (_quickBusy.value) return
        viewModelScope.launch {
            _quickBusy.value = true
            try {
                val prefs = settings.value
                graph.rateLimiter.configure(prefs.requestsPerSecond)
                graph.checker.configureTimeouts(prefs.connectTimeoutSeconds, prefs.readTimeoutSeconds)
                val result = graph.checker.check(validation.normalized)
                _quickResult.value = result
                val rows = mutableListOf<HuntResultEntity>()
                if (result.state == CheckState.AVAILABLE_LIKELY) {
                    val candidate = UsernameAnalyzer.analyze(result.username)
                    rows += HuntResultEntity(
                        username = candidate.username,
                        rarity = candidate.rarity,
                        tagsCsv = candidate.tags.joinToString(","),
                        length = candidate.username.length,
                        discoveredAt = System.currentTimeMillis(),
                        lastCheckedAt = result.checkedAt
                    )
                }
                graph.dao.recordBatch(
                    listOf(
                        CheckedCacheEntity(
                            result.username,
                            result.state.name,
                            result.checkedAt,
                            result.httpStatus,
                            result.detail
                        )
                    ),
                    rows
                )
            } finally {
                _quickBusy.value = false
            }
        }
    }

    fun toggleFavorite(username: String, favorite: Boolean) {
        viewModelScope.launch { graph.dao.setFavorite(username, favorite) }
    }

    fun clearCheckedCache() {
        viewModelScope.launch { graph.dao.clearCheckedCache() }
    }

    fun setLanguage(value: String) = viewModelScope.launch { graph.settings.setLanguage(value) }
    fun setDarkTheme(value: Boolean) = viewModelScope.launch { graph.settings.setDarkTheme(value) }
    fun setSpeed(value: Double) = viewModelScope.launch { graph.settings.setSpeed(value) }
    fun setConcurrency(value: Int) = viewModelScope.launch { graph.settings.setConcurrency(value) }
    fun setConnectTimeout(value: Int) = viewModelScope.launch { graph.settings.setConnectTimeout(value) }
    fun setReadTimeout(value: Int) = viewModelScope.launch { graph.settings.setReadTimeout(value) }
    fun setRecheckHours(value: Int) = viewModelScope.launch { graph.settings.setRecheckHours(value) }
    fun setAutoResume(value: Boolean) = viewModelScope.launch { graph.settings.setAutoResume(value) }

    fun runSelfTest() {
        if (_selfTest.value.busy) return
        viewModelScope.launch {
            _selfTest.value = SelfTestState(busy = true)
            val prefs = settings.value
            graph.rateLimiter.configure(prefs.requestsPerSecond)
            graph.checker.configureTimeouts(prefs.connectTimeoutSeconds, prefs.readTimeoutSeconds)
            val known = graph.checker.check("tiktok")
            val randomName = buildString(20) {
                val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
                val random = SecureRandom()
                repeat(20) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            val random = graph.checker.check(randomName)
            _selfTest.value = SelfTestState(knownTaken = known, randomAvailable = random, busy = false)
        }
    }

    suspend fun exportResults(uri: Uri, usernames: List<String>, asCsv: Boolean) {
        withContext(Dispatchers.IO) {
            val output = getApplication<Application>().contentResolver.openOutputStream(uri) ?: return@withContext
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                if (asCsv) {
                    writer.appendLine("username,rarity,tags,length")
                    val lookup = results.value.associateBy { it.result.username }
                    usernames.forEach { username ->
                        val row = lookup[username]?.result ?: return@forEach
                        writer.appendLine(
                            listOf(row.username, row.rarity.toString(), row.tagsCsv, row.length.toString())
                                .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                        )
                    }
                } else {
                    usernames.forEach { writer.appendLine(it) }
                }
            }
        }
    }
}
