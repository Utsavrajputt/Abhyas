package com.invictus.abhyas.domain.ncert

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parts an NCERT chapter-wise download is made of (`keph1ps`, `keph101`..., `keph1a1`, `keph1an`). */
enum class SectionKind { PRELIMS, CHAPTER, APPENDIX, ANSWERS }

@Serializable
data class LayoutSection(
    val kind: SectionKind,
    val number: Int,
    val title: String,
    /** 1-based page in the merged book. */
    val startPage: Int,
    val pages: Int,
)

/** One file of the zip, already measured (page count) and, for chapters, titled. */
data class PartInfo(val kind: SectionKind, val number: Int, val pages: Int, val title: String?)

/**
 * How a merged chapter-wise book is laid out: which pages are prelims, chapters, appendices and
 * answers, and how a page of the merged PDF maps to the page number printed in the textbook.
 * Stored next to the book as `layout.json`. Pure and unit-tested.
 */
@Serializable
data class BookLayout(val sections: List<LayoutSection>, val pageOffset: Int) {
    val prelimsPages: Int get() = sections.filter { it.kind == SectionKind.PRELIMS }.sumOf { it.pages }

    /** The number printed on the page: roman for prelims (i, ii, ...), arabic after that. */
    fun printedLabel(page: Int): String =
        if (page <= prelimsPages) roman(page) else (page + pageOffset).coerceAtLeast(1).toString()

    /** True for pages of a real chapter (not prelims, appendices or the answer key). */
    fun isContent(page: Int): Boolean =
        sections.any { it.kind == SectionKind.CHAPTER && page >= it.startPage && page < it.startPage + it.pages }

    fun chapters(): List<BookChapter> =
        sections.mapIndexed { i, s -> BookChapter(i, s.title, s.startPage, s.startPage + s.pages - 1) }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): BookLayout? = runCatching { JSON.decodeFromString(serializer(), text) }.getOrNull()

        /**
         * [parts] must already be in book order (prelims, chapters, appendices, answers). Neighbouring
         * non-chapter parts of the same kind become one section. [printedOffset] is
         * `printed page - merged page` when it could be read from the pages; otherwise the printed
         * numbering is assumed to start right after the prelims (true for NCERT's one-file-per-chapter downloads).
         */
        fun build(parts: List<PartInfo>, printedOffset: Int? = null): BookLayout {
            val sections = ArrayList<LayoutSection>()
            var next = 1
            for (p in parts) {
                val last = sections.lastOrNull()
                if (p.kind != SectionKind.CHAPTER && last != null && last.kind == p.kind) {
                    sections[sections.lastIndex] = last.copy(pages = last.pages + p.pages)
                } else {
                    sections += LayoutSection(p.kind, p.number, titleOf(p), next, p.pages)
                }
                next += p.pages
            }
            val prelims = sections.filter { it.kind == SectionKind.PRELIMS }.sumOf { it.pages }
            return BookLayout(sections, printedOffset ?: -prelims)
        }

        private fun titleOf(p: PartInfo): String = when (p.kind) {
            SectionKind.PRELIMS -> "Prelims"
            SectionKind.APPENDIX -> "Appendices"
            SectionKind.ANSWERS -> "Answers"
            SectionKind.CHAPTER -> if (p.title.isNullOrBlank()) "Chapter ${p.number}" else "Ch ${p.number}: ${p.title}"
        }

        fun roman(n: Int): String {
            if (n <= 0) return n.toString()
            val table = listOf(1000 to "m", 900 to "cm", 500 to "d", 400 to "cd", 100 to "c", 90 to "xc", 50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i")
            var rest = n
            val sb = StringBuilder()
            for ((value, symbol) in table) while (rest >= value) { sb.append(symbol); rest -= value }
            return sb.toString()
        }
    }
}

/** One recognised PDF inside a chapter-wise zip. */
data class ZipPart(val prefix: String, val kind: SectionKind, val number: Int, val entryName: String)

/** Recognises NCERT's file names (`keph1ps.pdf`, `keph103.pdf`, `keph1a1.pdf`, `keph1an.pdf`) and orders them as the book. */
object BookZipPlan {
    private val FILE = Regex("^([a-z]{2,8}\\d)(ps|an|a(\\d{1,2})|(\\d{2}))\\.pdf$", RegexOption.IGNORE_CASE)

