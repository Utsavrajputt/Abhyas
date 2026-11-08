package com.invictus.abhyas.domain.ai

import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic strings only: prompts, key/model checks and the JSON-to-question parser. */
class AiDomainTest {

    private val source = AiSource("book1", "Physics", 11, "TEST chapter", Exam.NEET)

    @Test
    fun modelIdsAreRestrictedToASafeAlphabet() {
        assertTrue(AiDefaults.isValidModel("gemini-2.5-flash"))
        assertTrue(AiDefaults.isValidModel("my_model.v1"))
        assertFalse(AiDefaults.isValidModel(""))
        assertFalse(AiDefaults.isValidModel("a/b"))
        assertFalse(AiDefaults.isValidModel("a b"))
        assertFalse(AiDefaults.isValidModel("x?key=1"))
        assertFalse(AiDefaults.isValidModel("a".repeat(65)))
    }

    @Test
    fun apiKeyCheckIsLightButRejectsObviousJunk() {
        assertTrue(ApiKeyFormat.isPlausible("A".repeat(39)))
        assertFalse(ApiKeyFormat.isPlausible("short"))
        assertFalse(ApiKeyFormat.isPlausible("has space ${"A".repeat(30)}"))
        assertFalse(ApiKeyFormat.isPlausible(""))
    }

    @Test
    fun clipKeepsShortTextAndCutsLongText() {
        assertEquals("abc", AiPrompts.clip("  abc  "))
        val long = "x".repeat(AiPrompts.MAX_CONTEXT_CHARS + 500)
        assertTrue(AiPrompts.clip(long).length <= AiPrompts.MAX_CONTEXT_CHARS + 4)
    }

    @Test
    fun promptsCarryThePassageAndTheStayInTextRule() {
        val p = AiPrompts.explain("TEST passage")
        assertTrue(p.user.contains("TEST passage"))
        assertTrue(p.system.contains("ONLY the passage"))
        assertFalse(p.json)
        val ar = AiPrompts.assertionReason("TEST passage", 3)
        assertTrue(ar.json)
        assertTrue(ar.user.contains("3 assertion-reason"))
        assertTrue(AiPrompts.ask("why?", "TEST passage").user.contains("why?"))
    }

    @Test
    fun parserAcceptsFencedJsonAndDropsBadEntries() {
        val raw = """
            Here you go:
            ```json
            [
              {"assertion":"TEST A one","reason":"TEST R one","correctOption":2,"explanation":"TEST why"},
              {"assertion":"TEST A two","reason":"TEST R two","correctOption":9},
              {"assertion":"","reason":"TEST R three","correctOption":1},
              {"assertion":"TEST A one","reason":"TEST R one","correctOption":2}
            ]
            ```
        """.trimIndent()
        val out = AiQuestionParser.parseAssertionReason(raw, source)
        assertEquals(1, out.questions.size)
        assertEquals(3, out.rejected)
        val q = out.questions.single()
        assertEquals(QuestionType.ASSERTION_REASON, q.type)
        assertFalse("AI output is never scored", q.answerVerified)
        assertTrue(q.tags.contains("ai-generated"))
        assertEquals(2, (q.payload as AssertionReasonPayload).correctOption)
        assertEquals("Physics", q.subject)
        assertTrue(q.id.startsWith("ai-ncert:"))
    }

    @Test
    fun parserHandlesStringCodesAndMissingCodes() {
        val raw = """[{"assertion":"TEST A","reason":"TEST R","correctOption":"3"},{"assertion":"TEST A2","reason":"TEST R2"}]"""
        val out = AiQuestionParser.parseAssertionReason(raw, source)
        assertEquals(2, out.questions.size)
        assertEquals(3, (out.questions[0].payload as AssertionReasonPayload).correctOption)
        assertNull((out.questions[1].payload as AssertionReasonPayload).correctOption)
        assertTrue(out.questions.none { it.answerVerified })
    }

    @Test
    fun parserReturnsNothingForGarbage() {
        assertEquals(0, AiQuestionParser.parseAssertionReason("no json here", source).questions.size)
        assertEquals(0, AiQuestionParser.parseAssertionReason("[not valid", source).questions.size)
        assertEquals(0, AiQuestionParser.parseAssertionReason("{\"assertion\":\"x\"}", source).questions.size)
    }

    @Test
    fun parserHonoursTheLimit() {
        val items = (1..10).joinToString(",") { """{"assertion":"TEST A $it","reason":"TEST R $it","correctOption":1}""" }
        assertEquals(4, AiQuestionParser.parseAssertionReason("[$items]", source, limit = 4).questions.size)
    }
}
