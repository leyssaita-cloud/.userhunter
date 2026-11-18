package com.userhunter.app.generator

import com.userhunter.app.core.AppConfig
import com.userhunter.app.core.CandidateInfo

object UsernameAnalyzer {
    fun analyze(username: String): CandidateInfo {
        val name = username.lowercase()
        val tags = linkedSetOf<String>()
        if (name.all { it in AppConfig.LETTERS }) tags += "letters-only"
        if (name.all { it in AppConfig.DIGITS }) tags += "digits-only"
        if (name.any { it == '_' }) tags += "underscore"
        if (name.any { it == '.' }) tags += "period"
        if (name.all { it !in AppConfig.LETTERS || it in AppConfig.VOWELS }) tags += "vowel-heavy"
        if (isPalindrome(name)) tags += "palindrome"
        if (name.toSet().size == 1) tags += "same-letter"
        if (isDoubles(name)) tags += "doubles"
        if (isAlternating(name)) tags += "alternating"
        if (isPronounceable(name)) tags += "pronounceable"

        var score = when (name.length) {
            2 -> 95
            3 -> 82
            4 -> 68
            else -> (66 - (name.length - 4) * 2).coerceIn(8, 65)
        }
        if ("palindrome" in tags) score += 9
        if ("same-letter" in tags) score += 12
        if ("doubles" in tags) score += 6
        if ("alternating" in tags) score += 6
        if ("pronounceable" in tags) score += 5
        if ('_' in name || '.' in name) score -= 8
        if (name.any { it.isDigit() }) score -= 4
        return CandidateInfo(name, score.coerceIn(0, 100), tags.toList())
    }

    private fun isPalindrome(value: String): Boolean = value == value.reversed()

    private fun isDoubles(value: String): Boolean = value.length >= 2 && value.length % 2 == 0 &&
        value.chunked(2).all { it.length == 2 && it[0] == it[1] }

    private fun isAlternating(value: String): Boolean = value.length >= 4 &&
        value.indices.all { value[it] == value[it % 2] }

    private fun isPronounceable(value: String): Boolean {
        if (value.isEmpty() || value.any { it !in AppConfig.LETTERS }) return false
        return value.zipWithNext().all { (left, right) ->
            (left in AppConfig.CONSONANTS && right in AppConfig.VOWELS) ||
                (left in AppConfig.VOWELS && right in AppConfig.CONSONANTS)
        }
    }
}
