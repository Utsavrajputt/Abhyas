package com.invictus.abhyas.domain.pdf

/**
 * Extracts an answer key: label -> raw answer text ("2", "B", "A,C", "24", "3.5", or free text for
 * NCERT-style "Answers to some questions").
 */
object AnswerKeyParser {

    private val HEADING = Regex(
        "^(?:answer\\s*keys?|answers?|answer\\s*sheet|ans\\.?\\s*key|key|answers?\\s+to\\s+.{3,50}|key\\s+to\\s+.{3,40})\\s*[:\\-\\u2013]*\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val SOLUTIONS_HEADING = Regex(
        "^(?:hints?\\s*(?:&|and)\\s*)?(?:detailed\\s*)?(?:solutions?|explanations?)(?:\\s*(?:&|and)\\s*answers?)?\\s*[:\\-\\u2013]*\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private val ENTRY = Regex(
        "(?<![\\w.])(\\d{1,3}(?:\\.\\d{1,2})?)\\s*[.):=\\-\\u2013]?\\s*" +
            "(\\(?[A-Ea-e]\\)?(?:\\s*(?:,|&|and)?\\s*\\(?[A-Ea-e]\\)?)*(?![\\w])|\\(?[1-5]\\)?(?![\\w.])|-?\\d+(?:\\.\\d+)?(?![\\w]))",
    )
    private val FREE_LINE = Regex("^(\\d{1,3}(?:\\.\\d{1,2})?)\\s*[.):\\-\\u2013]\\s*(.{4,})$")
    private val TOKEN_LIKE = Regex("^\\(?[A-Ea-e1-5]\\)?(?:\\s*(?:,|&|and)?\\s*\\(?[A-Ea-e]\\)?)*$|^-?\\d+(?:\\.\\d+)?$")

    fun isHeading(line: String): Boolean = line.length <= 60 && HEADING.matches(line.trim())
    fun isSolutionsHeading(line: String): Boolean = line.length <= 60 && SOLUTIONS_HEADING.matches(line.trim())

    fun parse(lines: List<String>): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val free = FREE_LINE.matchEntire(t)
            if (free != null && !TOKEN_LIKE.matches(free.groupValues[2].trim()) && ENTRY.findAll(t).count() <= 1) {
                found.putIfAbsent(free.groupValues[1], free.groupValues[2].trim())
                continue
            }
            for (m in ENTRY.findAll(t)) {
                val label = m.groupValues[1]
                val value = m.groupValues[2].trim()
                found.putIfAbsent(label, value)
            }
        }
        return keepSequential(found)
    }

    /** Keeps entries whose labels form a mostly increasing run, dropping stray numbers. */
    private fun keepSequential(raw: Map<String, String>): Map<String, String> {
        if (raw.size < 3) return raw
        val out = LinkedHashMap<String, String>()
        var last = -1.0
        for ((k, v) in raw) {
            val n = k.toDoubleOrNull() ?: continue
            if (n > last && n - last <= 12 || last < 0) {
                out[k] = v
                last = n
            }
        }
        return out
    }
}
