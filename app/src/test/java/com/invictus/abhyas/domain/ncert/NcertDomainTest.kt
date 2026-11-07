package com.invictus.abhyas.domain.ncert

import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.pack.Built
import com.invictus.abhyas.domain.pack.PayloadCodec
import com.invictus.abhyas.domain.pack.QuestionDto
import com.invictus.abhyas.domain.pack.QuestionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic text only ("TEST ..."): mechanics of paragraphs, chapters, index parsing and references. */
class NcertDomainTest {

    @Test
    fun paragraphs_splitByVerticalGap() {
        val lines = listOf(
            TextLine("TEST first paragraph line one", 100f, 10f),
            TextLine("TEST first paragraph line two", 112f, 10f),
            TextLine("TEST first paragraph line three", 124f, 10f),
            TextLine("TEST second paragraph starts here", 160f, 10f),
            TextLine("TEST second paragraph ends here", 172f, 10f),
        )
        val p = ParagraphSplitter.split(lines)
        assertEquals(2, p.size)
        assertEquals(0, p[0].index)
        assertTrue(p[0].text.startsWith("TEST first paragraph line one TEST first"))
        assertEquals(90f, p[0].top, 0.01f)
        assertTrue(p[1].top > p[0].bottom)
    }

    @Test
    fun paragraphs_tinyFragmentsAreDropped() {
        val p = ParagraphSplitter.split(listOf(TextLine("12", 50f, 10f), TextLine("TEST a real sentence here", 90f, 10f)))
        assertEquals(1, p.size)
        assertEquals(0, p[0].index)
    }

    @Test
    fun passageHash_ignoresCaseSpacingAndPunctuation() {
        assertEquals(PassageHash.of("TEST Hello,  World."), PassageHash.of("test hello world"))
        assertNotEquals(PassageHash.of("TEST one"), PassageHash.of("TEST two"))
        assertTrue(PassageHash.VALID.matches(PassageHash.of("TEST anything")))
    }

    @Test
    fun chapters_fromPageHeads() {
        val c = ChapterGuess.guess(
            mapOf(5 to listOf("Chapter 1", "TEST Alpha Title"), 20 to listOf("CHAPTER 2: TEST Beta Title")),
            pageCount = 40,
        )
        assertEquals(3, c.size)
        assertEquals("Front matter", c[0].title)
        assertEquals(1..4, c[0].startPage..c[0].endPage)
        assertEquals("Chapter 1: TEST Alpha Title", c[1].title)
        assertEquals(5..19, c[1].startPage..c[1].endPage)
        assertEquals(20..40, c[2].startPage..c[2].endPage)
    }

    @Test
    fun chapters_noStartsMeansWholeBook() {
        val c = ChapterGuess.fromStarts(emptyList(), 12)
        assertEquals(1, c.size)
        assertEquals(1..12, c[0].startPage..c[0].endPage)
    }

    @Test
    fun bookIndex_validAndInvalidEntries() {
        val sha = "a".repeat(64)
        val json = """
            {"formatVersion":1,"books":[
              {"id":"test_book_1","title":"TEST Book","class":11,"subject":"Physics","url":"https://example.invalid/a.pdf","sha256":"$sha","sizeBytes":1000},
              {"id":"bad id","subject":"Physics","url":"https://example.invalid/b.pdf","sha256":"$sha"},
              {"id":"test_book_3","subject":"Physics","url":"http://example.invalid/c.pdf","sha256":"$sha"},
              {"id":"test_book_4","subject":"Physics","url":"https://example.invalid/d.pdf","sha256":"short"}
            ]}
        """.trimIndent()
        val r = BookIndexParser.parse(json.toByteArray())
        assertEquals(1, r.entries.size)
        assertEquals("test_book_1", r.entries[0].id)
        assertEquals(3, r.errors.size)
        assertTrue(BookIndexParser.parse("not json".toByteArray()).errors.isNotEmpty())
        assertTrue(BookIndexParser.parse("""{"formatVersion":2,"books":[]}""".toByteArray()).errors.isNotEmpty())
    }

    private fun dto(ref: NcertRef?) = QuestionDto(
        id = "q1", type = "SINGLE_MCQ", statement = "TEST statement",
        payload = PayloadCodec.encode(SingleChoicePayload(listOf(Option("a", "TEST a"), Option("b", "TEST b")), "a")),
        exam = "NEET", subject = "Physics", ncertRef = ref,
    )

    @Test
    fun ncertRef_survivesFactoryRoundTrip() {
        val ref = NcertRef("test_book_1", 12, 3, PassageHash.of("TEST passage"), "TEST chapter")
        val defaults = QuestionFactory.Defaults(Exam.NEET, "Physics", null)
        val built = QuestionFactory.create(dto(ref), "bank", "bank:q1", defaults) as Built.Ok
        assertEquals(ref, built.question.ncertRef)
        assertEquals(ref, QuestionFactory.toDto(built.question).ncertRef)
    }

    @Test
    fun ncertRef_badValuesAreRejected() {
        val defaults = QuestionFactory.Defaults(Exam.NEET, "Physics", null)
        val bad = NcertRef("", 0, -1, "zz", null)
        val r = QuestionFactory.create(dto(bad), "bank", "bank:q1", defaults) as Built.Invalid
        assertTrue(r.errors.any { it.contains("ncertRef.textHash") })
        assertTrue(r.errors.any { it.contains("ncertRef.page") })
    }
}
