package com.invictus.abhyas.domain.pack

import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.QuestionPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object AbhyasJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** (De)serialises type-specific payloads. Throws on malformed input; callers turn that into errors. */
object PayloadCodec {
    private val json get() = AbhyasJson.json

    fun decode(type: QuestionType, element: JsonElement): QuestionPayload = when (type) {
        QuestionType.SINGLE_MCQ, QuestionType.IMAGE ->
            json.decodeFromJsonElement(SingleChoicePayload.serializer(), element)
        QuestionType.MULTI_MCQ -> json.decodeFromJsonElement(MultiChoicePayload.serializer(), element)
        QuestionType.NUMERICAL -> json.decodeFromJsonElement(NumericalPayload.serializer(), element)
        QuestionType.TRUE_FALSE -> json.decodeFromJsonElement(TrueFalsePayload.serializer(), element)
        QuestionType.ASSERTION_REASON -> json.decodeFromJsonElement(AssertionReasonPayload.serializer(), element)
        QuestionType.MATCH -> json.decodeFromJsonElement(MatchPayload.serializer(), element)
        QuestionType.FILL_BLANK -> json.decodeFromJsonElement(FillBlankPayload.serializer(), element)
    }

    fun encode(p: QuestionPayload): JsonElement = when (p) {
        is SingleChoicePayload -> json.encodeToJsonElement(SingleChoicePayload.serializer(), p)
        is MultiChoicePayload -> json.encodeToJsonElement(MultiChoicePayload.serializer(), p)
        is NumericalPayload -> json.encodeToJsonElement(NumericalPayload.serializer(), p)
        is TrueFalsePayload -> json.encodeToJsonElement(TrueFalsePayload.serializer(), p)
        is AssertionReasonPayload -> json.encodeToJsonElement(AssertionReasonPayload.serializer(), p)
        is MatchPayload -> json.encodeToJsonElement(MatchPayload.serializer(), p)
        is FillBlankPayload -> json.encodeToJsonElement(FillBlankPayload.serializer(), p)
    }

    fun decodeString(type: QuestionType, payloadJson: String): QuestionPayload =
        decode(type, json.parseToJsonElement(payloadJson))
}
