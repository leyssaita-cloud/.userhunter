package com.userhunter.app.generator

import com.userhunter.app.core.AppConfig
import com.userhunter.app.core.CharsetOptions
import com.userhunter.app.core.GenerationMode
import com.userhunter.app.core.GenerationRequest
import com.userhunter.app.core.SpecialPattern
import com.userhunter.app.core.UsernameValidator
import java.math.BigInteger
import java.util.Random

/**
 * Stateless, lazy candidate mapper. Sequential/pattern/special candidates are addressed by index,
 * so the cursor can be persisted and resumed without materializing a combinatorial list.
 */
class CandidateGenerator {
    fun totalCount(request: GenerationRequest): BigInteger {
        val chars = request.charset.characters()
        if (chars.isEmpty()) return BigInteger.ZERO
        return when (request.mode) {
            GenerationMode.RANDOM -> BigInteger.valueOf(Long.MAX_VALUE)
            GenerationMode.PATTERN -> {
                val parsed = PatternParser.parse(request.patternMask, request.charset)
                if (!parsed.isValid) BigInteger.ZERO else countPattern(parsed.tokens, request.charset)
            }
            GenerationMode.SEQUENTIAL -> request.validatedLengths()
                .fold(BigInteger.ZERO) { acc, length -> acc + sequentialCount(length, chars) }
            GenerationMode.SPECIAL -> request.validatedLengths()
                .fold(BigInteger.ZERO) { acc, length -> acc + specialCount(length, request.specialPattern, request.charset) }
            GenerationMode.WORD_LIST -> BigInteger.ZERO
        }
    }

    fun candidateAt(request: GenerationRequest, index: BigInteger): String? {
        if (index.signum() < 0) return null
        val total = totalCount(request)
        if (index >= total) return null
        val chars = request.charset.characters()
        if (chars.isEmpty()) return null
        val raw = when (request.mode) {
            GenerationMode.RANDOM -> randomCandidate(request, index, chars)
            GenerationMode.SEQUENTIAL -> sequentialAt(request, index, chars)
            GenerationMode.PATTERN -> patternAt(request, index)
            GenerationMode.SPECIAL -> specialAt(request, index)
            GenerationMode.WORD_LIST -> null
        } ?: return null
        return (UsernameValidator.validate(raw) as? com.userhunter.app.core.UsernameValidation.Valid)?.normalized
    }

    fun generate(
        request: GenerationRequest,
        startIndex: BigInteger = BigInteger.ZERO,
        limit: Int = 500
    ): Sequence<Pair<BigInteger, String>> = sequence {
        require(limit >= 0)
        var index = startIndex
        var emitted = 0
        val total = totalCount(request)
        while (emitted < limit && index < total) {
            candidateAt(request, index)?.let { yield(index to it) }
            index += BigInteger.ONE
            emitted += 1
        }
    }

    private fun randomCandidate(request: GenerationRequest, index: BigInteger, chars: String): String? {
        val length = request.validatedLengths().ifEmpty { return null }
            .let { it[index.mod(BigInteger.valueOf(it.size.toLong())).toInt()] }
        val seed = request.randomSeed xor index.toLong()
        val random = Random(seed)
        val lastChoices = chars.filter { it != '.' }.ifEmpty { return null }
        return buildString(length) {
            repeat(length - 1) { append(chars[random.nextInt(chars.length)]) }
            append(lastChoices[random.nextInt(lastChoices.length)])
        }
    }

    private fun sequentialAt(request: GenerationRequest, index: BigInteger, chars: String): String? {
        var remaining = index
        for (length in request.validatedLengths()) {
            val count = sequentialCount(length, chars)
            if (remaining < count) return sequentialNameAt(length, chars, remaining)
            remaining -= count
        }
        return null
    }

    private fun sequentialCount(length: Int, chars: String): BigInteger {
        val lastCount = chars.count { it != '.' }
        if (lastCount == 0) return BigInteger.ZERO
        return BigInteger.valueOf(chars.length.toLong()).pow(length - 1) * BigInteger.valueOf(lastCount.toLong())
    }

    private fun sequentialNameAt(length: Int, chars: String, index: BigInteger): String {
        val lastChoices = chars.filter { it != '.' }
        val lastCount = BigInteger.valueOf(lastChoices.length.toLong())
        var prefixIndex = index.divide(lastCount)
        val lastIndex = index.mod(lastCount).toInt()
        val prefix = CharArray(length - 1)
        for (position in prefix.lastIndex downTo 0) {
            prefix[position] = chars[prefixIndex.mod(BigInteger.valueOf(chars.length.toLong())).toInt()]
            prefixIndex = prefixIndex.divide(BigInteger.valueOf(chars.length.toLong()))
        }
        return buildString(length) {
            append(prefix)
            append(lastChoices[lastIndex])
        }
    }

