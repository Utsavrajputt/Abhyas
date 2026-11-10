package com.invictus.abhyas.data.pyq

import android.content.Context
import android.net.Uri
import com.invictus.abhyas.data.repo.BankImporter
import com.invictus.abhyas.data.repo.ImportResult
import com.invictus.abhyas.data.repo.PackageReader
import com.invictus.abhyas.domain.pack.PackageReadException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One downloadable previous-year-paper pack listed in the hosted index. */
data class PyqEntry(
    /** Bank id the pack installs as (matches manifest.json `id`). */
    val id: String,
    val exam: String,
    val year: Int,
    val title: String,
    val url: String,
    /** Hex SHA-256 of the zip file itself; the download is rejected if it does not match. */
    val sha256: String,
    val questions: Int,
)

sealed interface PyqInstallResult {
    data class Installed(val message: String, val bankId: String? = null) : PyqInstallResult
    data class Failed(val message: String) : PyqInstallResult
}

/**
 * Previous-year-paper store: reads a hosted `index.json`, downloads a year's pack (a normal
 * Abhyas question-bank zip), verifies its SHA-256 and installs it through the same importer
 * as "Import zip". Index format:
 * `{"packs":[{"id":"neet-pyq-2024","exam":"NEET","year":2024,"title":"NEET UG 2024",
 * "url":"https://.../neet-pyq-2024.zip","sha256":"<hex>","questions":180}]}`
 */
