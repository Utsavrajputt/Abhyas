package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.engine.Randomizer
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.pack.PackageReadException
import com.invictus.abhyas.domain.pack.PackageZip
import com.invictus.abhyas.domain.query.KindFilter
import com.invictus.abhyas.domain.query.QuestionFilter
import com.invictus.abhyas.domain.query.QuestionQuery
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2Test {
    private val ids = (1..20).map { "q$it" }

    @Test fun randomizerNoDuplicatesAndExactCount() {
        val p = Randomizer.pick(ids + ids, 10, random = Random(1))
        assertEquals(10, p.ids.size); assertEquals(10, p.ids.toSet().size); assertEquals(0, p.shortfall)
    }

    @Test fun randomizerAvoidsRecentWhenPossible() {
        val recent = ids.take(10).toSet()
        val p = Randomizer.pick(ids, 10, recent, random = Random(2))
        assertTrue(p.ids.none { it in recent }); assertEquals(0, p.fromRecent)
    }

    @Test fun randomizerFallsBackToRecentThenReportsShortfall() {
        val recent = ids.take(15).toSet()
        val p = Randomizer.pick(ids, 10, recent, random = Random(3))
        assertEquals(10, p.ids.size); assertEquals(5, p.fromRecent)
        val strict = Randomizer.pick(ids, 10, recent, allowRecentFallback = false, random = Random(3))
        assertEquals(5, strict.ids.size); assertEquals(5, strict.shortfall)
        val tooMany = Randomizer.pick(ids, 50)
        assertEquals(20, tooMany.ids.size); assertEquals(30, tooMany.shortfall)
        assertTrue(Randomizer.pick(emptyList(), 5).ids.isEmpty())
        assertTrue(Randomizer.pick(ids, 0).ids.isEmpty())
    }

    @Test fun queryBuilderParameterisesFilters() {
        val b = QuestionQuery.select(
            QuestionFilter(exam = Exam.NEET, subject = "Physics", type = QuestionType.NUMERICAL, kind = KindFilter.THEORY),
        )
        assertEquals(listOf<Any>("NEET", "Physics", "NUMERICAL"), b.args)
        assertTrue("exam = ?" in b.sql && "type <> 'NUMERICAL'" in b.sql && b.sql.endsWith("ORDER BY id"))
        assertTrue(!b.sql.contains("Physics"))
    }

    @Test fun emptyFilterHasNoWhere() {
        val b = QuestionQuery.count(QuestionFilter())
        assertEquals("SELECT COUNT(*) FROM questions", b.sql); assertTrue(b.args.isEmpty())
    }

    @Test fun searchEscapesLikeWildcardsAndAndsWords() {
        val b = QuestionQuery.select(QuestionFilter(query = "50%_off  force"))
        assertEquals(8, b.args.size)
        assertEquals("%50\\%\\_off%", b.args.first())
        assertEquals(2, Regex("\\(statement LIKE").findAll(b.sql).count())
    }

    @Test fun bookmarkAndMistakeFilters() {
        val b = QuestionQuery.select(QuestionFilter(bookmarkedOnly = true, incorrectOnly = true, pyqOnly = true))
        assertTrue("FROM bookmarks" in b.sql && "FROM mistakes" in b.sql && "year IS NOT NULL" in b.sql)
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun zipFindsFilesAtAnyDepth() {
        val f = PackageZip.read(ByteArrayInputStream(zip("pack/manifest.json" to "M", "pack/questions.json" to "Q", "pack/x.txt" to "z")))
        assertEquals("M", String(f.manifest)); assertEquals("Q", String(f.questions))
    }

    @Test fun zipMissingFileIsAGracefulError() {
        val e = runCatching { PackageZip.read(ByteArrayInputStream(zip("manifest.json" to "M"))) }.exceptionOrNull()
        assertTrue(e is PackageReadException)
        val notZip = runCatching { PackageZip.read(ByteArrayInputStream("garbage".toByteArray())) }.exceptionOrNull()
        assertTrue(notZip is PackageReadException)
    }
}
