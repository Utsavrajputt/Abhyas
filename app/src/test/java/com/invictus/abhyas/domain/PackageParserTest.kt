package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.TestData.manifest
import com.invictus.abhyas.domain.TestData.mcq
import com.invictus.abhyas.domain.engine.ContentHash
import com.invictus.abhyas.domain.model.hasAnswer
import com.invictus.abhyas.domain.pack.PackageParseResult
import com.invictus.abhyas.domain.pack.PackageParser
import com.invictus.abhyas.domain.pack.QuestionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageParserTest {
    private fun parse(manifest: String, questions: String) =
        PackageParser.parse(manifest.toByteArray(), questions.toByteArray())

    private fun ok(q: String, count: Int): PackageParseResult.Success {
        val r = parse(manifest(q, count), q)
        return r as? PackageParseResult.Success ?: throw AssertionError("expected success, got $r")
    }

    @Test fun validPackageImportsAndInheritsManifestDefaults() {
        val q = "[${mcq("1")},${mcq("2")}]"
        val r = ok(q, 2)
        assertEquals(2, r.questions.size)
        val first = r.questions.first().question
        assertEquals("test_bank:1", first.id)
        assertEquals("Physics", first.subject)
        assertEquals(11, first.classLevel)
        assertTrue(first.answerVerified)
    }

    @Test fun checksumMismatchRejected() {
        val q = "[${mcq("1")}]"
        val r = parse(manifest(q, 1, sha = "0".repeat(64)), q)
        assertTrue(r is PackageParseResult.Failure && "Checksum" in r.reason)
    }

    @Test fun tamperedQuestionsFailChecksum() {
        val q = "[${mcq("1")}]"
        val r = parse(manifest(q, 1), q.replace("TEST A", "TEST X"))
        assertTrue(r is PackageParseResult.Failure)
    }

    @Test fun countMismatchRejected() {
        val q = "[${mcq("1")}]"
        assertTrue(parse(manifest(q, 5), q) is PackageParseResult.Failure)
    }

    @Test fun emptyAndGarbagePackagesFailGracefully() {
        assertTrue(parse(manifest("[]", 0), "[]") is PackageParseResult.Failure)
        assertTrue(parse("not json", "[]") is PackageParseResult.Failure)
        val notArray = """{"a":1}"""
        assertTrue(parse(manifest(notArray, 1), notArray) is PackageParseResult.Failure)
        val broken = "[{"
        assertTrue(parse(manifest(broken, 1), broken) is PackageParseResult.Failure)
    }

    @Test fun badManifestFieldsRejected() {
        val q = "[${mcq("1")}]"
        val good = manifest(q, 1)
        assertTrue(parse(good.replace("NEET", "SAT"), q) is PackageParseResult.Failure)
        assertTrue(parse(good.replace("theory", "weird"), q) is PackageParseResult.Failure)
        assertTrue(parse(good.replace("test_bank", "bad:id"), q) is PackageParseResult.Failure)
        assertTrue(parse(good.replace("\"class\":11", "\"class\":9"), q) is PackageParseResult.Failure)
    }

    @Test fun oneBadQuestionDoesNotBlockOthers() {
        val bad = """{"id":"2","type":"SINGLE_MCQ","statement":"TEST","payload":{"options":[{"id":"a","text":"x"}],"correctId":"a"}}"""
        val malformed = """{"id":"3","type":"SINGLE_MCQ"}"""
        val unknownType = """{"id":"4","type":"ESSAY","statement":"TEST","payload":{}}"""
        val q = "[${mcq("1")},$bad,$malformed,$unknownType]"
        val r = ok(q, 4)
        assertEquals(1, r.questions.size)
        assertEquals(listOf(1, 2, 3), r.errors.map { it.index })
        assertEquals(listOf("2", "3", "4"), r.errors.map { it.id })
    }

    @Test fun missingAnswerIsKeptButUnverified() {
        val r = ok("[${mcq("1", correct = null)}]", 1)
        val q = r.questions.single().question
        assertFalse(q.answerVerified)
        assertFalse(q.payload.hasAnswer())
    }

    @Test fun explicitVerifiedWithoutAnswerIsAnError() {
        val s = """{"id":"1","type":"SINGLE_MCQ","statement":"TEST","answerVerified":true,"payload":{"options":[{"id":"a","text":"x"},{"id":"b","text":"y"}]}}"""
        val r = ok("[$s]", 1)
        assertEquals(0, r.questions.size)
        assertEquals(1, r.errors.size)
    }

    @Test fun duplicateIdsAndDuplicateContentAreDeduped() {
        val sameContentOtherId = mcq("2", statement = "TEST statement 1")
        val sameId = mcq("1", statement = "TEST different")
        val q = "[${mcq("1")},$sameContentOtherId,$sameId]"
        val r = ok(q, 3)
        assertEquals(1, r.questions.size)
        assertEquals(1, r.duplicatesInPackage)
        assertEquals(1, r.errors.size)
    }

    @Test fun contentHashIgnoresWhitespaceCaseAnswerAndId() {
        val a = ok("[${mcq("1", statement = "TEST  Statement")}]", 1).questions.single()
        val b = ok("[${mcq("9", statement = "test statement", correct = "b")}]", 1).questions.single()
        assertEquals(a.contentHash, b.contentHash)
        val c = ok("[${mcq("1", statement = "TEST other")}]", 1).questions.single()
        assertTrue(a.contentHash != c.contentHash)
    }

    @Test fun snapshotRoundTripPreservesQuestion() {
        val q = ok("[${mcq("1")}]", 1).questions.single().question
        val back = QuestionFactory.fromSnapshotJson(QuestionFactory.toSnapshotJson(q))
        assertEquals(q, back)
        assertEquals(ContentHash.of(q), ContentHash.of(back))
    }
}