class PyqRepository(
    private val context: Context,
    private val reader: PackageReader,
    private val importer: BankImporter,
) {
    suspend fun fetchIndex(url: String): List<PyqEntry> = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "The PYQ index URL must start with https://" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) throw IOException("Server answered ${conn.responseCode}")
            val bytes = conn.inputStream.use { it.readBounded(MAX_INDEX_BYTES) }
            parseIndex(bytes)
        } finally {
            conn.disconnect()
        }
    }

    /** Fetches any index.json (NCERT, PYQ or an "Other link") and parses it with [PackageIndexParser]. */
    suspend fun fetchPackageIndex(link: String): ParsedIndex = withContext(Dispatchers.IO) {
        val url = GitHubLinks.normalize(link)
        require(url.startsWith("https://")) { "The link must start with https://" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) throw IOException("Server answered ${conn.responseCode}")
            PackageIndexParser.parse(conn.inputStream.use { it.readBounded(MAX_INDEX_BYTES) })
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads a paper / question-bank zip from an index entry (checksum-verified when given) and installs it. */
    suspend fun installPackage(entry: PackageEntry): PyqInstallResult = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "pkg_${entry.id}.zip")
        val stage = File(context.cacheDir, "pkgstage_${System.nanoTime()}")
        try {
            fetchTo(entry.url, zip, MAX_PACK_BYTES, entry.sha256)
            val files = reader.readZip(Uri.fromFile(zip), stage)
            when (val r = importer.install(files.manifest, files.questions, stage)) {
                is ImportResult.Success -> PyqInstallResult.Installed("${r.report.imported} questions added", r.report.bankId)
                is ImportResult.AlreadyInstalled -> PyqInstallResult.Installed("Already installed", r.bankId)
                is ImportResult.Failure -> PyqInstallResult.Failed(r.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PackageReadException) {
            PyqInstallResult.Failed(e.message ?: "This is not an Abhyas question-bank zip")
        } catch (e: Exception) {
            PyqInstallResult.Failed("Download failed: ${e.message}")
        } finally {
            zip.delete()
            stage.deleteRecursively()
        }
    }

    suspend fun install(entry: PyqEntry): PyqInstallResult = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "pyq_${entry.id}.zip")
        val stage = File(context.cacheDir, "pyqstage_${System.nanoTime()}")
        try {
            fetchTo(entry.url, zip, MAX_PACK_BYTES, entry.sha256)
            val files = reader.readZip(Uri.fromFile(zip), stage)
            when (val r = importer.install(files.manifest, files.questions, stage)) {
                is ImportResult.Success -> PyqInstallResult.Installed("${r.report.imported} questions added")
                is ImportResult.AlreadyInstalled -> PyqInstallResult.Installed("Already installed")
                is ImportResult.Failure -> PyqInstallResult.Failed(r.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PackageReadException) {
            PyqInstallResult.Failed(e.message ?: "Unreadable package")
        } catch (e: Exception) {
            PyqInstallResult.Failed("Download failed: ${e.message}")
        } finally {
            zip.delete()
            stage.deleteRecursively()
        }
    }

    /** Fetches a web page (https, up to 3 MB) and lists the .pdf / .zip links in it. */
    suspend fun scanPage(pageUrl: String): List<FoundLink> = withContext(Dispatchers.IO) {
        require(pageUrl.startsWith("https://")) { "The page link must start with https://" }
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,*/*")
            if (conn.responseCode != 200) throw IOException("Server answered ${conn.responseCode}")
            val html = String(conn.inputStream.use { it.readBounded(3 * 1024 * 1024) }, Charsets.UTF_8)
            // After redirects the final address is the base for relative links.
            PageLinkScanner.parse(html, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads a user-added pack zip (no checksum is available) and installs it as a question bank. */
    suspend fun installPack(link: PaperLink): PyqInstallResult = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "pack_${link.id}.zip")
        val stage = File(context.cacheDir, "packstage_${System.nanoTime()}")
        try {
            fetchTo(link.url, zip, MAX_PACK_BYTES, null)
            val files = reader.readZip(Uri.fromFile(zip), stage)
            when (val r = importer.install(files.manifest, files.questions, stage)) {
                is ImportResult.Success -> PyqInstallResult.Installed("${r.report.imported} questions added")
                is ImportResult.AlreadyInstalled -> PyqInstallResult.Installed("Already installed")
                is ImportResult.Failure -> PyqInstallResult.Failed(r.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PackageReadException) {
            PyqInstallResult.Failed(e.message ?: "This is not an Abhyas question-bank zip")
        } catch (e: Exception) {
            PyqInstallResult.Failed("Download failed: ${e.message}")
        } finally {
            zip.delete()
            stage.deleteRecursively()
        }
    }

    /** Downloads a user-added paper PDF into the cache and returns it; PDF Studio imports it from there. */
    suspend fun downloadPaperPdf(link: PaperLink): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "papers").apply { mkdirs() }
        // Keep the folder small: drop earlier downloads older than a day.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }?.forEach { it.delete() }
        val slug = link.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "paper" }
        val target = File(dir, "$slug.pdf")
        fetchTo(link.url, target, MAX_PDF_BYTES, null)
        val head = ByteArray(8)
        val n = target.inputStream().use { it.read(head) }
        if (n < 4 || String(head, 0, 4, Charsets.ISO_8859_1) != "%PDF") {
            target.delete()
            throw IOException("The link did not return a PDF (it may need a login or open a web page)")
        }
        target
    }

    private fun fetchTo(url: String, target: File, maxBytes: Long, expectedSha256: String?) {
        require(url.startsWith("https://")) { "Only https:// links are allowed" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) throw IOException("Server answered ${conn.responseCode}")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("File is larger than ${maxBytes / (1024 * 1024)} MB")
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            if (expectedSha256 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch - the file was changed or damaged")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val MAX_INDEX_BYTES = 1024 * 1024
        private const val MAX_PACK_BYTES = 120L * 1024 * 1024
        private const val MAX_PDF_BYTES = 150L * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")
        private val SHA_REGEX = Regex("[0-9a-fA-F]{64}")

        /** Pure parser (no Android calls besides org.json): invalid entries are skipped, not fatal. */
        fun parseIndex(bytes: ByteArray): List<PyqEntry> {
            val packs = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("packs") ?: return emptyList()
            val out = mutableListOf<PyqEntry>()
            for (i in 0 until packs.length()) {
                val o = packs.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val url = o.optString("url")
                val sha = o.optString("sha256")
                val year = o.optInt("year", 0)
                if (!ID_REGEX.matches(id) || !url.startsWith("https://") || !SHA_REGEX.matches(sha) || year !in 1990..2100) continue
                out += PyqEntry(
                    id = id,
                    exam = o.optString("exam", "NEET"),
                    year = year,
                    title = o.optString("title").ifBlank { "$year" },
                    url = url,
                    sha256 = sha.lowercase(),
                    questions = o.optInt("questions", 0).coerceAtLeast(0),
                )
            }
            return out.distinctBy { it.id }
        }

        private fun java.io.InputStream.readBounded(max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) throw IOException("Index is larger than ${max / 1024} KB")
            }
            return out.toByteArray()
        }
    }
}
