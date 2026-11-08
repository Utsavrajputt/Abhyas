package com.invictus.abhyas.domain.ai

import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.ncert.GeneratedBanks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainAnswerPromptTest {

    private fun mcq(verified: Boolean, bankId: String = "demo") = Question(
        id = "$bankId:1", bankId = bankId, type = QuestionType.SINGLE_MCQ, statement = "TEST which option is right?",
        payload = SingleChoicePayload(listOf(Option("a", "TEST first"), Option("b", "TEST second")), if (verified) "b" else null),
        exam = Exam.NEET, subject = "Physics", answerVerified = verified,
    )

    @Test
    fun verifiedAnswerAsksForWhyRightAndWhyOthersWrong() {
        val p = AiPrompts.explainAnswer(mcq(true), given = "(A) TEST first", correct = "(B) TEST second", passage = "TEST passage")
        assertTrue(p.user.contains("verified correct answer is: (B) TEST second"))
        assertTrue(p.user.contains("every other option"))
        assertTrue(p.user.contains("TEST passage"))
        assertTrue(p.user.contains("(A) TEST first"))
        assertTrue(p.user.contains("(B) TEST second"))
    }

    @Test
    fun unverifiedAnswerIsNeverPresentedAsCertain() {
        val p = AiPrompts.explainAnswer(mcq(false), given = "Not attempted", correct = "(B) TEST second", passage = null)
        assertTrue(p.user.contains("NOT verified"))
        assertFalse(p.user.contains("verified correct answer is"))
        assertTrue(p.user.contains("No textbook passage is available"))
    }

    @Test
    fun onlyGeneratedBanksAreSavedAndTheyMoveToOneBank() {
        assertFalse(GeneratedBanks.isGenerated(mcq(true, "demo")))
        for (b in listOf("ncert-gen", "ncert-ex", "ai-ncert")) assertTrue(GeneratedBanks.isGenerated(mcq(true, b)))
        val saved = GeneratedBanks.forSaving(mcq(true, "ncert-gen"))
        assertEquals(GeneratedBanks.PRACTICE_BANK, saved.bankId)
        assertEquals("ncert-gen:1", saved.id)
    }
}
