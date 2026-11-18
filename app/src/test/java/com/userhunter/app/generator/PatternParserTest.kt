package com.userhunter.app.generator

import com.userhunter.app.core.CharsetOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternParserTest {
    @Test
    fun parsesAllMaskTokens() {
        val parsed = PatternParser.parse("L D V C ?", CharsetOptions(letters = true, digits = true))
        assertFalse(parsed.isValid)

        val compact = PatternParser.parse("LDVC?", CharsetOptions(letters = true, digits = true))
        assertTrue(compact.isValid)
        assertEquals(5, compact.tokens.size)
        assertEquals(PatternTokenKind.LETTER, compact.tokens[0].kind)
        assertEquals(PatternTokenKind.DIGIT, compact.tokens[1].kind)
        assertEquals(PatternTokenKind.VOWEL, compact.tokens[2].kind)
        assertEquals(PatternTokenKind.CONSONANT, compact.tokens[3].kind)
        assertEquals(PatternTokenKind.ANY, compact.tokens[4].kind)
    }

    @Test
    fun literalCharactersAreCheckedAgainstAllowedSet() {
        assertTrue(PatternParser.parse("a?b", CharsetOptions()).isValid)
        assertFalse(PatternParser.parse("a-$", CharsetOptions()).isValid)
        assertFalse(PatternParser.parse("ab.", CharsetOptions(period = true)).isValid)
        assertTrue(PatternParser.parse("va", CharsetOptions()).isValid)
    }
}
