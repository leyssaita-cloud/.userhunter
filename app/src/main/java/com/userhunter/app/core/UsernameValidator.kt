package com.userhunter.app.core

import java.util.Locale

sealed interface UsernameValidation {
    data class Valid(val normalized: String) : UsernameValidation
    data class Invalid(val reason: Reason) : UsernameValidation

    enum class Reason {
        TOO_SHORT,
        TOO_LONG,
        INVALID_CHARACTER,
        TRAILING_PERIOD,
        EMPTY
    }
}

object UsernameValidator {
    fun normalize(input: String): String = input.lowercase(Locale.ROOT)

    fun validate(input: String): UsernameValidation {
        val username = normalize(input)
        if (username.isEmpty()) return UsernameValidation.Invalid(UsernameValidation.Reason.EMPTY)
        if (username.length < AppConfig.MIN_LENGTH) {
            return UsernameValidation.Invalid(UsernameValidation.Reason.TOO_SHORT)
        }
        if (username.length > AppConfig.MAX_LENGTH) {
            return UsernameValidation.Invalid(UsernameValidation.Reason.TOO_LONG)
        }
        if (username.last() == '.') {
            return UsernameValidation.Invalid(UsernameValidation.Reason.TRAILING_PERIOD)
        }
        if (username.any { it !in AppConfig.ALLOWED_CHARACTERS }) {
            return UsernameValidation.Invalid(UsernameValidation.Reason.INVALID_CHARACTER)
        }
        return UsernameValidation.Valid(username)
    }

    fun isValid(input: String): Boolean = validate(input) is UsernameValidation.Valid
}
