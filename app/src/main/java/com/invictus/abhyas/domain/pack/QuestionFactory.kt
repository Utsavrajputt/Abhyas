package com.invictus.abhyas.domain.pack

import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.hasAnswer
import com.invictus.abhyas.domain.model.parseEnum
import com.invictus.abhyas.domain.validation.QuestionValidator
import kotlinx.serialization.SerializationException

sealed interface Built {
    data class Ok(val question: Question) : Built
    data class Invalid(val errors: List<String>) : Built
}

/** Turns a [QuestionDto] into a validated domain [Question], collecting every problem found. */
object QuestionFactory {

    data class Defaults(
        val exam: Exam? = null,
        val subject: String? = null,
        val classLevel: Int? = null,
    )

    fun create(dto: QuestionDto, bankId: String, fullId: String, defaults: Defaults): Built {
        val errors = mutableListOf<String>()

        val type = parseEnum<QuestionType>(dto.type)
        if (type == null) errors += "unknown type '${dto.type}'"

        val exam: Exam? = if (dto.exam != null) {
            parseEnum<Exam>(dto.exam).also { if (it == null) errors += "unknown exam '${dto.exam}'" }
        } else {
            defaults.exam
        }
        if (exam == null && dto.exam == null) errors += "exam missing"

        val difficulty: Difficulty? = dto.difficulty?.let { raw ->
            parseEnum<Difficulty>(raw).also { if (it == null) errors += "unknown difficulty '$raw'" }
        }

        val subject = dto.subject?.takeIf { it.isNotBlank() } ?: defaults.subject
        if (subject.isNullOrBlank()) errors += "subject missing"

        val payload = if (type != null) {
            try {
                PayloadCodec.decode(type, dto.payload)
            } catch (e: SerializationException) {
                errors += "invalid payload: ${e.message?.lineSequence()?.firstOrNull()}"
                null
            } catch (e: IllegalArgumentException) {
                errors += "invalid payload: ${e.message?.lineSequence()?.firstOrNull()}"
                null
            }
        } else null

        if (type == null || exam == null || subject.isNullOrBlank() || payload == null) {
            return Built.Invalid(errors)
        }

        val question = Question(
            id = fullId,
            bankId = bankId,
            type = type,
            statement = dto.statement,
            payload = payload,
            explanation = dto.explanation,
            exam = exam,
            subject = subject,
            classLevel = dto.classLevel ?: defaults.classLevel,
            chapter = dto.chapter,
            topic = dto.topic,
            difficulty = difficulty,
            year = dto.year,
            source = dto.source,
            sourcePage = dto.sourcePage,
            tags = dto.tags,
            imagePath = dto.imagePath,
            marks = dto.marks ?: 4.0,
            negativeMarks = dto.negativeMarks ?: 1.0,
            language = dto.language,
            answerVerified = dto.answerVerified ?: payload.hasAnswer(),
        )
        errors += QuestionValidator.validate(question)
        return if (errors.isEmpty()) Built.Ok(question) else Built.Invalid(errors)
    }

    fun toDto(q: Question): QuestionDto = QuestionDto(
        id = q.id,
        bankId = q.bankId,
        type = q.type.name,
        statement = q.statement,
        payload = PayloadCodec.encode(q.payload),
        explanation = q.explanation,
        exam = q.exam.name,
        subject = q.subject,
        classLevel = q.classLevel,
        chapter = q.chapter,
        topic = q.topic,
        difficulty = q.difficulty?.name,
        year = q.year,
        source = q.source,
        sourcePage = q.sourcePage,
        tags = q.tags,
        imagePath = q.imagePath,
        marks = q.marks,
        negativeMarks = q.negativeMarks,
        language = q.language,
        answerVerified = q.answerVerified,
    )

    /** Frozen per-attempt snapshot (JSON of the full question, incl. shuffled option order). */
    fun toSnapshotJson(q: Question): String =
        AbhyasJson.json.encodeToString(QuestionDto.serializer(), toDto(q))

    fun fromSnapshotJson(snapshot: String): Question {
        val dto = AbhyasJson.json.decodeFromString(QuestionDto.serializer(), snapshot)
        val bankId = dto.bankId ?: dto.id.substringBefore(':')
        return when (val built = create(dto, bankId, dto.id, Defaults())) {
            is Built.Ok -> built.question
            is Built.Invalid -> throw IllegalStateException("Corrupt snapshot: ${built.errors.joinToString()}")
        }
    }
}
