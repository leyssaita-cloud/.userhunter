package com.userhunter.app.checker

/**
 * TikTok page markers live in one place. TikTok can rename scripts, status codes, and copy at any
 * time. When detection changes, inspect a fresh public profile page and update only these values,
 * then extend TikTokPageParserTest fixtures. Never treat an unknown response as available.
 */
object TikTokMarkerConfig {
    val NOT_FOUND_HTTP_CODES = setOf(404, 410)
    val BLOCKED_HTTP_CODES = setOf(403, 429)
    val NOT_FOUND_STATUS_CODES = setOf("10221")

    val NOT_FOUND_TEXT_MARKERS = listOf(
        "couldn't find this account",
        "could not find this account",
        "this account doesn't exist",
        "account not found"
    )

    val CAPTCHA_TEXT_MARKERS = listOf(
        "captcha",
        "verify to continue",
        "verify you are human",
        "challenge-platform",
        "access denied",
        "too many requests"
    )

    const val USER_DATA_SCRIPT_ID = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
    const val USERNAME_JSON_FIELD = "uniqueId"
    const val USER_ID_JSON_FIELD = "id"
    const val SECURE_UID_JSON_FIELD = "secUid"

    // Script extraction and JSON-field expressions are deliberately centralized here.
    val USER_DATA_SCRIPT_REGEX = Regex(
        "<script[^>]*id=[\\\"']$USER_DATA_SCRIPT_ID[\\\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val STATUS_CODE_REGEX = Regex("\\\"statusCode\\\"\\s*:\\s*\\\"?(\\d+)\\\"?", RegexOption.IGNORE_CASE)
    val UNIQUE_ID_REGEX = Regex("\\\"uniqueId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
}
