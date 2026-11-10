package com.invictus.abhyas.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.domain.ncert.BookNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Book covers. NCERT textbooks get their cover from the NCERT website at runtime (found on the book's
 * page on ncert.nic.in, then cached on the device); everything else, or when the site cannot be
 * reached, falls back to a thumbnail of the PDF's first page.
 */
object BookCovers {
    private const val BASE = "https://ncert.nic.in/"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000
    private val IMG = Regex("""<img[^>]+?src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val IMG_EXT = Regex("""\.(jpe?g|png|webp)(\?.*)?$""", RegexOption.IGNORE_CASE)
    private val SKIP = Regex("""logo|icon|banner|header|footer|flag|sprite|arrow|button|social|emblem|bg|blank|spacer""", RegexOption.IGNORE_CASE)

    suspend fun load(context: Context, book: BookEntity): Bitmap? = withContext(Dispatchers.IO) {
        if (book.status == BookStatus.FAILED) return@withContext null
        online(context, book)?.let { return@withContext it }
        load(context, book.id, book.localPath)
    }

    /** First page of the PDF, rendered once and cached as a small JPEG. */
    suspend fun load(context: Context, bookId: String, pdfPath: String): Bitmap? = withContext(Dispatchers.IO) {
        val pdf = File(pdfPath)
        if (!pdf.isFile) return@withContext null
        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val cached = File(dir, "$bookId.jpg")
        if (cached.isFile && cached.lastModified() >= pdf.lastModified()) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return@withContext it }
        }
        try {
            PdfPageRenderer(pdf).use { r ->
                if (r.pageCount < 1) return@withContext null
                val bmp = r.render(1, 420).bitmap
                FileOutputStream(cached).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    /** NCERT code of a Class 11/12 PCBM book (keph1 = Class 11 Physics Part I), or null for any other book. */
    fun ncertCode(book: BookEntity): String? {
        val prefix = when (book.classLevel) { 11 -> "k"; 12 -> "l"; else -> return null }
        val s = book.subject.lowercase()
        val sub = when {
            s.startsWith("phys") -> "ph"
            s.startsWith("chem") -> "ch"
            s.startsWith("bio") -> "bo"
            s.startsWith("math") -> "mh"
            else -> return null
        }
        return prefix + "e" + sub + (BookNaming.part(book.title) ?: 1)
    }

    private fun online(context: Context, book: BookEntity): Bitmap? {
        val code = ncertCode(book) ?: return null
        val dir = File(context.filesDir, "covers-net").apply { mkdirs() }
        val file = File(dir, "$code.img")
        val miss = File(dir, "$code.miss")
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath)?.let { return it } else {
            if (miss.isFile && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS) return null
        }
        val bytes = try { fetchCover(code) } catch (e: Exception) { null }
        if (bytes == null) { runCatching { miss.writeText("1") }; return null }
        file.writeBytes(bytes)
        miss.delete()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** Reads the book's page on ncert.nic.in and downloads the first portrait image that looks like a cover. */
    private fun fetchCover(code: String): ByteArray? {
        for (n in intArrayOf(8, 7, 9, 10, 6)) {
            val base = BASE + "textbook.php?$code=0-$n"
            val html = http(base, 1_000_000)?.toString(Charsets.UTF_8) ?: continue
            val candidates = IMG.findAll(html).map { it.groupValues[1].trim() }
                .filter { IMG_EXT.containsMatchIn(it) && !SKIP.containsMatchIn(it.substringAfterLast('/')) }
                .distinct()
                .sortedBy { src ->
                    val l = src.lowercase()
                    when { code in l -> 0; "cover" in l || "book" in l || "textbook" in l -> 1; else -> 2 }
                }
                .take(4).toList()
            for (src in candidates) {
                val url = runCatching { URL(URL(base), src).toString() }.getOrNull() ?: continue
                val bytes = http(url, 3_000_000) ?: continue
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth >= 120 && opts.outHeight >= 150 && opts.outHeight >= opts.outWidth * 1.1f) return bytes
            }
            return null
        }
        return null
    }

    private fun http(url: String, max: Int): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) return null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    if (out.size() > max) return null
                }
            }
            out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }
}
