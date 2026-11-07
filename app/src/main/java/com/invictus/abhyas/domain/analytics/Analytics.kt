package com.invictus.abhyas.domain.analytics

import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.Verdict
import com.invictus.abhyas.domain.model.parseEnum

/** One scored question of a submitted attempt (denormalised columns of attempt_questions). */
data class StatRow(
    val subject: String,
    val chapter: String?,
    val topic: String?,
    val difficulty: String?,
    val type: String,
    val verdict: String?,
    val marksAwarded: Double,
    val timeSpentMs: Long,
)

data class Breakdown(
    val label: String,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val unattempted: Int,
    val unscored: Int,
    val score: Double,
    val totalTimeMs: Long,
) {
    val attempted: Int get() = correct + wrong
    val accuracy: Double get() = if (attempted == 0) 0.0 else correct.toDouble() / attempted
    val avgTimeMs: Long get() = if (total == 0) 0 else totalTimeMs / total
}

enum class Dimension(val label: String) {
    SUBJECT("Subject"),
    CHAPTER("Chapter"),
    TOPIC("Topic"),
    DIFFICULTY("Difficulty"),
    TYPE("Type"),
    SUBJECT_KIND("Theory / numerical"),
}

object Analytics {
    private const val UNSPECIFIED = "Unspecified"

    fun overall(rows: List<StatRow>): Breakdown = fold("All", rows.filter { it.verdict != null })

    fun breakdown(rows: List<StatRow>, dim: Dimension): List<Breakdown> {
        val scored = rows.filter { it.verdict != null }
        val groups = scored.groupBy { labelOf(it, dim) }
        val list = groups.map { (label, g) -> fold(label, g) }
        return if (dim == Dimension.DIFFICULTY) {
            list.sortedBy { b -> parseEnum<Difficulty>(b.label)?.ordinal ?: Int.MAX_VALUE }
        } else {
            list.sortedBy { it.label.lowercase() }
        }
    }

    /** Topics (falling back to chapter/subject) with enough attempts and low accuracy, weakest first. */
    fun weakTopics(
        rows: List<StatRow>,
        minAttempted: Int = 3,
        maxAccuracy: Double = 0.6,
        limit: Int = 10,
    ): List<Breakdown> = breakdown(rows, Dimension.TOPIC)
        .filter { it.attempted >= minAttempted && it.accuracy < maxAccuracy }
        .sortedWith(compareBy<Breakdown> { it.accuracy }.thenByDescending { it.attempted })
        .take(limit)

    fun labelOf(r: StatRow, dim: Dimension): String = when (dim) {
        Dimension.SUBJECT -> r.subject
        Dimension.CHAPTER -> "${r.subject} - ${r.chapter ?: UNSPECIFIED}"
        Dimension.TOPIC -> listOfNotNull(r.subject, r.chapter ?: UNSPECIFIED, r.topic).joinToString(" - ")
        Dimension.DIFFICULTY -> r.difficulty?.lowercase()?.replaceFirstChar { it.uppercaseChar() } ?: UNSPECIFIED
        Dimension.TYPE -> typeLabel(r.type)
        Dimension.SUBJECT_KIND -> "${r.subject} - ${if (r.type == QuestionType.NUMERICAL.name) "Numerical" else "Theory"}"
    }

    fun typeLabel(type: String): String = when (type) {
        "SINGLE_MCQ" -> "Single MCQ"
        "MULTI_MCQ" -> "Multi MCQ"
        "NUMERICAL" -> "Numerical"
        "TRUE_FALSE" -> "True/False"
        "ASSERTION_REASON" -> "Assertion-Reason"
        "MATCH" -> "Match"
        "FILL_BLANK" -> "Fill blank"
        "IMAGE" -> "Image"
        else -> type
    }

    private fun fold(label: String, rows: List<StatRow>): Breakdown {
        fun count(v: Verdict) = rows.count { it.verdict == v.name }
        return Breakdown(
            label = label,
            total = rows.size,
            correct = count(Verdict.CORRECT),
            wrong = count(Verdict.WRONG),
            unattempted = count(Verdict.UNATTEMPTED),
            unscored = count(Verdict.UNSCORED),
            score = rows.sumOf { it.marksAwarded },
            totalTimeMs = rows.sumOf { it.timeSpentMs },
        )
    }
}
