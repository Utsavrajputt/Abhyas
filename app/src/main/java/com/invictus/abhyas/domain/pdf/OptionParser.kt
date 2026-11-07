package com.invictus.abhyas.domain.pdf

/** Result of splitting a question block into stem and options. Option ids are always a..e. */
data class OptionSplit(val stem: String, val options: List<Pair<String, String>>, val stemRaw: String = stem)

object OptionParser {

    private class Style(val name: String, val regex: Regex)

    private val STYLES = listOf(
        Style("(1)", Regex("(?<![\\w(])\\(([1-5])\\)")),
        Style("(a)", Regex("(?<![\\w(])\\(([a-eA-E])\\)")),
        Style("1)", Regex("(?<![\\w(.])([1-5])\\)")),
        Style("A.", Regex("(?<![\\w(])([A-E])[.)](?=\\s)")),
        Style("a)", Regex("(?<![\\w(])([a-e])\\)")),
        Style("1.", Regex("(?<![\\w(.])([1-5])\\.(?=\\s)")),
    )

    private class Hit(val ord: Int, val start: Int, val end: Int)

    fun ordinal(token: String): Int? {
        val c = token.trim().trim('(', ')', '.').firstOrNull() ?: return null
        return when (c) {
            in '1'..'5' -> c - '0'
            in 'a'..'e' -> c - 'a' + 1
            in 'A'..'E' -> c - 'A' + 1
            else -> null
        }
    }

    fun idOf(ordinal: Int): String = ('a' + (ordinal - 1)).toString()

    fun parse(lines: List<String>): OptionSplit {
        val text = lines.joinToString("\n")
        var bestSeq: List<Hit>? = null
        var bestEnd = -1
        for (style in STYLES) {
            val hits = style.regex.findAll(text).mapNotNull { m ->
                ordinal(m.groupValues[1])?.let { Hit(it, m.range.first, m.range.last + 1) }
            }.toList()
            for (s in hits.filter { it.ord == 1 }) {
                val seq = arrayListOf(s)
                var pos = s.end
                var want = 2
                while (want <= 5) {
                    val nxt = hits.firstOrNull { it.ord == want && it.start >= pos } ?: break
                    seq += nxt; pos = nxt.end; want++
                }
                if (seq.size >= 2) {
                    val end = seq.last().end
                    val better = bestSeq == null || seq.size > bestSeq!!.size && seq.size >= 4 ||
                        (seq.size >= 3 && end > bestEnd && bestSeq!!.size < 4) ||
                        (seq.size == bestSeq!!.size && end > bestEnd)
                    if (better) { bestSeq = seq; bestEnd = end }
                }
            }
        }
        val seq = bestSeq ?: return OptionSplit(text.collapse(), emptyList(), text.trim())
        val stemRawText = text.substring(0, seq.first().start).trim()
        val stem = stemRawText.collapse()
        val options = seq.mapIndexed { k, h ->
            val to = if (k + 1 < seq.size) seq[k + 1].start else text.length
            idOf(h.ord) to text.substring(h.end, to).collapse()
        }.filter { it.second.isNotBlank() }
        return if (options.size >= 2) OptionSplit(stem, options, stemRawText) else OptionSplit(text.collapse(), emptyList(), text.trim())
    }

    private fun String.collapse() = replace(Regex("\\s*\\n\\s*"), " ").replace(Regex("[ \\t]+"), " ").trim()
}
