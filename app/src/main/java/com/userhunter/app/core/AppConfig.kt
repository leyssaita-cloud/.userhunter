package com.userhunter.app.core

/** Central username constraints. Keep all mutable product rules in this object. */
object AppConfig {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 24
    const val ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789_."
    const val DEFAULT_RECHECK_HOURS = 24
    const val DEFAULT_REQUESTS_PER_SECOND = 2.0
    const val DEFAULT_CONCURRENCY = 3
    const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L
    const val DEFAULT_READ_TIMEOUT_SECONDS = 15L
    const val DEFAULT_CONFIRMATION_DELAY_MILLIS = 2_500L
    const val BLOCKS_BEFORE_AUTO_PAUSE = 3
    const val MAX_BACKOFF_SECONDS = 120L

    const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val VOWELS = "aeiou"
    const val CONSONANTS = "bcdfghjklmnpqrstvwxyz"
}
