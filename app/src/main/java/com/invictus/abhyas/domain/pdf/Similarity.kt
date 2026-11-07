package com.invictus.abhyas.domain.pdf

/** Near-duplicate detection on normalised word bigrams (Jaccard), via an inverted index. */
object Similarity {

    fun tokens(text: String): List<String> =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    /** Hashes of word bigrams (unigrams when the text has fewer than 4 words). */
    fun shingles(text: String): Set<Int> {
        val t = tokens(text)
        if (t.size < 4) return t.map { it.hashCode() }.toSet()
        val out = HashSet<Int>(t.size)
        for (i in 0 until t.size - 1) out += (t[i] + " " + t[i + 1]).hashCode()
        return out
    }

    class Index(statements: List<String>) {
        private val sets: List<Set<Int>> = statements.map(::shingles)
        private val postings = HashMap<Int, MutableList<Int>>()

        init {
            sets.forEachIndexed { i, s -> for (h in s) postings.getOrPut(h) { ArrayList(2) } += i }
        }

        /** Best match of [other] among the indexed statements: (index, jaccard) or null if below [threshold]. */
        fun best(other: String, threshold: Double = 0.85): Pair<Int, Double>? {
            val s = shingles(other)
            if (s.isEmpty()) return null
            val overlap = HashMap<Int, Int>()
            for (h in s) postings[h]?.forEach { overlap[it] = (overlap[it] ?: 0) + 1 }
            var bestIdx = -1
            var bestScore = 0.0
            for ((i, o) in overlap) {
                val union = s.size + sets[i].size - o
                val j = if (union == 0) 0.0 else o.toDouble() / union
                if (j > bestScore) { bestScore = j; bestIdx = i }
            }
            return if (bestIdx >= 0 && bestScore >= threshold) bestIdx to bestScore else null
        }
    }
}
