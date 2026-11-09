package com.invictus.abhyas.domain.ncert

import kotlin.math.ln

/** One paragraph of a book as the matcher sees it. */
data class MatchPassage(val bookId: String, val page: Int, val idx: Int, val text: String)

enum class MatchConfidence { HIGH, LIKELY }

data class PassageMatch(val passage: MatchPassage, val score: Double, val matched: Int, val confidence: MatchConfidence)

/** Pre-computed word statistics of one book (build once, reuse for every question). */
class PassageIndex(val passages: List<MatchPassage>) {
    internal val tf: List<Map<String, Int>>
    internal val lengths: IntArray
    internal val bigrams: List<Set<String>>
    internal val avgLength: Double
    private val df = HashMap<String, Int>()

    init {
        val seqs = passages.map { PassageMatcher.tokens(it.text) }
        tf = seqs.map { seq -> seq.groupingBy { it }.eachCount() }
        lengths = IntArray(seqs.size) { seqs[it].size }
        bigrams = seqs.map { seq -> seq.zipWithNext { a, b -> "$a $b" }.toSet() }
        avgLength = if (seqs.isEmpty()) 0.0 else lengths.average()
        for (m in tf) for (t in m.keys) df[t] = (df[t] ?: 0) + 1
    }

    internal fun idf(term: String): Double = ln(1.0 + passages.size.toDouble() / (1 + (df[term] ?: 0)))
}

/**
 * Finds where a question's text is explained in a book: BM25 over paragraphs plus a small bonus for
 * shared word pairs. Deliberately cautious, because a wrong page is worse than no page: numerical
 * questions and topics the book does not cover usually come back null. Pure and unit-tested.
 */
object PassageMatcher {
    private const val K1 = 1.2
    private const val B = 0.75
    private val WORD = Regex("[a-z0-9]+")
    private val STOP = (
        "the an and or of in on at to for from by with as is are was were be been being this that these those it its into than then " +
            "there their they them which what when where who whom whose how why can could may might must shall should will would not " +
            "nor but if else such also any each other some more most many much very only own same so too both either neither one two " +
            "three four given following correct incorrect statement statements option options choose answer find value calculate " +
            "determine does did do has have had per using use used"
        ).split(' ').toSet()

    fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        for (m in WORD.findAll(text.lowercase())) {
            val w = m.value
            if (w.length < 3 || w in STOP || w.all { it.isDigit() }) continue
            out += stem(w)
        }
        return out
    }

    /** Light stemming so "forces" matches "force" and "moving" matches "move"-like stems; one suffix at most. */
    private fun stem(w: String): String = when {
        w.endsWith("ing") && w.length > 6 -> w.dropLast(3)
        w.endsWith("ed") && w.length > 5 -> w.dropLast(2)
        w.endsWith("ly") && w.length > 5 -> w.dropLast(2)
        w.endsWith("sses") -> w.dropLast(2)
        w.endsWith("s") && w.length > 4 && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
        else -> w
    }

    /** Best paragraph for [query], or null when nothing is convincing. */
    fun best(query: String, index: PassageIndex): PassageMatch? {
        if (index.passages.isEmpty() || index.avgLength <= 0.0) return null
        val qs = tokens(query)
        val q = qs.toSet()
        if (q.size < 3) return null
        val minMatched = if (q.size <= 4) 3 else 4
        val qb = qs.zipWithNext { a, b -> "$a $b" }.toSet()
        val ideal = q.sumOf { index.idf(it) * (K1 + 1) }
        var bestScore = -1.0
        var bestAt = -1
        var bestMatched = 0
        for (i in index.passages.indices) {
            val tf = index.tf[i]
            var matched = 0
            var raw = 0.0
            for (t in q) {
                val f = tf[t] ?: continue
                matched++
                raw += index.idf(t) * f * (K1 + 1) / (f + K1 * (1 - B + B * index.lengths[i] / index.avgLength))
            }
            if (matched < minMatched) continue
            val pairs = if (qb.isEmpty()) 0.0 else qb.count { it in index.bigrams[i] }.toDouble() / qb.size
            val score = 0.85 * raw / ideal + 0.15 * pairs
            if (score > bestScore) { bestScore = score; bestAt = i; bestMatched = matched }
        }
        if (bestAt < 0) return null
        val confidence = when {
            (bestMatched >= 5 && bestScore >= 0.40) || bestScore >= 0.60 -> MatchConfidence.HIGH
            bestMatched >= 4 && bestScore >= 0.28 -> MatchConfidence.LIKELY
            else -> return null
        }
        return PassageMatch(index.passages[bestAt], bestScore, bestMatched, confidence)
    }
}
