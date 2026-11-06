package com.invictus.abhyas.domain.model

enum class QuestionType {
    SINGLE_MCQ, MULTI_MCQ, NUMERICAL, TRUE_FALSE, ASSERTION_REASON, MATCH, FILL_BLANK, IMAGE
}

enum class Exam { NEET, JEE_MAIN }

enum class BankKind { THEORY, NUMERICALS, PYQS, MIXED }

enum class Difficulty { EASY, MEDIUM, HARD }

/** Case/separator tolerant enum parsing ("jee-main", "Jee Main" -> JEE_MAIN). */
inline fun <reified E : Enum<E>> parseEnum(raw: String?): E? {
    val key = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
    return enumValues<E>().firstOrNull { it.name == key }
}