    private fun patternAt(request: GenerationRequest, index: BigInteger): String? {
        val parsed = PatternParser.parse(request.patternMask, request.charset)
        if (!parsed.isValid) return null
        return mapChoices(parsed.tokens.mapIndexed { position, token ->
            PatternParser.choicesFor(token, request.charset.characters().toSet(), position == parsed.tokens.lastIndex)
        }, index)
    }

    private fun countPattern(tokens: List<PatternToken>, charset: CharsetOptions): BigInteger =
        tokens.mapIndexed { position, token ->
            PatternParser.choicesFor(token, charset.characters().toSet(), position == tokens.lastIndex).size
        }.fold(BigInteger.ONE) { acc, size ->
            acc * BigInteger.valueOf(size.toLong())
        }

    private fun specialAt(request: GenerationRequest, index: BigInteger): String? {
        var remaining = index
        for (length in request.validatedLengths()) {
            val layouts = specialLayouts(length, request.specialPattern, request.charset)
            val count = countChoices(layouts)
            if (remaining < count) return mapSlots(layouts, remaining)
            remaining -= count
        }
        return null
    }

    private fun specialCount(length: Int, special: SpecialPattern, charset: CharsetOptions): BigInteger =
        countChoices(specialLayouts(length, special, charset))

    /** Each output position references a choice-slot; slots are generated once, lazily per index. */
    private fun specialLayouts(length: Int, special: SpecialPattern, charset: CharsetOptions): List<Pair<Int, String>> {
        val selected = charset.characters()
        val letters = selected.filter { it in AppConfig.LETTERS }
        val consonants = letters.filter { it in AppConfig.CONSONANTS }
        val vowels = letters.filter { it in AppConfig.VOWELS }
        val layouts = when (special) {
            SpecialPattern.REPEATED -> (0 until length).map { 0 to selected }
            SpecialPattern.PALINDROME -> (0 until length).map { minOf(it, length - 1 - it) to selected }
            SpecialPattern.DOUBLES -> (0 until length).map { it / 2 to selected }
            SpecialPattern.ALTERNATING -> (0 until length).map { (it % 2) to selected }
            SpecialPattern.PRONOUNCEABLE -> (0 until length).map { position ->
                position to if (position % 2 == 0) consonants else vowels
            }
        }
        val finalSlot = layouts.lastOrNull()?.first
        return layouts.map { (slot, choices) ->
            slot to if (slot == finalSlot) choices.filterNot { it == '.' } else choices
        }
    }

    private fun countChoices(layouts: List<Pair<Int, String>>): BigInteger {
        if (layouts.isEmpty()) return BigInteger.ZERO
        val slotChoices = layouts.groupBy { it.first }.toSortedMap()
            .mapValues { (_, positions) -> positions.first().second }
        if (slotChoices.values.any { it.isEmpty() }) return BigInteger.ZERO
        return slotChoices.values.fold(BigInteger.ONE) { acc, chars ->
            acc * BigInteger.valueOf(chars.length.toLong())
        }
    }

    private fun mapSlots(layouts: List<Pair<Int, String>>, index: BigInteger): String? {
        val slots = layouts.groupBy { it.first }.toSortedMap()
            .mapValues { (_, positions) -> positions.first().second }
        if (slots.values.any { it.isEmpty() }) return null
        val keys = slots.keys.toList()
        val selected = mutableMapOf<Int, Char>()
        var remaining = index
        for (slot in keys.asReversed()) {
            val chars = slots.getValue(slot)
            val radix = BigInteger.valueOf(chars.length.toLong())
            selected[slot] = chars[remaining.mod(radix).toInt()]
            remaining = remaining.divide(radix)
        }
        return layouts.joinToString(separator = "") { (slot, _) -> selected[slot]?.toString().orEmpty() }
    }

    private fun mapChoices(choices: List<List<Char>>, index: BigInteger): String? {
        if (choices.isEmpty() || choices.any { it.isEmpty() }) return null
        var remaining = index
        val result = CharArray(choices.size)
        for (position in choices.indices.reversed()) {
            val chars = choices[position]
            val radix = BigInteger.valueOf(chars.size.toLong())
            result[position] = chars[remaining.mod(radix).toInt()]
            remaining = remaining.divide(radix)
        }
        return String(result)
    }
}
