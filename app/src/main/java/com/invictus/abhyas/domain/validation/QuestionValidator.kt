package com.invictus.abhyas.domain.validation

import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.domain.model.hasAnswer
import kotlin.math.floor

/** Pure validation. Returns human-readable errors; empty list = valid. */
object QuestionValidator {

    fun validate(q: Question): List<String> {
        val e = mutableListOf<String>()
        if (q.id.isBlank()) e += "id is blank"
        if (q.statement.isBlank()) e += "statement is blank"
        if (q.subject.isBlank()) e += "subject is blank"
        if (q.classLevel != null && q.classLevel != 11 && q.classLevel != 12) e += "class must be 11 or 12"
        if (q.year != null && q.year !in 1990..2100) e += "year out of range"
        if (!q.marks.isFinite() || q.marks < 0) e += "marks must be >= 0"
        if (!q.negativeMarks.isFinite() || q.negativeMarks < 0) e += "negativeMarks must be >= 0"
        if (q.answerVerified && !q.payload.hasAnswer()) e += "answerVerified=true but the answer is missing"
        if (q.type == QuestionType.IMAGE && q.imagePath.isNullOrBlank()) e += "IMAGE question needs imagePath"
        q.ncertRef?.let { r ->
            if (r.bookId.isBlank()) e += "ncertRef.bookId is blank"
            if (r.page < 1) e += "ncertRef.page must be >= 1"
            if (r.paragraph < 0) e += "ncertRef.paragraph must be >= 0"
            if (!com.invictus.abhyas.domain.ncert.PassageHash.VALID.matches(r.textHash)) e += "ncertRef.textHash must be 16 hex chars"
        }
        e += validatePayload(q.type, q.payload)
        return e
    }

    private fun validatePayload(type: QuestionType, p: QuestionPayload): List<String> {
        val e = mutableListOf<String>()
        val expectedOk = when (type) {
            QuestionType.SINGLE_MCQ, QuestionType.IMAGE -> p is SingleChoicePayload
            QuestionType.MULTI_MCQ -> p is MultiChoicePayload
            QuestionType.NUMERICAL -> p is NumericalPayload
            QuestionType.TRUE_FALSE -> p is TrueFalsePayload
            QuestionType.ASSERTION_REASON -> p is AssertionReasonPayload
            QuestionType.MATCH -> p is MatchPayload
            QuestionType.FILL_BLANK -> p is FillBlankPayload
        }
        if (!expectedOk) return listOf("payload does not match type $type")

        when (p) {
            is SingleChoicePayload -> {
                e += options(p.options, min = 2, label = "options")
                if (p.correctId != null && p.options.none { it.id == p.correctId }) e += "correctId not in options"
            }
            is MultiChoicePayload -> {
                e += options(p.options, min = 2, label = "options")
                if (p.correctIds.size != p.correctIds.toSet().size) e += "correctIds has duplicates"
                if (p.correctIds.any { id -> p.options.none { it.id == id } }) e += "correctIds not all in options"
            }
            is NumericalPayload -> {
                if (p.answer != null && !p.answer.isFinite()) e += "answer must be finite"
                if (!p.tolerance.isFinite() || p.tolerance < 0) e += "tolerance must be >= 0"
                if (p.integerOnly && p.answer != null && p.answer.isFinite() && p.answer != floor(p.answer)) {
                    e += "integerOnly but answer is not an integer"
                }
            }
            is TrueFalsePayload -> Unit
            is AssertionReasonPayload -> {
                if (p.assertion.isBlank()) e += "assertion is blank"
                if (p.reason.isBlank()) e += "reason is blank"
                if (p.correctOption != null && p.correctOption !in 1..4) e += "correctOption must be 1..4"
            }
            is MatchPayload -> {
                e += options(p.left, min = 2, label = "left")
                e += options(p.right, min = 2, label = "right")
                e += options(p.options, min = 2, label = "options")
                if (p.correctId != null && p.options.none { it.id == p.correctId }) e += "correctId not in options"
            }
            is FillBlankPayload -> {
                if (p.acceptedAnswers.any { it.isBlank() }) e += "acceptedAnswers has a blank entry"
            }
        }
        return e
    }

    private fun options(list: List<Option>, min: Int, label: String): List<String> {
        val e = mutableListOf<String>()
        if (list.size < min) e += "$label needs at least $min entries"
        if (list.any { it.id.isBlank() }) e += "$label has a blank id"
        if (list.any { it.text.isBlank() }) e += "$label has blank text"
        if (list.map { it.id }.toSet().size != list.size) e += "$label has duplicate ids"
        return e
    }
}
