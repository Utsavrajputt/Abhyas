package com.invictus.abhyas.domain.ncert

/**
 * Short display names for books: "NCERT Physics Class 11 Part 1" -> "Physics 1".
 * The class is shown as the shelf heading, so it is dropped from the name. Pure JVM, unit-testable.
 */
object BookNaming {
    private val CLASS = Regex("""\(?\s*\bclass\s+(?:xii|xi|\d{1,2})\b\s*\)?""", RegexOption.IGNORE_CASE)
    private val NCERT = Regex("""\bncert\b""", RegexOption.IGNORE_CASE)
    private val PART = Regex("""\bpart\s*[-:]?\s*(iv|iii|ii|i|\d+)\b""", RegexOption.IGNORE_CASE)
    private val ROMAN = mapOf("i" to "1", "ii" to "2", "iii" to "3", "iv" to "4")

    fun shortTitle(title: String, subject: String): String {
        var t = title
        t = CLASS.replace(t, " ")
        t = NCERT.replace(t, " ")
        t = PART.replace(t) { m ->
            val v = m.groupValues[1].lowercase()
            " " + (ROMAN[v] ?: v) + " "
        }
        t = t.replace(Regex("""[()\[\]]"""), " ").replace(Regex("""\s*[-:,]\s*$"""), "").replace(Regex("""\s+"""), " ").trim()
        return t.ifBlank { subject.ifBlank { title } }
    }

    /** Heading for a shelf: "Class 11", or "Other" when the class is unknown. */
    fun classLabel(classLevel: Int?): String = classLevel?.let { "Class $it" } ?: "Other"

    /** Part number (1, 2, ...) found in a title, or null. */
    fun part(title: String): Int? {
        val v = PART.find(title)?.groupValues?.get(1)?.lowercase() ?: return null
        return (ROMAN[v] ?: v).toIntOrNull()
    }
}
