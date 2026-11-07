package com.invictus.abhyas.domain.pdf

/** Decides whether the digital text of a page is usable or the page needs OCR (scanned / broken fonts). */
object PageQuality {
    fun needsOcr(text: String, minChars: Int = 40): Boolean {
        val t = text.filter { !it.isWhitespace() }
        if (t.length < minChars) return true
        val good = t.count { it.isLetterOrDigit() || it in ".,;:()[]{}+-=/%?'\"\u00B0*<>|!_" }
        return good.toDouble() / t.length < 0.6
    }
}

/** Finds the vertical band (PDF points from the page top) a question occupies, to crop its figure. */
object FigureRegion {
    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun startY(lines: List<LinePos>, statement: String): Float? {
        val k = key(statement).take(14)
        if (k.length < 8) return null
        return lines.firstOrNull { key(it.t).contains(k) }?.y
    }

    /**
     * [statement] / [nextStatement]: starts of this and the following question on the same page
     * (null = band runs to the page bottom). Returns (top, bottom) in points, or null if unknown.
     */
    fun band(lines: List<LinePos>, pageHeight: Float, statement: String, nextStatement: String?): Pair<Float, Float>? {
        if (lines.isEmpty() || pageHeight <= 0f) return null
        val top = startY(lines, statement) ?: return null
        val bottom = nextStatement?.let { startY(lines, it) }?.takeIf { it > top + 20f } ?: pageHeight
        return maxOf(0f, top - 6f) to minOf(pageHeight, bottom)
    }
}
