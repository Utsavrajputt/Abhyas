package com.invictus.abhyas.domain.pdf

import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.pack.AbhyasJson
import com.invictus.abhyas.domain.pack.PackageParseResult
import com.invictus.abhyas.domain.pack.PackageParser
import com.invictus.abhyas.domain.pack.PackageZip
import com.invictus.abhyas.domain.pack.PayloadCodec
import com.invictus.abhyas.domain.pack.QuestionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PackageExporterTest {

    private fun dto(id: String, correct: String?) = QuestionDto(
        id = id,
        type = "SINGLE_MCQ",
        statement = "TEST statement $id",
        payload = PayloadCodec.encode(SingleChoicePayload(listOf(Option("a", "TEST a"), Option("b", "TEST b")), correct)),
        exam = "NEET",
        subject = "Physics",
        chapter = "TEST chapter",
        sourcePage = 3,
    )

    @Test
    fun exportedZip_reimportsIdentically() {
        val meta = ExportMeta("test_pdf_bank", "TEST bank", 1, "NEET", 11, "Physics", "PYQS")
        val qs = listOf(ExportQuestion(dto("q1", "b")), ExportQuestion(dto("q2", null)))
        val zip = PackageExporter.zipBytes(meta, qs)

        val files = PackageZip.read(ByteArrayInputStream(zip))
        val result = PackageParser.parse(files.manifest, files.questions) as PackageParseResult.Success
        assertEquals(2, result.questions.size)
        assertTrue(result.errors.isEmpty())
        val q1 = result.questions[0].question
        assertEquals("test_pdf_bank:q1", q1.id)
        assertEquals("TEST statement q1", q1.statement)
        assertEquals("TEST chapter", q1.chapter)
        assertTrue(q1.answerVerified)
        assertEquals("b", (q1.payload as SingleChoicePayload).correctId)
        // A question without an answer stays unverified instead of being guessed.
        assertTrue(!result.questions[1].question.answerVerified)
    }

    @Test
    fun imagesAreWrittenAndReferenced() {
        val meta = ExportMeta("test_pdf_bank", "TEST bank", 1, "NEET", null, "Physics", "PYQS")
        val q = ExportQuestion(dto("q1", "a"), imageName = "q1.jpg", imageBytes = { byteArrayOf(1, 2, 3) })
        val zip = PackageExporter.zipBytes(meta, listOf(q))
        val seen = HashMap<String, ByteArray>()
        val files = PackageZip.read(ByteArrayInputStream(zip)) { name ->
            object : java.io.ByteArrayOutputStream() {
                override fun close() { seen[name] = toByteArray(); super.close() }
            }
        }
        assertEquals(listOf<Byte>(1, 2, 3), seen["q1.jpg"]?.toList())
        val list = AbhyasJson.json.parseToJsonElement(files.questions.toString(Charsets.UTF_8)).toString()
        assertTrue(list.contains("images/q1.jpg"))
    }
}
