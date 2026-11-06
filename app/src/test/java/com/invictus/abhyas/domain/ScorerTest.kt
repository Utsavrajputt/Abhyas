package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.TestData.opts
import com.invictus.abhyas.domain.TestData.question
import com.invictus.abhyas.domain.engine.Scorer
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.domain.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScorerTest {
    private val single = question(SingleChoicePayload(opts(), correctId = "o2"))

    @Test fun correctGivesMarks() {
        val s = Scorer.score(single, Response.Choice(setOf("o2")))
        assertEquals(Verdict.CORRECT, s.verdict); assertEquals(4.0, s.marks, 0.0)
    }

    @Test fun wrongAppliesNegativeMarking() {
        val s = Scorer.score(single, Response.Choice(setOf("o1")))
        assertEquals(Verdict.WRONG, s.verdict); assertEquals(-1.0, s.marks, 0.0)
    }

    @Test fun overridesBeatQuestionMarks() {
        val s = Scorer.score(single, Response.Choice(setOf("o1")), marks = 2.0, negative = 0.5)
        assertEquals(-0.5, s.marks, 0.0)
    }

    @Test fun zeroNegativeMeansNoPenalty() {
        val q = question(SingleChoicePayload(opts(), "o1"), negative = 0.0)
        assertEquals(0.0, Scorer.score(q, Response.Choice(setOf("o3"))).marks + 0.0, 0.0)
    }

    @Test fun unattemptedIsZero() {
        assertEquals(Verdict.UNATTEMPTED, Scorer.score(single, null).verdict)
        assertEquals(Verdict.UNATTEMPTED, Scorer.score(single, Response.Choice(emptySet())).verdict)
        assertEquals(0.0, Scorer.score(single, null).marks, 0.0)
    }

    @Test fun missingAnswerIsUnscoredNotGuessed() {
        val q = question(SingleChoicePayload(opts(), null), verified = false)
        val s = Scorer.score(q, Response.Choice(setOf("o1")))
        assertEquals(Verdict.UNSCORED, s.verdict); assertEquals(0.0, s.marks, 0.0)
    }

    @Test fun unverifiedWithAnswerIsStillUnscored() {
        val q = question(SingleChoicePayload(opts(), "o1"), verified = false)
        assertEquals(Verdict.UNSCORED, Scorer.score(q, Response.Choice(setOf("o1"))).verdict)
    }

    @Test fun multiMcqIsAllOrNothing() {
        val q = question(MultiChoicePayload(opts(), listOf("o1", "o3")))
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response.Choice(setOf("o3", "o1"))).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(q, Response.Choice(setOf("o1"))).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(q, Response.Choice(setOf("o1", "o2", "o3"))).verdict)
    }

    @Test fun singleRejectsMultipleSelections() {
        assertEquals(Verdict.WRONG, Scorer.score(single, Response.Choice(setOf("o2", "o1"))).verdict)
    }

    @Test fun numericalWithinTolerance() {
        val q = question(NumericalPayload(answer = 9.8, tolerance = 0.05))
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response.Number(9.84)).verdict)
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response.Number(9.75)).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(q, Response.Number(9.9)).verdict)
    }

    @Test fun numericalExactAndFloatNoise() {
        val q = question(NumericalPayload(answer = 0.3))
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response.Number(0.1 + 0.2)).verdict)
    }

    @Test fun integerOnlyRejectsDecimals() {
        val q = question(NumericalPayload(answer = 5.0, integerOnly = true))
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response.Number(5.0)).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(q, Response.Number(5.2)).verdict)
    }

    @Test fun numberParsing() {
        assertEquals(12.5, Scorer.parseNumber(" 12.5 ")!!.value, 0.0)
        assertNull(Scorer.parseNumber("abc"))
        assertNull(Scorer.parseNumber("NaN"))
        assertNotNull(Scorer.parseNumber("-3"))
    }

    @Test fun trueFalseAndAssertionReason() {
        val tf = question(TrueFalsePayload(true), QuestionType.TRUE_FALSE)
        assertEquals(Verdict.CORRECT, Scorer.score(tf, Response.Bool(true)).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(tf, Response.Bool(false)).verdict)
        val ar = question(AssertionReasonPayload("TEST A", "TEST R", 3), QuestionType.ASSERTION_REASON)
        assertEquals(Verdict.CORRECT, Scorer.score(ar, Response.Choice(setOf("3"))).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(ar, Response.Choice(setOf("1"))).verdict)
    }

    @Test fun fillBlankNormalisesCaseAndSpace() {
        val q = question(FillBlankPayload(listOf("Newton Metre")), QuestionType.FILL_BLANK)
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response.Text("  newton   metre ")).verdict)
        assertEquals(Verdict.UNATTEMPTED, Scorer.score(q, Response.Text("  ")).verdict)
        val cs = question(FillBlankPayload(listOf("NaCl"), caseSensitive = true), QuestionType.FILL_BLANK)
        assertEquals(Verdict.WRONG, Scorer.score(cs, Response.Text("nacl")).verdict)
    }
}
