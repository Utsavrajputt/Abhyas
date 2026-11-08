package com.invictus.abhyas.domain.ai

import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.ncert.NcertRef
import com.invictus.abhyas.domain.validation.QuestionValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Where AI-written questions come from, so they can carry a verifiable NCERT reference. */
data class AiSource(
    val bookId: String,
    val subject: String,
    val classLevel: Int?,
    val chapter: String?,
    val exam: Exam = Exam.NEET,
    /** Passage the text was taken from; null when the model was given several passages. */
    val ref: NcertRef? = null,
)

class AiParseOutcome(val questions: List<Question>, val rejected: Int)

/**
 * Turns the model's JSON reply into validated questions. Anything malformed is dropped, never repaired by
 * guessing. Every result is `answerVerified = false`: AI output is shown, but never scored.
 */
object AiQuestionParser {
    const val BANK_ID = "ai-ncert"
    private const val MAX_STATEMENT = 600

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseAssertionReason(raw: String, source: AiSource, limit: Int = 20): AiParseOutcome {
        val array = extractArray(raw) ?: return AiParseOutcome(emptyList(), 0)
        val out = ArrayList<Question>()
        val seen = HashSet<String>()
        var rejected = 0
        for (el in array.take(limit * 2)) {
            if (out.size >= limit) break
            val q = build(el as? JsonObject, source)
            if (q == null || !seen.add(q.id)) rejected++ else out += q
        }
        return AiParseOutcome(out, rejected)
    }

    /** Pulls the first JSON array out of a reply that may be wrapped in ``` fences or prose. */
    fun extractArray(raw: String): JsonArray? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try {
            lenient.parseToJsonElement(raw.substring(start, end + 1)) as? JsonArray
        } catch (e: Exception) {
            null
        }
    }

    private fun str(o: JsonObject, key: String): String? =
        (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun build(o: JsonObject?, s: AiSource): Question? {
        o ?: return null
        val assertion = str(o, "assertion") ?: return null
        val reason = str(o, "reason") ?: return null
        if (assertion.length > MAX_STATEMENT || reason.length > MAX_STATEMENT) return null
        val code = (o["correctOption"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }
        if (code != null && code !in 1..4) return null
        val id = "$BANK_ID:" + Sha256.hex("$assertion|$reason".lowercase().toByteArray(Charsets.UTF_8)).take(16)
        val q = Question(
            id = id,
            bankId = BANK_ID,
            type = QuestionType.ASSERTION_REASON,
            statement = "Assertion (A) and Reason (R) are given below.",
            payload = AssertionReasonPayload(assertion, reason, code),
            explanation = str(o, "explanation"),
            exam = s.exam,
            subject = s.subject,
            classLevel = s.classLevel,
            chapter = s.chapter,
            difficulty = Difficulty.MEDIUM,
            source = "AI-generated from NCERT text (unverified)",
            sourcePage = s.ref?.page,
            tags = listOf("ai-generated"),
            marks = 4.0,
            negativeMarks = 1.0,
            answerVerified = false,
            ncertRef = s.ref,
        )
        return if (QuestionValidator.validate(q).isEmpty()) q else null
    }
}