    fun classify(entryName: String): ZipPart? {
        if (entryName.contains("__MACOSX")) return null
        val base = entryName.substringAfterLast('/').substringAfterLast('\\')
        if (base.startsWith(".")) return null
        val m = FILE.matchEntire(base) ?: return null
        val prefix = m.groupValues[1].lowercase()
        val tail = m.groupValues[2].lowercase()
        return when {
            tail == "ps" -> ZipPart(prefix, SectionKind.PRELIMS, 0, entryName)
            tail == "an" -> ZipPart(prefix, SectionKind.ANSWERS, 0, entryName)
            m.groupValues[3].isNotEmpty() -> ZipPart(prefix, SectionKind.APPENDIX, m.groupValues[3].toInt(), entryName)
            else -> ZipPart(prefix, SectionKind.CHAPTER, m.groupValues[4].toInt(), entryName)
        }
    }

    /**
     * Keeps the parts of the book that has most files in the zip (a zip should hold one book), drops
     * duplicates and returns them in reading order. Empty when there is no chapter at all.
     */
    fun plan(parts: List<ZipPart>): List<ZipPart> {
        val prefix = parts.groupBy { it.prefix }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<ZipPart>>> { it.value.size }.thenBy { it.key })
            .firstOrNull()?.key ?: return emptyList()
        val own = parts.filter { it.prefix == prefix }.distinctBy { it.kind to it.number }
        if (own.none { it.kind == SectionKind.CHAPTER }) return emptyList()
        return own.sortedWith(compareBy({ it.kind.ordinal }, { it.number }))
    }
}

/** Reads the chapter name from the first page of a chapter file ("CHAPTER ONE" then "UNITS AND MEASUREMENT"). */
object ChapterTitles {
    private val HEAD = Regex("^chapter\\s+([a-z]+|\\d+)$", RegexOption.IGNORE_CASE)
    private val CAPS = Regex("^[A-Z][A-Z0-9 ,'\u2019&:;()\\-\u2013/.]*$")
    private val SMALL = setOf("and", "of", "in", "on", "the", "a", "an", "to", "for", "by", "at", "or")

    /** Some chapter headings use a symbol font whose letters come out as U+F0xx; shift them back to ASCII. */
    fun decodeSymbolFont(s: String): String = buildString(s.length) {
        for (c in s) append(if (c.code in 0xF020..0xF0FF) (c.code - 0xF000).toChar() else c)
    }

    fun fromFirstPage(text: String): String? {
        val lines = decodeSymbolFont(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val head = lines.indexOfFirst { HEAD.matches(it) }
        if (head < 0) return null
        val parts = ArrayList<String>()
        for (l in lines.drop(head + 1)) {
            if (parts.size >= 3 || l.length < 3 || l.first().isDigit() || !CAPS.matches(l)) break
            parts += l
        }
        if (parts.isEmpty()) return null
        return titleCase(parts.joinToString(" "))
    }

    fun titleCase(s: String): String = s.lowercase().split(' ').filter { it.isNotEmpty() }
        .mapIndexed { i, w -> if (i > 0 && w in SMALL) w else w.replaceFirstChar { it.uppercase() } }
        .joinToString(" ")
}

/**
 * Finds `printed page - merged page` from the page numbers NCERT prints in the header or footer
 * ("2 PHYSICS" at the top of even pages, "UNITS AND MEASUREMENT 3" at the bottom of odd ones).
 * Needs two different pages that agree, otherwise null.
 */
object PrintedPages {
    private val FIRST = Regex("^(\\d{1,4})(?:\\s+\\p{L}[\\p{L} .,&-]*)?$")
    private val LAST = Regex("^(?:\\p{L}[\\p{L} .,&-]*\\s+)?(\\d{1,4})$")

    /** [pages]: merged page number -> that page's non-blank lines, top to bottom. */
    fun offset(pages: Map<Int, List<String>>): Int? {
        val votes = HashMap<Int, MutableSet<Int>>()
        for ((page, lines) in pages) {
            val first = lines.firstOrNull()?.trim()?.let { FIRST.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            val last = lines.lastOrNull()?.trim()?.let { LAST.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            for (n in listOfNotNull(first, last)) if (n >= 1) votes.getOrPut(n - page) { HashSet() }.add(page)
        }
        val best = votes.entries.maxByOrNull { it.value.size } ?: return null
        return if (best.value.size >= 2) best.key else null
    }
}
