package com.invictus.abhyas.data.pyq

import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.ui.library.PaperFilter
import com.invictus.abhyas.ui.library.PaperRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageIndexParserTest {
    private val sha = "a".repeat(64)

    private fun parse(json: String) = PackageIndexParser.parse(json.toByteArray())

    @Test fun newFormatReadsNameAndFields() {
        val r = parse(
            """{"name":"My Source","packages":[{"id":"neet-2024","name":"NEET 2024","description":"Full paper",
            "version":2,"url":"https://x.test/a.zip","type":"paper","subject":"Physics","coaching":"Allen",
            "kind":"pyq","year":2024,"questions":180}]}""",
        )
        assertEquals("My Source", r.name)
        val e = r.entries.single()
        assertEquals("NEET 2024", e.name)
        assertEquals(2, e.version)
        assertEquals("PYQ", e.kind)
        assertEquals("Allen", e.coaching)
        assertEquals(2024, e.year)
        assertEquals(180, e.questions)
        assertNull(e.sha256)
        assertEquals(PackageType.PAPER, e.type)
    }

    @Test fun legacyPacksAndBooksStillParse() {
        val r = parse(
            """{"packs":[{"id":"p1","title":"NEET 2023","year":2023,"url":"https://x.test/p.zip","sha256":"$sha","questions":180}],
            "books":[{"id":"b1","title":"Phys","subject":"Physics","class":11,"url":"https://x.test/b.pdf","sha256":"$sha"}]}""",
        )
        assertEquals(2, r.entries.size)
        assertEquals(PackageType.BOOK, r.entries.first { it.id == "b1" }.type)
        assertEquals(11, r.entries.first { it.id == "b1" }.classLevel)
    }

    @Test fun badEntriesAreSkippedNotFatal() {
        val r = parse(
            """{"packages":[{"id":"ok","url":"https://x.test/a.zip"},{"id":"bad id","url":"https://x.test/a.zip"},
            {"id":"http","url":"http://x.test/a.zip"},{"id":"book","type":"book","subject":"Physics","url":"https://x.test/b.pdf"}]}""",
        )
        assertEquals(listOf("ok"), r.entries.map { it.id })
        assertEquals(3, r.errors.size)
    }

    @Test fun notJsonGivesError() {
        val r = parse("<html>nope</html>")
        assertTrue(r.entries.isEmpty())
        assertFalse(r.errors.isEmpty())
    }

    @Test fun gitHubLinksBecomeRawLinks() {
        assertEquals(
            "https://raw.githubusercontent.com/u/r/main/packs/index.json",
            GitHubLinks.normalize("https://github.com/u/r/blob/main/packs/index.json"),
        )
        assertEquals("https://raw.githubusercontent.com/u/r/HEAD/index.json", GitHubLinks.normalize("https://github.com/u/r"))
        assertEquals("https://raw.githubusercontent.com/u/r/HEAD/index.json", GitHubLinks.normalize("https://github.com/u/r.git/"))
        assertEquals("https://example.com/index.json", GitHubLinks.normalize("https://example.com/index.json"))
    }

    @Test fun linksAndMetaRoundTrip() {
        val links = listOf(IndexLink("1", "Src", "https://x.test/i.json"))
        assertEquals(links, SourceCodec.decodeLinks(SourceCodec.encodeLinks(links)))
        val meta = mapOf("b" to PaperMeta(coaching = "Allen", subject = "Physics", kind = "PYQ", year = 2020))
        assertEquals(meta, SourceCodec.decodeMeta(SourceCodec.encodeMeta(meta)))
        assertTrue(SourceCodec.decodeLinks("garbage").isEmpty())
    }

    private fun bank(id: String, subject: String, kind: String) =
        BankEntity(id, "T $id", 1, "NEET", null, subject, kind, "c", 10, 0L)

    @Test fun paperRowsGroupAndFilter() {
        val rows = PaperRows.build(
            listOf(bank("a", "Zoology", "PYQS"), bank("b", "Physics", "THEORY"), bank("c", "Chemistry", "MIXED")),
            mapOf("c" to PaperMeta(coaching = "Allen", kind = "PYQ")),
            setOf("b"),
        )
        val a = rows.first { it.bank.id == "a" }
        assertEquals("Biology", a.subjectGroup)
        assertEquals(PaperRows.TYPE_PYQ, a.type)
        assertEquals(PaperRows.TYPE_PYQ, rows.first { it.bank.id == "c" }.type)
        assertTrue(PaperFilter(completedOnly = true).matches(rows.first { it.bank.id == "b" }))
        assertFalse(PaperFilter(completedOnly = true).matches(a))
        assertTrue(PaperFilter(coaching = "allen", subject = "Chemistry").matches(rows.first { it.bank.id == "c" }))
    }

    @Test fun paperOrderIsNumeric() {
        assertEquals(listOf("p:1", "p:2", "p:10"), PaperRows.paperOrder(listOf("p:10", "p:2", "p:1")))
    }
}
