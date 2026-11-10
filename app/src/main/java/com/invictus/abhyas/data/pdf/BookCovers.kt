package com.invictus.abhyas.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.domain.ncert.BookNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Book covers from the internet (Open Library's public search + cover API), cached on the device.
 * Returns null when no cover is found or there is no network; the shelf then draws its own designed cover.
 */
object BookCovers {
    private const val USER_AGENT = "Abhyas/1.0 (Android study app)"
    private const val MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000

    suspend fun load(context: Context, book: BookEntity): Bitmap? = withContext(Dispatchers.IO) {
        if (book.status == BookStatus.FAILED) return@withContext null
        val key = coverKey(book) ?: return@withContext null
        val dir = File(context.filesDir, "covers-net").apply { mkdirs() }
        val file = File(dir, "$key.img")
        val miss = File(dir, "$key.miss")
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath)?.let { return@withContext it }
        if (miss.isFile && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS) return@withContext null
        val bytes = try {
            findCover(book)
        } catch (e: Exception) {
            return@withContext null // no network right now: try again next time
        }
        if (bytes == null) {
            runCatching { miss.writeText("1") }
            return@withContext null
        }
        runCatching { FileOutputStream(file).use { it.write(bytes) } }
        miss.delete()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** Stable cache key, only for Class 11/12 textbooks of a known subject. */
    fun coverKey(book: BookEntity): String? {
        val cls = book.classLevel?.takeIf { it == 11 || it == 12 } ?: return null
        val sub = subjectWord(book.subject) ?: return null
        return "$cls-$sub-${BookNaming.part(book.title) ?: 1}"
    }

    private fun subjectWord(subject: String): String? {
        val s = subject.lowercase()
        return when {
            s.startsWith("phys") -> "physics"
            s.startsWith("chem") -> "chemistry"
            s.startsWith("bio") -> "biology"
            s.startsWith("math") -> "mathematics"
            else -> null
        }
    }

    private fun roman(cls: Int) = if (cls == 12) "xii" else "xi"

    private fun findCover(book: BookEntity): ByteArray? {
        val cls = book.classLevel ?: return null
        val sub = subjectWord(book.subject) ?: return null
        val part = BookNaming.part(book.title)
        val q = buildString {
            append("ncert ").append(sub).append(" class ").append(roman(cls))
            if (part != null) append(" part ").append(part)
        }
        val url = "https://openlibrary.org/search.json?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fields=title,cover_i,author_name,publisher&limit=12"
        val json = JSONObject(http(url, 600_000)?.toString(Charsets.UTF_8) ?: return null)
        val docs = json.optJSONArray("docs") ?: return null
        var bestId = 0L
        var bestScore = 0
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val coverId = d.optLong("cover_i", 0L)
            if (coverId <= 0L) continue
            val title = d.optString("title").lowercase()
            if (!title.contains(sub.take(4))) continue
            val by = (d.optJSONArray("author_name")?.join(" ") ?: "") + " " + (d.optJSONArray("publisher")?.join(" ") ?: "")
            var score = 0
            if (by.lowercase().let { "ncert" in it || "national council" in it }) score += 3
            if (Regex("""\b${roman(cls)}\b|\b$cls\b""").containsMatchIn(title)) score += 2
            if (part != null && Regex("""\bpart\s*(0?$part|${romanPart(part)})\b""").containsMatchIn(title)) score += 1
            if (score > bestScore) { bestScore = score; bestId = coverId }
        }
        if (bestScore < 3) return null
        val bytes = http("https://covers.openlibrary.org/b/id/$bestId-L.jpg?default=false", 4_000_000) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return if (o.outWidth >= 150 && o.outHeight >= o.outWidth * 1.1f) bytes else null
    }

    private fun romanPart(p: Int) = when (p) { 1 -> "i"; 2 -> "ii"; 3 -> "iii"; else -> "iv" }

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
