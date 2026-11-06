package com.invictus.abhyas.domain.pack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** manifest.json of a question-bank package. [sha256] is the hex digest of the raw questions.json bytes. */
@Serializable
data class BankManifest(
    val id: String,
    val version: Int,
    val title: String? = null,
    val exam: String,
    @SerialName("class") val classLevel: Int? = null,
    val subject: String,
    val kind: String,
    val sha256: String,
    val questionCount: Int,
    val formatVersion: Int = 1,
)

/**
 * One entry of questions.json (a JSON array). exam / subject / class fall back to the manifest.
 * Also used as the frozen per-attempt snapshot format.
 */
@Serializable
data class QuestionDto(
    val id: String,
    val bankId: String? = null,
    val type: String,
    val statement: String,
    val payload: JsonElement,
    val explanation: String? = null,
    val exam: String? = null,
    val subject: String? = null,
    @SerialName("class") val classLevel: Int? = null,
    val chapter: String? = null,
    val topic: String? = null,
    val difficulty: String? = null,
    val year: Int? = null,
    val source: String? = null,
    val sourcePage: Int? = null,
    val tags: List<String> = emptyList(),
    val imagePath: String? = null,
    val marks: Double? = null,
    val negativeMarks: Double? = null,
    val language: String = "en",
    /** Defaults to "an answer is present". Explicit true without an answer is rejected. */
    val answerVerified: Boolean? = null,
)
