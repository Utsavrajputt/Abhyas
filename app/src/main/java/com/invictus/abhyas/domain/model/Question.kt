package com.invictus.abhyas.domain.model

data class Question(
    /** Globally unique: "<bankId>:<id in package>". */
    val id: String,
    val bankId: String,
    val type: QuestionType,
    val statement: String,
    val payload: QuestionPayload,
    val explanation: String? = null,
    val exam: Exam,
    val subject: String,
    val classLevel: Int? = null,
    val chapter: String? = null,
    val topic: String? = null,
    val difficulty: Difficulty? = null,
    val year: Int? = null,
    val source: String? = null,
    val sourcePage: Int? = null,
    val tags: List<String> = emptyList(),
    val imagePath: String? = null,
    val marks: Double = 4.0,
    val negativeMarks: Double = 1.0,
    val language: String = "en",
    val answerVerified: Boolean = false,
)

/** What the student entered/selected. */
sealed interface Response {
    data class Choice(val ids: Set<String>) : Response
    data class Number(val value: Double) : Response
    data class Bool(val value: Boolean) : Response
    data class Text(val value: String) : Response
}

enum class Verdict { CORRECT, WRONG, UNATTEMPTED, UNSCORED }

data class Score(val verdict: Verdict, val marks: Double)
