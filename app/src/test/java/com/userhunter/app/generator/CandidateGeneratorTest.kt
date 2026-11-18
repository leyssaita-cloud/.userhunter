package com.userhunter.app.generator

import com.userhunter.app.core.CharsetOptions
import com.userhunter.app.core.GenerationMode
import com.userhunter.app.core.GenerationRequest
import com.userhunter.app.core.SpecialPattern
import com.userhunter.app.core.UsernameValidator
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateGeneratorTest {
    private val generator = CandidateGenerator()

    @Test
    fun sequentialCountAndBaseNMappingAreCorrect() {
        val request = GenerationRequest(
            lengths = setOf(2),
            charset = CharsetOptions(letters = false, digits = true),
            mode = GenerationMode.SEQUENTIAL
        )
        assertEquals(BigInteger.valueOf(100), generator.totalCount(request))
        assertEquals("00", generator.candidateAt(request, BigInteger.ZERO))
        assertEquals("01", generator.candidateAt(request, BigInteger.ONE))
        assertEquals("99", generator.candidateAt(request, BigInteger.valueOf(99)))
    }

    @Test
    fun resumeIndexReturnsSameCandidate() {
        val request = GenerationRequest(
            lengths = setOf(2, 3),
            charset = CharsetOptions(letters = true),
            mode = GenerationMode.SEQUENTIAL
        )
        val savedIndex = BigInteger.valueOf(731)
        val savedValue = generator.candidateAt(request, savedIndex)
        val resumedValue = generator.generate(request, savedIndex, 1).first().second
        assertEquals(savedValue, resumedValue)
    }

    @Test
    fun sequentialNamesNeverEndWithPeriod() {
        val request = GenerationRequest(
            lengths = setOf(2, 3),
            charset = CharsetOptions(letters = false, digits = false, period = true, underscore = true),
            mode = GenerationMode.SEQUENTIAL
        )
        generator.generate(request, limit = 100).forEach { (_, value) ->
            assertTrue(value.last() != '.')
            assertTrue(UsernameValidator.isValid(value))
        }
    }

    @Test
    fun repeatedAndPronounceablePatternsHaveExpectedShapes() {
        val request = GenerationRequest(
            lengths = setOf(4),
            charset = CharsetOptions(letters = true),
            mode = GenerationMode.SPECIAL,
            specialPattern = SpecialPattern.REPEATED
        )
        assertEquals("aaaa", generator.candidateAt(request, BigInteger.ZERO))

        val pronounceable = request.copy(specialPattern = SpecialPattern.PRONOUNCEABLE)
        val sample = generator.candidateAt(pronounceable, BigInteger.ZERO)
        assertNotNull(sample)
        assertEquals(4, sample!!.length)
        assertTrue(sample[0] in "bcdfghjklmnpqrstvwxyz")
        assertTrue(sample[1] in "aeiou")
    }

    @Test
    fun patternCountIsBoundedAndNeverEndsWithPeriod() {
        val request = GenerationRequest(
            lengths = setOf(2),
            charset = CharsetOptions(letters = true, period = true),
            mode = GenerationMode.PATTERN,
            patternMask = "??"
        )
        assertEquals(BigInteger.valueOf(26L * 27L), generator.totalCount(request))
        assertNotNull(generator.candidateAt(request, BigInteger.ZERO))
        assertNull(generator.candidateAt(request, generator.totalCount(request)))
        generator.generate(request, limit = 200).forEach { (_, value) ->
            assertTrue(value.last() != '.')
            assertTrue(UsernameValidator.isValid(value))
        }
    }

    @Test
    fun invalidPatternProducesNoCandidate() {
        val request = GenerationRequest(
            lengths = setOf(2),
            charset = CharsetOptions(letters = true),
            mode = GenerationMode.PATTERN,
            patternMask = "a?"
        )
        assertNotNull(generator.candidateAt(request, BigInteger.ZERO))
        assertNull(generator.candidateAt(request, BigInteger.valueOf(26)))
    }
}
