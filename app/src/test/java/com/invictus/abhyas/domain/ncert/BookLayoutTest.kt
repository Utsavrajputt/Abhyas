package com.invictus.abhyas.domain.ncert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLayoutTest {
    private fun names(vararg n: String) = n.map { "keph1dd/$it" }

    @Test fun classifiesNcertFileNames() {
        assertEquals(SectionKind.PRELIMS, BookZipPlan.classify("keph1dd/keph1ps.pdf")?.kind)
        assertEquals(SectionKind.ANSWERS, BookZipPlan.classify("keph1an.pdf")?.kind)
        val ch = BookZipPlan.classify("keph107.pdf")!!
        assertEquals(SectionKind.CHAPTER, ch.kind)
        assertEquals(7, ch.number)
        assertEquals("keph1", ch.prefix)
        val app = BookZipPlan.classify("keph1a2.pdf")!!
        assertEquals(SectionKind.APPENDIX, app.kind)
        assertEquals(2, app.number)
        assertNull(BookZipPlan.classify("notes.pdf"))
        assertNull(BookZipPlan.classify("keph101.txt"))
        assertNull(BookZipPlan.classify("__MACOSX/keph101.pdf"))
        assertNull(BookZipPlan.classify("keph1dd/"))
    }

    @Test fun planOrdersPrelimsChaptersAppendicesAnswers() {
        val parts = names("keph1an.pdf", "keph102.pdf", "keph1a1.pdf", "keph101.pdf", "keph1ps.pdf", "keph110.pdf", "readme.txt")
            .mapNotNull { BookZipPlan.classify(it) }
        val kinds = BookZipPlan.plan(parts).map { it.kind to it.number }
        assertEquals(
            listOf(
                SectionKind.PRELIMS to 0, SectionKind.CHAPTER to 1, SectionKind.CHAPTER to 2, SectionKind.CHAPTER to 10,
                SectionKind.APPENDIX to 1, SectionKind.ANSWERS to 0,
            ),
            kinds,
        )
    }

    @Test fun planKeepsOneBookAndNeedsAChapter() {
        val mixed = names("keph101.pdf", "keph102.pdf", "kech101.pdf").mapNotNull { BookZipPlan.classify(it) }
        assertEquals(listOf("keph1"), BookZipPlan.plan(mixed).map { it.prefix }.distinct())
        assertTrue(BookZipPlan.plan(names("keph1ps.pdf", "keph1an.pdf").mapNotNull { BookZipPlan.classify(it) }).isEmpty())
        assertTrue(BookZipPlan.plan(emptyList()).isEmpty())
    }

    @Test fun chapterTitleFromFirstPage() {
        assertEquals("Units and Measurement", ChapterTitles.fromFirstPage("CHAPTER ONE\nUNITS AND MEASUREMENT\n1.1 INTRODUCTION\nMeasurement of any"))
        assertEquals(
            "Systems of Particles and Rotational Motion",
            ChapterTitles.fromFirstPage("CHAPTER SIX\nSYSTEMS OF PARTICLES AND\nROTATIONAL MOTION\n6.1 INTRODUCTION"),
        )
        assertNull(ChapterTitles.fromFirstPage("\n\n\n"))
        assertNull(ChapterTitles.fromFirstPage("CHAPTER TWO\n2.1 INTRODUCTION"))
    }

    @Test fun chapterTitleFromSymbolFontHeading() {
        val shifted = "CHAPTER SEVEN\nGRAVITATION\n7.1 INTRODUCTION".map { if (it in ' '..'~' && it != '\n') (it.code + 0xF000).toChar() else it }.joinToString("")
        assertEquals("Gravitation", ChapterTitles.fromFirstPage(shifted))
    }

    private fun sample(offset: Int? = null) = BookLayout.build(
        listOf(
            PartInfo(SectionKind.PRELIMS, 0, 16, null),
            PartInfo(SectionKind.CHAPTER, 1, 12, "Units and Measurement"),
            PartInfo(SectionKind.CHAPTER, 2, 14, null),
            PartInfo(SectionKind.APPENDIX, 1, 16, null),
            PartInfo(SectionKind.ANSWERS, 0, 9, null),
        ),
        offset,
    )

    @Test fun buildMakesChaptersWithRanges() {
        val ch = sample().chapters()
        assertEquals(listOf("Prelims", "Ch 1: Units and Measurement", "Chapter 2", "Appendices", "Answers"), ch.map { it.title })
        assertEquals(17, ch[1].startPage)
        assertEquals(28, ch[1].endPage)
        assertEquals(29, ch[2].startPage)
        assertEquals(42, ch[2].endPage)
        assertEquals(43, ch[3].startPage)
        assertEquals(58, ch[3].endPage)
        assertEquals(59, ch[4].startPage)
        assertEquals(67, ch[4].endPage)
    }

    @Test fun printedPageNumbersFollowTheBook() {
        val l = sample()
        assertEquals("i", l.printedLabel(1))
        assertEquals("xiii", l.printedLabel(13))
        assertEquals("1", l.printedLabel(17))
        assertEquals("13", l.printedLabel(29))
        assertEquals("27", l.printedLabel(43))
        assertEquals("43", l.printedLabel(59))
        // a book whose numbering does not restart after the prelims (offset read from the pages)
        assertEquals("27", sample(offset = -2).printedLabel(29))
    }

    @Test fun onlyChapterPagesAreContent() {
        val l = sample()
        assertFalse(l.isContent(5))
        assertTrue(l.isContent(17))
        assertTrue(l.isContent(42))
        assertFalse(l.isContent(43))
        assertFalse(l.isContent(60))
    }

    @Test fun layoutSurvivesJson() {
        val l = sample(-16)
        val back = BookLayout.fromJson(l.toJson())
        assertNotNull(back)
        assertEquals(l, back)
        assertNull(BookLayout.fromJson("not json"))
    }

    @Test fun printedOffsetFromHeadersAndFooters() {
        val pages = mapOf(
            18 to listOf("2", "PHYSICS", "1.2 THE INTERNATIONAL SYSTEM OF UNITS", "body"),
            19 to listOf("UNITS AND MEASUREMENT", "body", "UNITS AND MEASUREMENT", "3"),
            20 to listOf("4 PHYSICS", "body text", "more"),
        )
        assertEquals(-16, PrintedPages.offset(pages))
    }

    @Test fun printedOffsetNeedsTwoPagesThatAgree() {
        assertNull(PrintedPages.offset(mapOf(18 to listOf("2", "PHYSICS"))))
        assertNull(PrintedPages.offset(mapOf(18 to listOf("2", "PHYSICS"), 19 to listOf("7", "text"))))
        assertNull(PrintedPages.offset(emptyMap()))
    }

    @Test fun romanNumerals() {
        assertEquals("iv", BookLayout.roman(4))
        assertEquals("xvi", BookLayout.roman(16))
        assertEquals("xlix", BookLayout.roman(49))
    }
}
