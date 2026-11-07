package com.invictus.abhyas.domain.pdf

/** One cleaned line of the document and the page it came from. */
data class Ln(val page: Int, val text: String)

/** A question as found in the source: its original label and its raw lines (label stripped). */
data class QBlock(val label: String, val page: Int, val lines: List<String>, val gapBefore: Boolean)

/**
 * Question boundary detection. Finds numbering candidates (Q1. / 1. / 1) / Question 1 / NCERT 5.3),
 * then keeps only the longest consecutive 1,2,3... chain so numbers inside option lists, solution
 * steps or sentences ("2. 5 m/s") are not mistaken for questions.
 */
object QuestionSplitter {

    private enum class Style { Q, DOT, PAREN, NCERT }

    private class Cand(val idx: Int, val style: Style, val major: Int, val minor: Int, val rest: String)

    private val Q_PREFIX = Regex("^(?:q(?:uestion)?)\\s*\\.?\\s*(\\d{1,3})\\s*[.):\\-\\u2013]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val NCERT = Regex("^(\\d{1,2})\\.(\\d{1,2})\\s+(\\S.*)$")
    private val DOT = Regex("^(\\d{1,3})\\s*\\.\\s*(\\S.*)?$")
    private val PAREN = Regex("^(\\d{1,3})\\s*\\)\\s*(\\S.*)?$")
    private val SOL_START = Regex("^(?:sol(?:ution)?|explanation|hint|ans(?:wer)?)\\b.*", RegexOption.IGNORE_CASE)
    private val OPTION_ANY = Regex("(?:^|\\s)\\(?[1-4a-dA-D][.)]\\s|\\([1-4a-dA-D]\\)")

    fun split(lines: List<Ln>, ncert: Boolean = false): List<QBlock> {
        val cands = candidates(lines, ncert)
        val chain = bestChain(cands)
        if (chain.isEmpty()) return emptyList()
        val blocks = ArrayList<QBlock>(chain.size)
        for ((k, c) in chain.withIndex()) {
            val end = if (k + 1 < chain.size) chain[k + 1].idx else lines.size
            val body = ArrayList<String>()
            if (c.rest.isNotBlank()) body += c.rest
            for (i in c.idx + 1 until end) body += lines[i].text
            val label = if (c.minor >= 0) "${c.major}.${c.minor}" else c.major.toString()
            val prev = chain.getOrNull(k - 1)
            val gap = prev != null && !isNext(prev, c)
            blocks += QBlock(label, lines[c.idx].page, body, gap)
        }
        return blocks
    }

    private fun candidates(lines: List<Ln>, ncert: Boolean): List<Cand> {
        val out = ArrayList<Cand>()
        var inSolution = false
        for ((i, ln) in lines.withIndex()) {
            val t = ln.text
            if (SOL_START.matches(t)) inSolution = true
            val c = candidate(i, t, ncert) ?: continue
            if (inSolution) {
                // Inside a solution only things that clearly look like a new question count.
                val look = lines.subList(i, minOf(lines.size, i + 8)).any { OPTION_ANY.containsMatchIn(it.text) }
                val ok = c.style == Style.Q || c.style == Style.NCERT || look || c.rest.contains('?') || c.rest.length > 70
                if (!ok) continue
                inSolution = false
            }
            out += c
        }
        return removeOptionRuns(out)
    }

    private fun candidate(i: Int, t: String, ncert: Boolean): Cand? {
        Q_PREFIX.matchEntire(t)?.let { return Cand(i, Style.Q, it.groupValues[1].toInt(), -1, it.groupValues[2]) }
        NCERT.matchEntire(t)?.let {
            return Cand(i, Style.NCERT, it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3])
        }
        DOT.matchEntire(t)?.let { return Cand(i, Style.DOT, it.groupValues[1].toInt(), -1, it.groupValues[2]) }
        PAREN.matchEntire(t)?.let { return Cand(i, Style.PAREN, it.groupValues[1].toInt(), -1, it.groupValues[2]) }
        return null
    }

    /** Drops "1 2 3 4" runs of tightly packed numbered lines that are option lists, not questions. */
    private fun removeOptionRuns(c: List<Cand>): List<Cand> {
        val drop = BooleanArray(c.size)
        var i = 1
        while (i < c.size) {
            val s = c[i]
            if (s.major == 1 && s.minor < 0 && s.style != Style.Q) {
                var j = i
                var n = 1
                while (j + 1 < c.size && c[j + 1].style == s.style && c[j + 1].major == n + 1 &&
                    c[j + 1].idx - c[j].idx <= 4 && n < 5
                ) { j++; n++ }
                if (n >= 3) {
                    for (k in i..j) drop[k] = true
                    i = j + 1
                    continue
                }
            }
            i++
        }
        return c.filterIndexed { k, _ -> !drop[k] }
    }

    private fun isNext(a: Cand, b: Cand): Boolean = when {
        a.minor >= 0 && b.minor >= 0 ->
            (b.major == a.major && b.minor == a.minor + 1) || (b.major == a.major + 1 && b.minor == 1)
        a.minor < 0 && b.minor < 0 -> b.major == a.major + 1
        else -> false
    }

    private fun isGap(a: Cand, b: Cand): Boolean = when {
        a.minor >= 0 && b.minor >= 0 -> b.major == a.major && b.minor in (a.minor + 2)..(a.minor + 4)
        a.minor < 0 && b.minor < 0 -> b.major in (a.major + 2)..(a.major + 4)
        else -> false
    }

    private fun isStart(c: Cand) = if (c.minor >= 0) c.minor == 1 else c.major == 1

    private fun confirmed(c: List<Cand>, i: Int): Boolean {
        for (j in i + 1 until minOf(c.size, i + 7)) if (isNext(c[i], c[j])) return true
        return false
    }

    private fun greedy(c: List<Cand>, from: Int): List<Cand> {
        val out = ArrayList<Cand>()
        for (i in from until c.size) {
            val cur = c[i]
            if (out.isEmpty()) {
                if (i == from) out += cur
                continue
            }
            val last = out.last()
            when {
                isNext(last, cur) -> out += cur
                isGap(last, cur) && confirmed(c, i) -> out += cur
                isStart(cur) && out.size >= 3 && cur.style == last.style && confirmed(c, i) -> out += cur // new section restarts at 1
            }
        }
        return out
    }

    private fun bestChain(c: List<Cand>): List<Cand> {
        if (c.isEmpty()) return emptyList()
        // Prefer the dominant style so stray lines of another style cannot hijack a chain.
        var best: List<Cand> = emptyList()
        val starts = c.indices.filter { isStart(c[it]) || confirmed(c, it) }.take(12)
        for (s in starts) {
            val sameStyle = c.filter { it.style == c[s].style || it.style == Style.Q && c[s].style == Style.Q }
            val from = sameStyle.indexOf(c[s])
            if (from < 0) continue
            val g = greedy(sameStyle, from)
            if (g.size > best.size) best = g
        }
        return if (best.size >= 2) best else if (c.size == 1) c else best
    }
}
