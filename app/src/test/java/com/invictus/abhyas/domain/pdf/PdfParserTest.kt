package com.invictus.abhyas.domain.pdf

import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.pack.PayloadCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic text only ("TEST ..."): these check the mechanics of splitting, option and answer-key
 * parsing. Real PDF fixtures (digital + scanned) are added separately under src/test/resources/fixtures.
 */
class PdfParserTest {

    private fun page(n: Int, text: String, method: ExtractMethod = ExtractMethod.TEXT) = PageText(n, text, method)

    private fun payload(d: ParsedDraft) = PayloadCodec.decodeString(QuestionType.valueOf(d.typeName), d.payloadJson)

    @Test
    fun keyAtEnd_matchesAnswersToQuestions() {
        val text = """
            1. TEST statement one
            (1) TEST a (2) TEST b (3) TEST c (4) TEST d
            2. TEST statement two
            (1) TEST a
            (2) TEST b
            (3) TEST c
            (4) TEST d
            3. TEST statement three numerical value
            Answer Key
            1. (2) 2. (4) 3. 25
        """.trimIndent()
        val out = PdfParser.parse(listOf(page(1, text)))
        assertEquals(3, out.drafts.size)
        assertEquals(3, out.keyEntries)
        assertEquals("b", (payload(out.drafts[0]) as SingleChoicePayload).correctId)
        assertEquals("d", (payload(out.drafts[1]) as SingleChoicePayload).correctId)
        val n = payload(out.drafts[2]) as NumericalPayload
        assertEquals(25.0, n.answer!!, 0.0)
    }

    @Test
    fun inlineAnswersAndSolutions() {
        val text = """
            Q1. TEST statement one
            (a) TEST a (b) TEST b (c) TEST c (d) TEST d
            Ans. (b)
            Sol. TEST explanation text
            Q2. TEST statement two here
            (a) TEST a
            (b) TEST b
            (c) TEST c
            (d) TEST d
            Answer: C
        """.trimIndent()
        val out = PdfParser.parse(listOf(page(1, text)), ParseOptions(AnswerLayout.INLINE))
        assertEquals(2, out.drafts.size)
        assertEquals("b", (payload(out.drafts[0]) as SingleChoicePayload).correctId)
        assertEquals("TEST explanation text", out.drafts[0].explanation)
        assertEquals("c", (payload(out.drafts[1]) as SingleChoicePayload).correctId)
    }

    @Test
    fun missingAnswer_isNeverGuessed() {
        val text = "1. TEST statement one\n(1) TEST a\n(2) TEST b\n(3) TEST c\n(4) TEST d\n2. TEST statement two\n(1) TEST a\n(2) TEST b\n(3) TEST c\n(4) TEST d"
        val out = PdfParser.parse(listOf(page(1, text)))
        assertEquals(2, out.drafts.size)
        out.drafts.forEach {
            assertNull((payload(it) as SingleChoicePayload).correctId)
            assertTrue(it.issues.any { i -> i.contains("No answer") })
        }
    }

    @Test
    fun optionRunsAreNotQuestions() {
        val lines = listOf(
            "1. TEST question one", "1. TEST a", "2. TEST b", "3. TEST c", "4. TEST d",
            "2. TEST question two", "1. TEST a", "2. TEST b", "3. TEST c", "4. TEST d",
            "3. TEST question three",
        ).map { Ln(1, it) }
        assertEquals(listOf("1", "2", "3"), QuestionSplitter.split(lines).map { it.label })
    }

    @Test
    fun ncertDottedNumbering() {
        val lines = listOf("5.1 TEST first exercise", "continues here", "5.2 TEST second exercise", "5.3 TEST third exercise").map { Ln(1, it) }
        assertEquals(listOf("5.1", "5.2", "5.3"), QuestionSplitter.split(lines, ncert = true).map { it.label })
    }

