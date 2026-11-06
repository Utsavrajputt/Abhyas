package com.invictus.abhyas.domain.engine

import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionPayload
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.Score
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.domain.model.Verdict
import com.invictus.abhyas.domain.model.hasAnswer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

object Scorer {

    /**
     * [marks]/[negative] default to the question's own values; exam templates can override them.
     * Questions without a verified answer are UNSCORED (never guessed, never penalised).
     */
    fun score(
        q: Question,
        response: Response?,
        marks: Double = q.marks,
        negative: Double = q.negativeMarks,
    ): Score {
        if (!q.answerVerified || !q.payload.hasAnswer()) return Score(Verdict.UNSCORED, 0.0)
        if (response == null || isBlank(response)) return Score(Verdict.UNATTEMPTED, 0.0)
        return if (isCorrect(q.payload, response)) Score(Verdict.CORRECT, marks)
        else Score(Verdict.WRONG, -negative)
    }

    fun isBlank(r: Response): Boolean = when (r) {
        is Response.Choice -> r.ids.isEmpty()
        is Response.Number -> r.value.isNaN()
        is Response.Bool -> false
        is Response.Text -> r.value.isBlank()
    }

    fun isCorrect(p: QuestionPayload, r: Response): Boolean = when (p) {
        is SingleChoicePayload -> r is Response.Choice && r.ids.size == 1 && r.ids.first() == p.correctId
        is MatchPayload -> r is Response.Choice && r.ids.size == 1 && r.ids.first() == p.correctId
        is MultiChoicePayload -> r is Response.Choice && r.ids == p.correctIds.toSet()
        is AssertionReasonPayload -> r is Response.Choice && r.ids == setOf(p.correctOption.toString())
        is TrueFalsePayload -> r is Response.Bool && r.value == p.answer
        is NumericalPayload -> r is Response.Number && numericMatches(p, r.value)
        is FillBlankPayload -> r is Response.Text && fillMatches(p, r.value)
    }

    fun numericMatches(p: NumericalPayload, given: Double): Boolean {
        val answer = p.answer ?: return false
        if (!given.isFinite()) return false
        if (p.integerOnly && given != floor(given)) return false
        val slack = p.tolerance + 1e-9 * max(1.0, abs(answer))
        return abs(given - answer) <= slack
    }

    fun fillMatches(p: FillBlankPayload, given: String): Boolean {
        val g = normalizeText(given, p.caseSensitive)
        return p.acceptedAnswers.any { normalizeText(it, p.caseSensitive) == g }
    }

    private fun normalizeText(s: String, caseSensitive: Boolean): String {
        val collapsed = s.trim().replace(Regex("\\s+"), " ")
        return if (caseSensitive) collapsed else collapsed.lowercase()
    }

    /** Parses typed numeric input; null when it is not a number. */
    fun parseNumber(text: String): Response.Number? =
        text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let { Response.Number(it) }
}
