package com.userhunter.app.generator

import com.userhunter.app.core.AppConfig
import com.userhunter.app.core.CharsetOptions

enum class PatternTokenKind {
    ANY,
    LETTER,
    DIGIT,
    VOWEL,
    CONSONANT,
    LITERAL
}

data class PatternToken(
    val kind: PatternTokenKind,
    val literal: Char? = null
)

data class PatternParseResult(
    val tokens: List<PatternToken>,
    val error: String? = null
) {
    val isValid: Boolean get() = error == null && tokens.isNotEmpty()
}

object PatternParser {
    fun parse(mask: String, charset: CharsetOptions): PatternParseResult {
        val normalized = mask
        if (normalized.length !in AppConfig.MIN_LENGTH..AppConfig.MAX_LENGTH) {
            return PatternParseResult(emptyList(), "Pattern length must be ${AppConfig.MIN_LENGTH}..${AppConfig.MAX_LENGTH}.")
        }
        val selected = charset.characters().toSet()
        val tokens = normalized.map { char ->
            when (char) {
                '?' -> PatternToken(PatternTokenKind.ANY)
                'L' -> PatternToken(PatternTokenKind.LETTER)
                'D' -> PatternToken(PatternTokenKind.DIGIT)
                'V' -> PatternToken(PatternTokenKind.VOWEL)
                'C' -> PatternToken(PatternTokenKind.CONSONANT)
                else -> {
                    val literal = char.lowercaseChar()
                    if (literal !in AppConfig.ALLOWED_CHARACTERS) {
                        return PatternParseResult(emptyList(), "Invalid literal character: $char")
                    }
                    PatternToken(PatternTokenKind.LITERAL, literal)
                }
            }
        }
        val emptyToken = tokens.mapIndexedNotNull { position, token ->
            token.takeIf { choicesFor(it, selected, position == tokens.lastIndex).isEmpty() }
        }.firstOrNull()
        if (emptyToken != null) {
            return PatternParseResult(emptyList(), "The selected character sets do not satisfy this pattern.")
        }
        return PatternParseResult(tokens)
    }

    fun choicesFor(token: PatternToken, selected: Set<Char>, isLast: Boolean = false): List<Char> {
        val choices = when (token.kind) {
            PatternTokenKind.ANY -> AppConfig.ALLOWED_CHARACTERS.filter { it in selected }.toList()
            PatternTokenKind.LETTER -> AppConfig.LETTERS.filter { it in selected }.toList()
            PatternTokenKind.DIGIT -> AppConfig.DIGITS.filter { it in selected }.toList()
            PatternTokenKind.VOWEL -> AppConfig.VOWELS.filter { it in selected }.toList()
            PatternTokenKind.CONSONANT -> AppConfig.CONSONANTS.filter { it in selected }.toList()
            PatternTokenKind.LITERAL -> listOfNotNull(token.literal)
        }
        return if (isLast) choices.filterNot { it == '.' } else choices
    }
}
