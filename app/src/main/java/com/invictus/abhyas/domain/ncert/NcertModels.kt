package com.invictus.abhyas.domain.ncert

import com.invictus.abhyas.domain.engine.Sha256
import kotlinx.serialization.Serializable

/**
 * Stable pointer into a book: `(bookId, page, paragraphIndex, textHash)`. The hash lets the reader
 * re-find the passage if paragraph numbering shifts (different PDF edition / re-index).
 */
@Serializable
data class NcertRef(
    val bookId: String,
    val page: Int,
    val paragraph: Int,
    val textHash: String,
    val chapter: String? = null,
)

/** One text line with its baseline position (PDF points from the top of the page) and glyph height. */
data class TextLine(val text: String, val y: Float, val height: Float)

data class Paragraph(val index: Int, val text: String, val top: Float, val bottom: Float, val hash: String)

object PassageHash {
    /** 16 hex chars of SHA-256 over the text with everything but letters/digits removed, lower-cased. */
    fun of(text: String): String {
        val norm = text.lowercase().filter { it.isLetterOrDigit() }
        return Sha256.hex(norm.toByteArray(Charsets.UTF_8)).take(16)
    }

    val VALID = Regex("^[0-9a-f]{16}$")
}

/** Groups the lines of one page into paragraphs by vertical spacing. Pure. */
object ParagraphSplitter {

    fun split(rawLines: List<TextLine>, minChars: Int = 8): List<Paragraph> {
        val lines = rawLines.filter { it.text.isNotBlank() }.sortedBy { it.y }
        if (lines.isEmpty()) return emptyList()
        val pitches = lines.zipWithNext { a, b -> b.y - a.y }.filter { it > 0.5f }.sorted()
        val typical = if (pitches.isEmpty()) lines.first().height.coerceAtLeast(8f) * 1.2f else pitches[pitches.size / 2]
        val groups = ArrayList<MutableList<TextLine>>()
        for (l in lines) {
            val last = groups.lastOrNull()?.lastOrNull()
            if (last == null || l.y - last.y > typical * 1.45f) groups += arrayListOf(l) else groups.last() += l
        }
        val out = ArrayList<Paragraph>()
        for (g in groups) {
            val text = g.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim()
            if (text.count { it.isLetterOrDigit() } < minChars) continue
            val top = (g.first().y - g.first().height).coerceAtLeast(0f)
            val bottom = g.last().y + g.last().height * 0.35f
            out += Paragraph(out.size, text, top, bottom, PassageHash.of(text))
        }
        return out
    }
}

data class BookChapter(val index: Int, val title: String, val startPage: Int, val endPage: Int)

/** Chapter detection fallback for PDFs without an outline: looks for "Chapter N"/"Unit N" near page tops. */
object ChapterGuess {
    private val HEAD = Regex("^(chapter|unit)\\s+([a-z0-9]+)\\b[\\s:.\\-\\u2013]*(.*)$", RegexOption.IGNORE_CASE)

    /** [pageHeads]: page (1-based) -> its first lines. */
    fun guess(pageHeads: Map<Int, List<String>>, pageCount: Int): List<BookChapter> {
        val starts = ArrayList<Pair<Int, String>>()
        for (page in pageHeads.keys.sorted()) {
            val heads = pageHeads.getValue(page)
            for ((i, l) in heads.withIndex()) {
                val m = HEAD.matchEntire(l.trim()) ?: continue
                val rest = m.groupValues[3].trim()
                val title = if (rest.length >= 3) rest else heads.getOrNull(i + 1)?.trim().orEmpty()
                val label = "${m.groupValues[1].replaceFirstChar { it.uppercase() }} ${m.groupValues[2]}"
                starts += page to (if (title.isNotBlank()) "$label: $title" else label)
                break
            }
        }
        return fromStarts(starts, pageCount)
    }

    /** Turns (startPage, title) pairs into contiguous chapters; falls back to one chapter for the whole book. */
    fun fromStarts(raw: List<Pair<Int, String>>, pageCount: Int): List<BookChapter> {
        val starts = raw.filter { it.first in 1..pageCount }.sortedBy { it.first }.distinctBy { it.first }
        if (starts.isEmpty()) return listOf(BookChapter(0, "Whole book", 1, pageCount))
        val out = ArrayList<BookChapter>()
        if (starts.first().first > 1) out += BookChapter(0, "Front matter", 1, starts.first().first - 1)
        for ((i, s) in starts.withIndex()) {
            val end = if (i + 1 < starts.size) starts[i + 1].first - 1 else pageCount
            out += BookChapter(out.size, s.second, s.first, end)
        }
        return out
    }
}

/** An entry of a hosted book index (`{"formatVersion":1,"books":[...]}`). */
data class IndexEntry(
    val id: String,
    val title: String,
    val classLevel: Int?,
    val subject: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long?,
    val note: String?,
)

class IndexParseResult(val entries: List<IndexEntry>, val errors: List<String>)

object BookIndexParser {
    private val ID = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val SHA = Regex("^[0-9a-fA-F]{64}$")

    fun parse(bytes: ByteArray): IndexParseResult {
        val root = try {
            kotlinx.serialization.json.Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return IndexParseResult(emptyList(), listOf("Index is not valid JSON"))
        }
        val obj = root as? kotlinx.serialization.json.JsonObject ?: return IndexParseResult(emptyList(), listOf("Index must be a JSON object"))
        val version = (obj["formatVersion"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 1
        if (version != 1) return IndexParseResult(emptyList(), listOf("Unsupported index formatVersion $version"))
        val arr = obj["books"] as? kotlinx.serialization.json.JsonArray ?: return IndexParseResult(emptyList(), listOf("Index has no \"books\" array"))
        val entries = ArrayList<IndexEntry>()
        val errors = ArrayList<String>()
        val seen = HashSet<String>()
        arr.forEachIndexed { i, el ->
            val o = el as? kotlinx.serialization.json.JsonObject
            fun str(k: String) = (o?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            fun num(k: String) = (o?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
            val id = str("id")
            val problems = ArrayList<String>()
            if (id == null || !ID.matches(id)) problems += "bad id"
            if (id != null && !seen.add(id)) problems += "duplicate id"
            val url = str("url")
            if (url == null || !url.startsWith("https://")) problems += "url must be https"
            val sha = str("sha256")
            if (sha == null || !SHA.matches(sha)) problems += "sha256 must be 64 hex chars"
            val subject = str("subject")
            if (subject.isNullOrBlank()) problems += "subject missing"
            val cls = num("class")?.toInt()
            if (cls != null && cls != 11 && cls != 12) problems += "class must be 11 or 12"
            if (problems.isNotEmpty()) {
                errors += "Book #${i + 1}${id?.let { " ($it)" } ?: ""}: ${problems.joinToString()}"
            } else {
                entries += IndexEntry(id!!, str("title") ?: id, cls, subject!!, url!!, sha!!.lowercase(), num("sizeBytes"), str("note"))
            }
        }
        return IndexParseResult(entries, errors)
    }
}
