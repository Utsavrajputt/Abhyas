package com.invictus.abhyas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Common question record. Type-specific data (options, correct answer, numeric tolerance,
 * match pairs, ...) lives in [payloadJson] and is validated per [type] in the domain layer
 * (Milestone 1). Filterable fields are real columns so filtering stays database-backed.
 */
@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["exam", "subject", "classLevel", "chapter"]),
        Index(value = ["bankId"]),
        Index(value = ["contentHash"], unique = true),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    val type: String,            // SINGLE_MCQ, MULTI_MCQ, NUMERICAL, TRUE_FALSE, ASSERTION_REASON, MATCH, FILL_BLANK, IMAGE
    val statement: String,
    val payloadJson: String,
    val explanation: String? = null,
    val exam: String,            // NEET, JEE_MAIN
    val subject: String,
    val classLevel: Int? = null,
    val chapter: String? = null,
    val topic: String? = null,
    val difficulty: String? = null,
    val year: Int? = null,
    val source: String? = null,
    val sourcePage: Int? = null,
    val tags: String = "",
    val imagePath: String? = null,
    val marks: Double = 4.0,
    val negativeMarks: Double = 1.0,
    val language: String = "en",
    val ncertRefJson: String? = null,
    val answerVerified: Boolean = false,
    val contentHash: String,
)
