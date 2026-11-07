package com.invictus.abhyas.domain.attempt

import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.parseEnum
import com.invictus.abhyas.domain.pack.AbhyasJson
import kotlinx.serialization.Serializable

@Serializable
data class Marking(val correct: Double, val wrong: Double)

@Serializable
data class TemplateSection(
    val title: String,
    val subject: String? = null,
    /** "THEORY" (everything except numerical) or "NUMERICAL"; null = any. */
    val kind: String? = null,
    val count: Int,
    val marking: Marking? = null,
)

/** JSON exam template: duration, sections and (overridable) marking. Never hard-coded in Kotlin. */
@Serializable
data class ExamTemplate(
    val id: String,
    val name: String,
    val exam: String? = null,
    val durationMinutes: Int? = null,
    val marking: Marking? = null,
    val sections: List<TemplateSection> = emptyList(),
    /** Used when [sections] is empty: a plain N-question timed test. */
    val questionCount: Int? = null,
    val note: String? = null,
) {
    val totalQuestions: Int get() = if (sections.isEmpty()) questionCount ?: 0 else sections.sumOf { it.count }

    val examEnum: Exam? get() = parseEnum<Exam>(exam)

    /** Marking for a question in [section]: section override > template default > question's own. */
    fun markingFor(section: TemplateSection?): Marking? = section?.marking ?: marking
}

sealed interface TemplateParseResult {
    data class Ok(val template: ExamTemplate) : TemplateParseResult
    data class Error(val message: String) : TemplateParseResult
}

object TemplateParser {
    fun parse(json: String): TemplateParseResult {
        val t = try {
            AbhyasJson.json.decodeFromString(ExamTemplate.serializer(), json)
        } catch (e: Exception) {
            return TemplateParseResult.Error("Invalid template JSON: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        validate(t)?.let { return TemplateParseResult.Error(it) }
        return TemplateParseResult.Ok(t)
    }

    fun validate(t: ExamTemplate): String? {
        if (t.id.isBlank()) return "id is blank"
        if (t.name.isBlank()) return "name is blank"
        if (t.exam != null && parseEnum<Exam>(t.exam) == null) return "unknown exam '${t.exam}'"
        if (t.durationMinutes != null && t.durationMinutes <= 0) return "durationMinutes must be > 0"
        if (t.sections.isEmpty() && (t.questionCount ?: 0) <= 0) return "needs sections or questionCount > 0"
        if (t.sections.any { it.count <= 0 }) return "section count must be > 0"
        if (t.sections.any { it.title.isBlank() }) return "section title is blank"
        if (t.sections.any { it.kind != null && it.kind.uppercase() !in setOf("THEORY", "NUMERICAL") }) {
            return "section kind must be THEORY or NUMERICAL"
        }
        val markings = listOfNotNull(t.marking) + t.sections.mapNotNull { it.marking }
        if (markings.any { !it.correct.isFinite() || !it.wrong.isFinite() || it.correct < 0 || it.wrong < 0 }) {
            return "marking values must be finite and >= 0"
        }
        return null
    }
}
