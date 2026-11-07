package com.invictus.abhyas.domain.pdf

/** Cleans raw extracted page text: ligatures, hyphenation, repeated headers/footers, page numbers. */
object TextCleaner {

    private val LIGATURES = mapOf(
        "\uFB01" to "fi", "\uFB02" to "fl", "\uFB00" to "ff", "\uFB03" to "ffi", "\uFB04" to "ffl",
        "\u00A0" to " ", "\u200B" to "", "\u00AD" to "", "\uFFFD" to "", "\u2212" to "-",
        "\u2019" to "'", "\u2018" to "'", "\u201C" to "\"", "\u201D" to "\"",
    )

    private val PAGE_NUMBER = Regex("^\\s*(?:page\\s*)?\\d{1,4}\\s*(?:/\\s*\\d{1,4})?\\s*$", RegexOption.IGNORE_CASE)

    fun normalizeLine(raw: String): String {
        var s = raw
        for ((k, v) in LIGATURES) if (s.contains(k)) s = s.replace(k, v)
        return s.replace(Regex("[ \\t]+"), " ").trim()
    }

    /**
     * Cleans all pages together so running headers/footers (a line repeated on >= 40% of pages,
     * at least 3 pages) and lone page numbers can be dropped. Returns page -> cleaned lines.
     */
    fun clean(pages: List<PageText>): List<Pair<Int, List<String>>> {
        val perPage = pages.map { p ->
            p.page to p.text.lineSequence().map(::normalizeLine).filter { it.isNotEmpty() }.toList()
        }
        val repeated = repeatedEdgeLines(perPage.map { it.second })
        return perPage.map { (page, lines) ->
            val kept = lines.filterIndexed { i, l ->
                val edge = i < 3 || i >= lines.size - 3
                !(edge && (PAGE_NUMBER.matches(l) || ContentKey.of(l) in repeated))
            }
            page to dehyphenate(kept)
        }
    }

    private fun repeatedEdgeLines(pages: List<List<String>>): Set<String> {
        if (pages.size < 3) return emptySet()
        val counts = HashMap<String, Int>()
        for (lines in pages) {
            val edge = (lines.take(3) + lines.takeLast(3)).map { ContentKey.of(it) }.filter { it.length >= 4 }.toSet()
            for (k in edge) counts[k] = (counts[k] ?: 0) + 1
        }
        val need = maxOf(3, (pages.size * 0.4).toInt())
        return counts.filterValues { it >= need }.keys
    }

    /** Joins "inter-\nference" style breaks when the next line starts in lower case. */
    private fun dehyphenate(lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null && l.length > 2 && l.endsWith("-") && l[l.length - 2].isLetter() &&
                next.isNotEmpty() && next[0].isLowerCase()
            ) {
                val firstWordEnd = next.indexOf(' ').let { if (it < 0) next.length else it }
                out += l.dropLast(1) + next.substring(0, firstWordEnd)
                val rest = next.substring(firstWordEnd).trim()
                if (rest.isNotEmpty()) out += rest
                i += 2
            } else {
                out += l
                i++
            }
        }
        return out
    }
}

/** Digit-insensitive key so "Page 3 of 40" and "Page 4 of 40" count as the same header. */
object ContentKey {
    fun of(line: String): String = line.lowercase().replace(Regex("\\d+"), "#").replace(Regex("\\s+"), " ").trim()
}