    @Test
    fun answerKeyFormats() {
        assertEquals(mapOf("1" to "(2)", "2" to "(4)", "3" to "(1)"), AnswerKeyParser.parse(listOf("1. (2) 2. (4) 3. (1)")))
        assertEquals(mapOf("1" to "C", "2" to "B", "3" to "A"), AnswerKeyParser.parse(listOf("1-C 2-B 3-A")))
        assertEquals(mapOf("21" to "24", "22" to "36", "23" to "3.5"), AnswerKeyParser.parse(listOf("21. 24 22. 36", "23. 3.5")))
    }

    @Test
    fun answerInterpreter() {
        assertEquals("b", AnswerInterpreter.singleId("(2)", 4))
        assertEquals("c", AnswerInterpreter.singleId("C", 4))
        assertNull(AnswerInterpreter.singleId("E", 4))
        assertEquals(listOf("a", "c"), AnswerInterpreter.optionIds("A, C", 4))
        assertEquals(listOf("a", "c", "d"), AnswerInterpreter.optionIds("ACD", 4))
        assertEquals(12.5, AnswerInterpreter.number("12.5")!!, 0.0)
        assertNull(AnswerInterpreter.number("2 to 3"))
        assertEquals(0.005, AnswerInterpreter.toleranceFor("correct up to 2 decimal places"), 1e-12)
    }

    @Test
    fun assertionReason_answerIsRemappedByOptionWording() {
        val text = """
            1. TEST intro. Assertion (A): TEST claim. Reason (R): TEST because.
            (1) A is true but R is false
            (2) Both A and R are true and R is the correct explanation of A
            (3) Both A and R are true but R is not the correct explanation of A
            (4) A is false but R is true
            Answer Key
            1. (2) 2. (1) 3. (1)
        """.trimIndent()
        val d = PdfParser.parse(listOf(page(1, text))).drafts.first()
        assertEquals("ASSERTION_REASON", d.typeName)
        val p = payload(d) as AssertionReasonPayload
        assertEquals("TEST claim.", p.assertion)
        assertEquals(1, p.correctOption) // option (2) in this paper is standard code 1
    }

    @Test
    fun ocrPagesLowerConfidence() {
        val text = "1. TEST statement one\n(1) TEST a\n(2) TEST b\n(3) TEST c\n(4) TEST d"
        val digital = PdfParser.parse(listOf(page(1, text))).drafts.first()
        val scanned = PdfParser.parse(listOf(page(1, text, ExtractMethod.OCR))).drafts.first()
        assertTrue(scanned.confidence < digital.confidence)
        assertTrue(scanned.issues.any { it.contains("OCR") })
    }

    @Test
    fun runningHeadersAndPageNumbersAreRemoved() {
        val words = listOf("alpha", "bravo", "charlie", "delta")
        val pages = (1..4).map { n ->
            page(n, "TEST Coaching Institute Header\n$n. TEST statement about ${words[n - 1]}\n(1) TEST a\n(2) TEST b\n(3) TEST c\n(4) TEST d\n$n")
        }
        val out = PdfParser.parse(pages)
        assertEquals(4, out.drafts.size)
        out.drafts.forEach { assertTrue(!it.statement.contains("Header")) }
    }

    @Test
    fun pageQuality() {
        assertTrue(PageQuality.needsOcr(""))
        assertTrue(PageQuality.needsOcr("\uFFFD\uFFFD \u0001\u0002 ".repeat(30)))
        assertTrue(!PageQuality.needsOcr("This page has plenty of ordinary readable text on it for extraction."))
    }

    @Test
    fun nearDuplicateDetection() {
        val index = Similarity.Index(listOf("A TEST body moves with uniform acceleration along a straight line for ten seconds"))
        assertNotNull(index.best("A TEST body moves with uniform acceleration along a straight line for 10 seconds", 0.5))
        assertNull(index.best("Completely different TEST sentence about something else entirely here", 0.5))
    }

    @Test
    fun figureBand() {
        val lines = listOf(LinePos("1. TEST first statement", 100f), LinePos("2. TEST second statement", 400f))
        val band = FigureRegion.band(lines, 800f, "TEST first statement", "TEST second statement")
        assertNotNull(band)
        assertEquals(94f, band!!.first, 0.01f)
        assertEquals(400f, band.second, 0.01f)
    }
}
