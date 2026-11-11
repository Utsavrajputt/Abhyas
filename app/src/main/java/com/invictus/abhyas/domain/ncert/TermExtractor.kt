package com.invictus.abhyas.domain.ncert

/** One passage of a book, with enough identity to build an [NcertRef]. */
data class SourcePassage(
    val bookId: String,
    val page: Int,
    val idx: Int,
    val text: String,
    val hash: String,
    val chapter: String? = null,
    /** Page number printed in the textbook (the PDF page can differ: prelims, merged chapter files). */
    val pageLabel: String = page.toString(),
) {
    fun ref() = NcertRef(bookId, page, idx, hash, chapter)
}

/** A defined term together with the textbook sentence that defines it. */
data class KeyTerm(val term: String, val sentence: String, val source: SourcePassage)

/** Splits passage text into sentences without breaking on "Fig.", "e.g." and similar. Pure. */
object SentenceSplitter {
    private val ABBREV = Regex("\\b(Fig|Figs|e\\.g|i\\.e|etc|vs|Eq|Eqs|approx|Dr|Mr|No|Sec|Vol)\\.", RegexOption.IGNORE_CASE)
    private val BOUNDARY = Regex("(?<=[.!?])\\s+(?=[A-Z])")
    private const val MARK = '\u0001'

    fun split(text: String): List<String> {
        val protectedText = ABBREV.replace(text.replace(Regex("\\s+"), " ").trim()) { it.value.dropLast(1) + MARK }
        return protectedText.split(BOUNDARY).map { it.replace(MARK, '.').trim() }.filter { it.isNotEmpty() }
    }
}

/** Finds "X is called Y" / "known as Y" / "X is defined as ..." sentences. Pure, no AI. */
object TermExtractor {
    private val TRIGGER = Regex("\\b(?:called|known as|termed|referred to as|named)\\s+", RegexOption.IGNORE_CASE)
    private val DEFINED = Regex("^(?:the\\s+)?(.{3,50}?)\\s+(?:is|are)\\s+defined\\s+as\\b", RegexOption.IGNORE_CASE)
    private val WORDS = Regex("^[\"\u201C\u2018']?([A-Za-z][A-Za-z\\-'\u2019]*(?: [A-Za-z][A-Za-z\\-'\u2019]*){0,3})")
    private val LEADING_ARTICLE = Regex("^(?:the|a|an)\\s+", RegexOption.IGNORE_CASE)

    private val STOP = setOf(
        "and", "or", "which", "when", "if", "that", "where", "is", "are", "was", "were", "because", "as", "the", "in",
        "on", "at", "by", "for", "to", "with", "from", "has", "have", "can", "may", "will", "then", "but", "so", "it",
        "its", "this", "these", "those", "also", "thus", "while", "whose", "who", "whom", "such", "being", "be",
    )
    private val BAD_FIRST = setOf("it", "this", "these", "those", "that", "they", "he", "she", "we", "there", "which", "what")

    fun extract(passages: List<SourcePassage>): List<KeyTerm> {
        val out = ArrayList<KeyTerm>()
        val seen = HashSet<String>()
        for (p in passages) {
            for (s in SentenceSplitter.split(p.text)) {
                if (s.length !in 45..260) continue
                val term = termIn(s) ?: continue
                if (seen.add(term.lowercase())) out += KeyTerm(term, s, p)
            }
        }
        return out
    }

    /** The defined term of [sentence], or null when it has none or the term looks unreliable. */
    fun termIn(sentence: String): String? {
        val m = TRIGGER.find(sentence)
        val raw = if (m != null) wordsAfter(sentence.substring(m.range.last + 1)) else DEFINED.find(sentence)?.groupValues?.get(1)
        return clean(raw)
    }

    private fun wordsAfter(rest: String): String? {
        val afterArticle = rest.replace(LEADING_ARTICLE, "")
        val seq = WORDS.find(afterArticle)?.groupValues?.get(1) ?: return null
        val words = seq.split(' ').takeWhile { it.lowercase() !in STOP }.toMutableList()
        // A term cut off by the 4-word limit can end on "of"; drop it so "centre of mass of" becomes "centre of mass".
        while (words.isNotEmpty() && (words.last().lowercase() in STOP || words.last().equals("of", ignoreCase = true))) words.removeAt(words.lastIndex)
        return words.joinToString(" ").ifBlank { null }
    }

    private fun clean(raw: String?): String? {
        val t = raw?.trim()?.trim('"', '\u201C', '\u201D', '\u2018', '\u2019', '\'', ',', ';', ':')?.replace(LEADING_ARTICLE, "")?.trim() ?: return null
        val words = t.split(' ')
        if (t.length !in 3..40 || words.size > 4) return null
        if (words.first().lowercase() in BAD_FIRST) return null
        if (words.all { it.lowercase() in STOP }) return null
        if (t.any { it.isDigit() }) return null
        return t
    }
}
