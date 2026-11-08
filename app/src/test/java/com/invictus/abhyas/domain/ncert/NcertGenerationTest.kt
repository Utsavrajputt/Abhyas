package com.invictus.abhyas.domain.ncert

import com.invictus.abhyas.domain.ai.AiSource
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.TrueFalsePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Synthetic physics-like sentences: tests the mechanics of term finding, blanking and falsifying. */
class NcertGenerationTest {

    private val ctx = AiSource("book1", "Physics", 11, "TEST chapter", Exam.NEET)

    private fun passage(text: String, page: Int = 3, idx: Int = 0) =
        SourcePassage("book1", page, idx, text, PassageHash.of(text), "TEST chapter")

    private val friction = "The force that opposes the relative motion between two surfaces in contact is called friction."
    private val gas = "The temperature of the gas will increase when it is compressed quickly."
    private val heat = "Heat always flows from a body at higher temperature to a body at lower temperature."

    @Test
    fun sentencesSplitWithoutBreakingOnAbbreviations() {
        val parts = SentenceSplitter.split("See Fig. 5.1 for the setup. The next sentence starts here.")
        assertEquals(listOf("See Fig. 5.1 for the setup.", "The next sentence starts here."), parts)
    }

    @Test
    fun termsAreFoundFromCalledAndDefinedAs() {
        assertEquals("friction", TermExtractor.termIn(friction))
        assertEquals(
            "centre of mass",
            TermExtractor.termIn("The point at which the whole mass of a body may be assumed to be concentrated is known as the centre of mass of the body."),
        )
        assertEquals("Work done", TermExtractor.termIn("Work done is defined as the product of force and displacement in the direction of the force."))
        assertNull(TermExtractor.termIn("Light travels in a straight line in a uniform medium and bends at the boundary."))
    }

    @Test
    fun blankReplacesExactlyOneWholeWordOccurrence() {
        val stem = NcertQuestionGenerator.blank(friction, "friction")!!
        assertTrue(stem.contains("_____"))
        assertFalse(stem.contains("friction"))
        assertNull(NcertQuestionGenerator.blank("Friction is a force; friction opposes motion between surfaces in contact.", "friction"))
        assertNull(NcertQuestionGenerator.blank("Frictional forces act between the surfaces that touch each other.", "friction"))
    }

    @Test
    fun usableSentencesStandOnTheirOwn() {
        assertTrue(NcertQuestionGenerator.usable(heat))
        assertFalse(NcertQuestionGenerator.usable("See the figure below for details of the setup used here."))
        assertFalse(NcertQuestionGenerator.usable("This is why the body moves forward when brakes are applied suddenly."))
        assertFalse(NcertQuestionGenerator.usable("Short one."))
    }

    @Test
    fun falsifySwapsOneWordForItsOpposite() {
        assertEquals(
            "The temperature of the gas will decrease when it is compressed quickly.",
            NcertQuestionGenerator.falsify(gas, emptyList(), Random(1)),
        )
    }

    @Test
    fun falsifyRefusesRiskySentences() {
        // Negation could flip the meaning back; both opposites present means the sentence contrasts them.
        assertNull(NcertQuestionGenerator.falsify("The gas does not increase in temperature when it expands slowly.", emptyList(), Random(1)))
        assertNull(NcertQuestionGenerator.falsify("The pressure can increase or decrease depending on the volume of gas.", emptyList(), Random(1)))
        assertNull(NcertQuestionGenerator.falsify(heat, emptyList(), Random(1)))
    }

    @Test
    fun fillBlankQuestionsAreVerifiedAndLinkedToTheBook() {
        val qs = NcertQuestionGenerator.fillBlanks(listOf(passage(friction)), ctx, 5, Random(2))
        assertEquals(1, qs.size)
        val q = qs.single()
        assertEquals(QuestionType.FILL_BLANK, q.type)
        assertTrue(q.answerVerified)
        assertEquals(listOf("friction"), (q.payload as FillBlankPayload).acceptedAnswers)
        assertTrue(q.statement.contains("_____"))
        assertEquals("book1", q.ncertRef?.bookId)
        assertEquals(3, q.ncertRef?.page)
    }

    @Test
    fun trueFalseIsBalancedAndExplainsTheOriginal() {
        val qs = NcertQuestionGenerator.trueFalse(listOf(passage("$gas $heat")), ctx, 2, Random(3))
        assertEquals(2, qs.size)
        val answers = qs.map { (it.payload as TrueFalsePayload).answer }.toSet()
        assertEquals(setOf(true, false), answers)
        val wrong = qs.first { (it.payload as TrueFalsePayload).answer == false }
        assertEquals("The temperature of the gas will decrease when it is compressed quickly.", wrong.statement)
        assertTrue(wrong.explanation!!.contains(gas))
        assertTrue(qs.all { it.answerVerified && it.ncertRef != null })
    }

    @Test
    fun generateMixesKindsAndIgnoresTheOnesHandledElsewhere() {
        val ps = listOf(passage("$friction $gas $heat"))
        val r = NcertQuestionGenerator.generate(ps, ctx, setOf(GenKind.FILL_BLANK, GenKind.TRUE_FALSE, GenKind.ASSERTION_REASON), 4, Random(4))
        assertTrue(r.questions.isNotEmpty())
        assertTrue(r.questions.size <= 4)
        assertTrue(r.questions.all { it.type == QuestionType.FILL_BLANK || it.type == QuestionType.TRUE_FALSE })
        assertEquals(r.questions.size, r.questions.map { it.id }.toSet().size)
        assertTrue(NcertQuestionGenerator.generate(ps, ctx, setOf(GenKind.EXERCISE), 4).questions.isEmpty())
    }

    @Test
    fun exercisesAreReadAfterTheHeadingAndNeverScored() {
        val ps = listOf(
            passage("TEST body text before the exercises begin here.", page = 9, idx = 0),
            passage("EXERCISES", page = 10, idx = 0),
            passage("5.1 State the second law of motion given by Newton and write its mathematical form.", page = 10, idx = 1),
            passage("5.2 Explain why a passenger in a bus moves forward when the driver applies the brakes.", page = 10, idx = 2),
            passage("5.3 Describe how friction depends on the nature of the two surfaces in contact.", page = 10, idx = 3),
        )
        val r = ExerciseExtractor.extract(ps, ctx)
        assertEquals(3, r.questions.size)
        assertTrue(r.questions.none { it.answerVerified })
        assertNotNull(r.note)
        assertTrue(r.questions.any { it.ncertRef?.page == 10 })
    }

    @Test
    fun noExercisesHeadingGivesAClearNote() {
        val r = ExerciseExtractor.extract(listOf(passage(friction)), ctx)
        assertTrue(r.questions.isEmpty())
        assertTrue(r.note!!.contains("Exercises"))
    }
}
