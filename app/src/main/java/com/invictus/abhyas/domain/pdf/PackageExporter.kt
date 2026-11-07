package com.invictus.abhyas.domain.pdf

import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.pack.AbhyasJson
import com.invictus.abhyas.domain.pack.BankManifest
import com.invictus.abhyas.domain.pack.QuestionDto
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportQuestion(val dto: QuestionDto, val imageName: String? = null, val imageBytes: (() -> ByteArray?)? = null)

class ExportMeta(
    val bankId: String,
    val title: String,
    val version: Int,
    val exam: String,
    val classLevel: Int?,
    val subject: String,
    val kind: String,
)

/** Writes an Abhyas bank package zip: manifest.json + questions.json (+ images/). Pure JVM. */
object PackageExporter {

    private val pretty = kotlinx.serialization.json.Json(from = AbhyasJson.json) { prettyPrint = true }

    class Built(val manifestBytes: ByteArray, val questionsBytes: ByteArray)

    fun build(meta: ExportMeta, questions: List<ExportQuestion>): Built {
        val dtos = questions.map { q ->
            if (q.imageName != null) q.dto.copy(imagePath = "images/${q.imageName}") else q.dto.copy(imagePath = null)
        }
        val questionsBytes = pretty.encodeToString(ListSerializer(QuestionDto.serializer()), dtos).toByteArray(Charsets.UTF_8)
        val manifest = BankManifest(
            id = meta.bankId,
            version = meta.version,
            title = meta.title,
            exam = meta.exam,
            classLevel = meta.classLevel,
            subject = meta.subject,
            kind = meta.kind,
            sha256 = Sha256.hex(questionsBytes),
            questionCount = dtos.size,
        )
        val manifestBytes = pretty.encodeToString(BankManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        return Built(manifestBytes, questionsBytes)
    }

    fun writeZip(out: OutputStream, meta: ExportMeta, questions: List<ExportQuestion>) {
        val built = build(meta, questions)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(built.manifestBytes); zip.closeEntry()
            zip.putNextEntry(ZipEntry("questions.json")); zip.write(built.questionsBytes); zip.closeEntry()
            val written = HashSet<String>()
            for (q in questions) {
                val name = q.imageName ?: continue
                if (!written.add(name)) continue
                val bytes = q.imageBytes?.invoke() ?: continue
                zip.putNextEntry(ZipEntry("images/$name")); zip.write(bytes); zip.closeEntry()
            }
        }
    }

    fun zipBytes(meta: ExportMeta, questions: List<ExportQuestion>): ByteArray =
        ByteArrayOutputStream().also { writeZip(it, meta, questions) }.toByteArray()
}
