package com.userhunter.app.core

import java.math.BigInteger

/** States intentionally avoid claiming that TikTok guarantees a name can be claimed. */
enum class CheckState {
    AVAILABLE_LIKELY,
    TAKEN,
    INVALID,
    BLOCKED,
    UNKNOWN
}

data class CheckResult(
    val username: String,
    val state: CheckState,
    val httpStatus: Int? = null,
    val detail: String = "",
    val checkedAt: Long = System.currentTimeMillis()
)

enum class GenerationMode {
    RANDOM,
    SEQUENTIAL,
    PATTERN,
    SPECIAL,
    WORD_LIST
}

enum class SpecialPattern {
    REPEATED,
    PALINDROME,
    DOUBLES,
    ALTERNATING,
    PRONOUNCEABLE
}

data class CharsetOptions(
    val letters: Boolean = true,
    val digits: Boolean = false,
    val underscore: Boolean = false,
    val period: Boolean = false
) {
    fun characters(): String = buildString {
        if (letters) append(AppConfig.LETTERS)
        if (digits) append(AppConfig.DIGITS)
        if (underscore) append('_')
        if (period) append('.')
    }.toList().distinct().joinToString(separator = "")
}

data class GenerationRequest(
    val lengths: Set<Int> = setOf(2, 3, 4),
    val charset: CharsetOptions = CharsetOptions(),
    val mode: GenerationMode = GenerationMode.SEQUENTIAL,
    val patternMask: String = "",
    val specialPattern: SpecialPattern = SpecialPattern.PRONOUNCEABLE,
    val randomSeed: Long = 7_314_159L
) {
    fun validatedLengths(): List<Int> = lengths
        .filter { it in AppConfig.MIN_LENGTH..AppConfig.MAX_LENGTH }
        .distinct()
        .sorted()
}

data class CandidateInfo(
    val username: String,
    val rarity: Int,
    val tags: List<String>
)

data class HuntStats(
    val checked: Long = 0,
    val found: Long = 0,
    val taken: Long = 0,
    val blocked: Long = 0,
    val errors: Long = 0,
    val requestsPerSecond: Double = 0.0,
    val nextIndex: String = "0",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val pauseReason: String? = null
) {
    val progressLabel: String get() = "${checked} / ${found}"
}

/** BigInteger keeps combinatorial cursor sizes exact for longer usernames. */
data class GeneratorPage(
    val values: List<CandidateInfo>,
    val nextIndex: BigInteger
)
