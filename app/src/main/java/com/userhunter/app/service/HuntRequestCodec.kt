package com.userhunter.app.service

import com.userhunter.app.core.CharsetOptions
import com.userhunter.app.core.GenerationMode
import com.userhunter.app.core.GenerationRequest
import com.userhunter.app.core.SpecialPattern
import org.json.JSONArray
import org.json.JSONObject

internal data class HuntRequestEnvelope(
    val generation: GenerationRequest,
    val wordListUri: String
)

internal object HuntRequestCodec {
    fun encode(request: GenerationRequest, wordListUri: String): String = JSONObject()
        .put("lengths", JSONArray(request.lengths.sorted()))
        .put("letters", request.charset.letters)
        .put("digits", request.charset.digits)
        .put("underscore", request.charset.underscore)
        .put("period", request.charset.period)
        .put("mode", request.mode.name)
        .put("pattern", request.patternMask)
        .put("special", request.specialPattern.name)
        .put("seed", request.randomSeed)
        .put("wordListUri", wordListUri)
        .toString()

    fun decode(json: String): HuntRequestEnvelope {
        val value = JSONObject(json)
        val lengthArray = value.optJSONArray("lengths") ?: JSONArray()
        val lengths = buildSet {
            for (index in 0 until lengthArray.length()) add(lengthArray.optInt(index))
        }
        val mode = runCatching { GenerationMode.valueOf(value.optString("mode")) }
            .getOrDefault(GenerationMode.SEQUENTIAL)
        val special = runCatching { SpecialPattern.valueOf(value.optString("special")) }
            .getOrDefault(SpecialPattern.PRONOUNCEABLE)
        return HuntRequestEnvelope(
            generation = GenerationRequest(
                lengths = lengths,
                charset = CharsetOptions(
                    letters = value.optBoolean("letters", true),
                    digits = value.optBoolean("digits", false),
                    underscore = value.optBoolean("underscore", false),
                    period = value.optBoolean("period", false)
                ),
                mode = mode,
                patternMask = value.optString("pattern"),
                specialPattern = special,
                randomSeed = value.optLong("seed", 7_314_159L)
            ),
            wordListUri = value.optString("wordListUri")
        )
    }
}
