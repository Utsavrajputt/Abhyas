package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.TestData.opts
import com.invictus.abhyas.domain.TestData.question
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.validation.QuestionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionValidatorTest {
    private fun errors(q: com.invictus.abhyas.domain.model.Question) = QuestionValidator.validate(q)

    @Test fun validSingleHasNoErrors() =
        assertEquals(emptyList<String>(), errors(question(SingleChoicePayload(opts(), "o1"))))

    @Test fun missingAnswerIsFineWhenUnverified() =
        assertEquals(emptyList<String>(), errors(question(SingleChoicePayload(opts(), null), verified = false)))

    @Test fun verifiedWithoutAnswerRejected() =
        assertTrue(errors(question(SingleChoicePayload(opts(), null), verified = true)).any { "missing" in it })

    @Test fun correctIdMustExist() =
        assertTrue(errors(question(SingleChoicePayload(opts(), "zzz"))).any { "correctId" in it })

    @Test fun needsTwoOptionsUniqueIdsNonBlank() {
        assertTrue(errors(question(SingleChoicePayload(listOf(Option("a", "x")), "a"))).isNotEmpty())
        assertTrue(errors(question(SingleChoicePayload(listOf(Option("a", "x"), Option("a", "y")), "a"))).isNotEmpty())
        assertTrue(errors(question(SingleChoicePayload(listOf(Option("a", "x"), Option("b", " ")), "a"))).isNotEmpty())
    }

    @Test fun multiAnswersMustBeSubsetWithoutDuplicates() {
        assertTrue(errors(question(MultiChoicePayload(opts(), listOf("o1", "nope")))).isNotEmpty())
        assertTrue(errors(question(MultiChoicePayload(opts(), listOf("o1", "o1")))).isNotEmpty())
    }

    @Test fun numericalRules() {
        assertTrue(errors(question(NumericalPayload(answer = 1.0, tolerance = -1.0))).isNotEmpty())
        assertTrue(errors(question(NumericalPayload(answer = 1.5, integerOnly = true))).isNotEmpty())
        assertTrue(errors(question(NumericalPayload(answer = Double.NaN))).isNotEmpty())
        assertEquals(emptyList<String>(), errors(question(NumericalPayload(answer = 2.0, tolerance = 0.1))))
    }

    @Test fun assertionReasonRange() {
        val bad = question(AssertionReasonPayload("TEST A", "TEST R", 5), QuestionType.ASSERTION_REASON)
        assertTrue(errors(bad).any { "1..4" in it })
    }

    @Test fun imageNeedsImagePath() {
        val q = question(SingleChoicePayload(opts(), "o1"), QuestionType.IMAGE)
        assertTrue(errors(q).any { "imagePath" in it })
        assertEquals(emptyList<String>(), errors(q.copy(imagePath = "img/1.png")))
    }

    @Test fun payloadTypeMismatchRejected() =
        assertTrue(errors(question(NumericalPayload(1.0), QuestionType.SINGLE_MCQ)).isNotEmpty())

    @Test fun badMetadataRejected() {
        val q = question(SingleChoicePayload(opts(), "o1"))
        assertTrue(errors(q.copy(classLevel = 10)).isNotEmpty())
        assertTrue(errors(q.copy(marks = -1.0)).isNotEmpty())
        assertTrue(errors(q.copy(statement = " ")).isNotEmpty())
    }
}
