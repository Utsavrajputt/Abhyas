package com.invictus.abhyas.domain.pdf

import kotlin.math.floor

/** Turns a raw key/inline answer string into typed answers. Never guesses: returns null if unclear. */
object AnswerInterpreter {

    /** Single option id (a..e) from "(2)", "2", "B", "(b)". Null if absent, ambiguous or out of range. */
    fun singleId(raw: String?, optionCount: Int): String? {
        val ids = optionIds(raw, optionCount)
        return if (ids.size == 1) ids.first() else null
    }

    fun optionIds(raw: String?, optionCount: Int): List<String> {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return emptyList()
        val tokens = Regex("\\(?([A-Ea-e1-5])\\)?").findAll(s).map { it.groupValues[1] }.toList()
        // Compact forms like "ACD" / "13" are accepted only when the whole string is option letters/digits.
        val cleaned = s.replace(Regex("[\\s,&()]|and", RegexOption.IGNORE_CASE), "")
        val compact = cleaned.isNotEmpty() && cleaned.all { it in "ABCDEabcde12345" }
        if (!compact) return emptyList()
        val ords = tokens.mapNotNull { OptionParser.ordinal(it) }.distinct().sorted()
        if (ords.isEmpty() || ords.any { it > optionCount }) return emptyList()
        return ords.map { OptionParser.idOf(it) }
    }

    fun number(raw: String?): Double? {
        val s = raw?.trim()?.replace(",", "") ?: return null
        return Regex("^-?\\d+(?:\\.\\d+)?$").find(s)?.value?.toDoubleOrNull()
    }

    fun bool(raw: String?): Boolean? = when (raw?.trim()?.trim('.', '(', ')')?.lowercase()) {
        "true", "t", "yes", "correct" -> true
        "false", "f", "no", "incorrect" -> false
        else -> null
    }

    fun isInteger(d: Double) = d == floor(d)

    /** Decimal tolerance from phrases like "correct up to 2 decimal places" (0.005). */
    fun toleranceFor(statement: String): Double {
        val m = Regex("(\\d)\\s*decimal\\s*place", RegexOption.IGNORE_CASE).find(statement) ?: return 0.0
        val n = m.groupValues[1].toInt()
        var t = 0.5
        repeat(n) { t /= 10 }
        return t
    }

    /** Maps an assertion-reason option's wording to the standard code 1..4, or null if unrecognised. */
    fun arCode(optionText: String): Int? {
        val t = optionText.lowercase().replace(Regex("[()]"), "").replace(Regex("\\s+"), " ")
        val bothTrue = t.contains("both") && (t.contains("true") || t.contains("correct"))
        val notExplanation = t.contains("not the correct explanation") || t.contains("not a correct explanation") ||
            t.contains("not correct explanation") || t.contains("not the correct reason")
        val aTrue = listOf("a is true", "a is correct", "assertion is true", "assertion is correct").any { t.contains(it) }
        val aFalse = listOf("a is false", "a is incorrect", "a is not correct", "assertion is false", "assertion is incorrect")
            .any { t.contains(it) }
        val rTrue = listOf("r is true", "r is correct", "reason is true", "reason is correct").any { t.contains(it) }
        val rFalse = listOf("r is false", "r is incorrect", "r is not correct", "reason is false", "reason is incorrect")
            .any { t.contains(it) }
        return when {
            bothTrue && notExplanation -> 2
            bothTrue && t.contains("correct explanation") -> 1
            aTrue && rFalse -> 3
            aFalse && rTrue -> 4
            else -> null
        }
    }
}
