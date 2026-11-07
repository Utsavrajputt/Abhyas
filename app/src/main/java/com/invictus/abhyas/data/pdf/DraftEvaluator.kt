package com.invictus.abhyas.data.pdf

import com.invictus.abhyas.data.local.PdfDraftEntity
import com.invictus.abhyas.domain.engine.ContentHash
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.hasAnswer
import com.invictus.abhyas.domain.model.parseEnum
import com.invictus.abhyas.domain.pack.AbhyasJson
import com.invictus.abhyas.domain.pack.Built
import com.invictus.abhyas.domain.pack.QuestionDto
import com.invictus.abhyas.domain.pack.QuestionFactory
import com.invictus.abhyas.domain.pdf.DraftStatus

/** Validates a draft with the same factory/validator the bank importer uses. Pure JVM. */
object DraftEvaluator {

    class Result(val errors: List<String>, val hasAnswer: Boolean, val contentHash: String?)

    fun idOf(d: PdfDraftEntity) = "q${d.position + 1}"

    fun toDto(d: PdfDraftEntity, cfg: PdfJobConfig, source: String, imagePath: String? = d.imagePath): QuestionDto =
        QuestionDto(
            id = idOf(d),
            type = d.type,
            statement = d.statement,
            payload = AbhyasJson.json.parseToJsonElement(d.payloadJson),
            explanation = d.explanation,
            exam = cfg.exam,
            subject = d.subject?.takeIf { it.isNotBlank() } ?: cfg.subject,
            classLevel = cfg.classLevel,
            chapter = d.chapter?.takeIf { it.isNotBlank() },
            topic = d.topic?.takeIf { it.isNotBlank() },
            difficulty = d.difficulty,
            year = cfg.year,
            source = source,
            sourcePage = d.page,
            imagePath = imagePath,
        )

    fun evaluate(d: PdfDraftEntity, cfg: PdfJobConfig, source: String): Result {
        val dto = try {
            toDto(d, cfg, source)
        } catch (e: Exception) {
            return Result(listOf("payload is not valid JSON"), false, null)
        }
        val defaults = QuestionFactory.Defaults(parseEnum<Exam>(cfg.exam), cfg.subject, cfg.classLevel)
        return when (val built = QuestionFactory.create(dto, "pdf", "pdf:${dto.id}", defaults)) {
            is Built.Invalid -> Result(built.errors, false, null)
            is Built.Ok -> Result(emptyList(), built.question.payload.hasAnswer(), ContentHash.of(built.question))
        }
    }

    fun statusOf(errors: List<String>, duplicateOf: String?, hasAnswer: Boolean, confidence: Double): DraftStatus = when {
        errors.isNotEmpty() -> DraftStatus.INVALID
        duplicateOf != null -> DraftStatus.DUPLICATE
        !hasAnswer -> DraftStatus.MISSING_ANSWER
        confidence < LOW_CONFIDENCE -> DraftStatus.LOW_CONFIDENCE
        else -> DraftStatus.OK
    }

    const val LOW_CONFIDENCE = 0.6
}
