package com.invictus.abhyas.domain.query

import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.QuestionType

enum class KindFilter { ALL, THEORY, NUMERICAL }

data class QuestionFilter(
    val exam: Exam? = null,
    val classLevel: Int? = null,
    val subject: String? = null,
    val chapter: String? = null,
    val topic: String? = null,
    val type: QuestionType? = null,
    val difficulty: Difficulty? = null,
    val year: Int? = null,
    val kind: KindFilter = KindFilter.ALL,
    val query: String = "",
    val bankId: String? = null,
    val bookmarkedOnly: Boolean = false,
    /** Unresolved entries of the mistake notebook. */
    val incorrectOnly: Boolean = false,
    val pyqOnly: Boolean = false,
)

/** Builds parameterised SQL for [QuestionFilter]. Pure, so it is unit-testable without Room. */
object QuestionQuery {
    class Built(val sql: String, val args: List<Any>)

    fun select(f: QuestionFilter, columns: String = "*", orderBy: String? = "id"): Built {
        val (where, args) = where(f)
        val sql = buildString {
            append("SELECT ").append(columns).append(" FROM questions")
            if (where.isNotEmpty()) append(" WHERE ").append(where)
            if (orderBy != null) append(" ORDER BY ").append(orderBy)
        }
        return Built(sql, args)
    }

    fun count(f: QuestionFilter): Built = select(f, columns = "COUNT(*)", orderBy = null)

    private fun where(f: QuestionFilter): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        fun eq(column: String, value: Any?) {
            if (value != null) { clauses += "$column = ?"; args += value }
        }
        eq("exam", f.exam?.name)
        eq("classLevel", f.classLevel)
        eq("subject", f.subject)
        eq("chapter", f.chapter)
        eq("topic", f.topic)
        eq("type", f.type?.name)
        eq("difficulty", f.difficulty?.name)
        eq("year", f.year)
        eq("bankId", f.bankId)

        when (f.kind) {
            KindFilter.ALL -> Unit
            KindFilter.THEORY -> clauses += "type <> 'NUMERICAL'"
            KindFilter.NUMERICAL -> clauses += "type = 'NUMERICAL'"
        }
        if (f.pyqOnly) clauses += "year IS NOT NULL"
        if (f.bookmarkedOnly) clauses += "id IN (SELECT questionId FROM bookmarks)"
        if (f.incorrectOnly) clauses += "id IN (SELECT questionId FROM mistakes WHERE resolved = 0)"

        // Every search word must match statement, chapter, topic or tags.
        f.query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            val like = "%" + escapeLike(word) + "%"
            clauses += "(statement LIKE ? ESCAPE '\\' OR chapter LIKE ? ESCAPE '\\' " +
                "OR topic LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')"
            repeat(4) { args += like }
        }
        return clauses.joinToString(" AND ") to args
    }

    fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
