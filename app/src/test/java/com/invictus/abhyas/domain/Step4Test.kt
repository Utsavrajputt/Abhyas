package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.TestData.opts
import com.invictus.abhyas.domain.TestData.question
import com.invictus.abhyas.domain.analytics.Analytics
import com.invictus.abhyas.domain.analytics.AnswerFormatter
import com.invictus.abhyas.domain.analytics.Dimension
import com.invictus.abhyas.domain.analytics.StatRow
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step4Test {
    private fun row(
        subject: String = "Physics", chapter: String? = "C1", topic: String? = "T1",
        difficulty: String? = "EASY", type: String = "SINGLE_MCQ", verdict: String? = "CORRECT",
        marks: Double = 4.0, time: Long = 1000,
    ) = StatRow(subject, chapter, topic, difficulty, type, verdict, marks, time)

    @Test fun breakdownCountsAndAccuracy() {
        val rows = listOf(
            row(verdict = "CORRECT"), row(verdict = "WRONG", marks = -1.0),
            row(verdict = "UNATTEMPTED", marks = 0.0), row(verdict = "UNSCORED", marks = 0.0),
            row(subject = "Chemistry", verdict = "CORRECT"),
        )
        val b = Analytics.breakdown(rows, Dimension.SUBJECT)
        assertEquals(listOf("Chemistry", "Physics"), b.map { it.label })
        val p = b.first { it.label == "Physics" }
        assertEquals(listOf(4, 1, 1, 1, 1), listOf(p.total, p.correct, p.wrong, p.unattempted, p.unscored))
        assertEquals(0.5, p.accuracy, 1e-9)
        assertEquals(3.0, p.score, 1e-9)
        assertEquals(1000, p.avgTimeMs)
    }

    @Test fun inProgressRowsIgnoredAndEmptyAccuracyIsZero() {
        assertEquals(0, Analytics.overall(listOf(row(verdict = null))).total)
        assertEquals(0.0, Analytics.overall(emptyList()).accuracy, 0.0)
    }

    @Test fun theoryVsNumericalAndDifficultyOrder() {
        val rows = listOf(row(type = "NUMERICAL", difficulty = "HARD"), row(difficulty = "EASY"), row(difficulty = null))
        assertEquals(listOf("Physics - Numerical", "Physics - Theory"), Analytics.breakdown(rows, Dimension.SUBJECT_KIND).map { it.label })
        assertEquals(listOf("Easy", "Hard", "Unspecified"), Analytics.breakdown(rows, Dimension.DIFFICULTY).map { it.label })
    }

    @Test fun chapterLabelsDoNotMergeAcrossSubjects() {
        val rows = listOf(row(subject = "Physics", chapter = "Motion"), row(subject = "Chemistry", chapter = "Motion"))
        assertEquals(2, Analytics.breakdown(rows, Dimension.CHAPTER).size)
    }

    @Test fun weakTopicsNeedEnoughAttemptsAndLowAccuracy() {
        val weak = List(3) { row(topic = "Weak", verdict = if (it == 0) "CORRECT" else "WRONG") }
        val few = List(2) { row(topic = "Few", verdict = "WRONG") }
        val strong = List(4) { row(topic = "Strong", verdict = "CORRECT") }
        val out = Analytics.weakTopics(weak + few + strong)
        assertEquals(1, out.size)
        assertTrue(out.single().label.endsWith("Weak"))
    }

    @Test fun answerFormatting() {
        val single = question(SingleChoicePayload(opts(), "o2"))
        assertEquals("B. TEST option 2", AnswerFormatter.correct(single))
        assertEquals("C. TEST option 3", AnswerFormatter.given(single, Response.Choice(setOf("o3"))))
        assertEquals("Not attempted", AnswerFormatter.given(single, null))
        assertNull(AnswerFormatter.correct(question(SingleChoicePayload(opts(), null), verified = false)))

        val multi = question(MultiChoicePayload(opts(), listOf("o1", "o3")))
        assertEquals("A. TEST option 1; C. TEST option 3", AnswerFormatter.correct(multi))

        assertEquals("42 (+/- 0.5)", AnswerFormatter.correct(question(NumericalPayload(42.0, 0.5))))
        assertEquals("3.5", AnswerFormatter.given(question(NumericalPayload(1.0)), Response.Number(3.5)))
        assertEquals("True", AnswerFormatter.correct(question(TrueFalsePayload(true), QuestionType.TRUE_FALSE)))
        assertEquals("a / b", AnswerFormatter.correct(question(FillBlankPayload(listOf("a", "b")), QuestionType.FILL_BLANK)))
        val ar = question(AssertionReasonPayload("A", "R", 3), QuestionType.ASSERTION_REASON)
        assertTrue(AnswerFormatter.correct(ar)!!.startsWith("(3) Assertion is true but Reason is false"))
    }
}
