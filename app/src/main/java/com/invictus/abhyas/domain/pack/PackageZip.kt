package com.invictus.abhyas.domain.pack

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class PackageFiles(val manifest: ByteArray, val questions: ByteArray)

class PackageReadException(message: String) : Exception(message)

/** Extracts manifest.json + questions.json from a zip stream (any folder depth). Pure JVM. */
object PackageZip {
    const val MAX_FILE_BYTES = 128L * 1024 * 1024

    /**
     * [imageSink] receives every `images/<file>` entry (file name only, so paths cannot escape) and
     * returns the stream to write it to, or null to skip it.
     */
    fun read(input: InputStream, imageSink: ((String) -> OutputStream?)? = null): PackageFiles {
        var manifest: ByteArray? = null
        var questions: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/').lowercase()
                if (name.startsWith(".") || entry.name.startsWith("__MACOSX")) continue
                val inImages = imageSink != null && entry.name.replace('\\', '/').split('/').dropLast(1).any { it.equals("images", true) }
                if (inImages && name.isNotEmpty()) {
                    val rawName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    imageSink!!(rawName)?.use { out ->
                        val bytes = readBounded(zip, rawName, 16L * 1024 * 1024)
                        out.write(bytes)
                    }
                    continue
                }
                when (name) {
                    "manifest.json" -> if (manifest == null) manifest = readBounded(zip, name)
                    "questions.json" -> if (questions == null) questions = readBounded(zip, name)
                }
            }
        }
        return PackageFiles(
            manifest ?: throw PackageReadException("manifest.json not found in the zip"),
            questions ?: throw PackageReadException("questions.json not found in the zip"),
        )
    }

    fun readBounded(input: InputStream, label: String, max: Long = MAX_FILE_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw PackageReadException("$label is too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
