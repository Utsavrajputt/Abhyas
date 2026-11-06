package com.invictus.abhyas.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Option(val id: String, val text: String)

/**
 * Type-specific question data. Answer fields are nullable on purpose: a source without an
 * answer key is imported with `answerVerified = false` and is never guessed.
 */
sealed interface QuestionPayload

/** Used by SINGLE_MCQ and IMAGE questions. */
@Serializable
data class SingleChoicePayload(
    val options: List<Option>,
    val correctId: String? = null,
) : QuestionPayload

@Serializable
data class MultiChoicePayload(
    val options: List<Option>,
    val correctIds: List<String> = emptyList(),
) : QuestionPayload

@Serializable
data class NumericalPayload(
    val answer: Double? = null,
    /** Absolute tolerance: |given - answer| <= tolerance. */
    val tolerance: Double = 0.0,
    val integerOnly: Boolean = false,
    val unit: String? = null,
) : QuestionPayload

@Serializable
data class TrueFalsePayload(val answer: Boolean? = null) : QuestionPayload

/** Standard four options ([AssertionReasonOptions.ALL]); [correctOption] is 1..4. */
@Serializable
data class AssertionReasonPayload(
    val assertion: String,
    val reason: String,
    val correctOption: Int? = null,
) : QuestionPayload

@Serializable
data class MatchPayload(
    val leftTitle: String = "List I",
    val rightTitle: String = "List II",
    val left: List<Option>,
    val right: List<Option>,
    /** Answer combinations such as "A-2, B-1, C-4, D-3". */
    val options: List<Option>,
    val correctId: String? = null,
) : QuestionPayload

@Serializable
data class FillBlankPayload(
    val acceptedAnswers: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
) : QuestionPayload

object AssertionReasonOptions {
    val ALL: List<Option> = listOf(
        Option("1", "Both Assertion and Reason are true and Reason is the correct explanation of Assertion"),
        Option("2", "Both Assertion and Reason are true but Reason is not the correct explanation of Assertion"),
        Option("3", "Assertion is true but Reason is false"),
        Option("4", "Assertion is false but Reason is true"),
    )
}

fun QuestionPayload.hasAnswer(): Boolean = when (this) {
    is SingleChoicePayload -> correctId != null
    is MultiChoicePayload -> correctIds.isNotEmpty()
    is NumericalPayload -> answer != null
    is TrueFalsePayload -> answer != null
    is AssertionReasonPayload -> correctOption != null
    is MatchPayload -> correctId != null
    is FillBlankPayload -> acceptedAnswers.isNotEmpty()
}

/** Same payload with the answer cleared; used so dedupe ignores answer-key presence. */
fun QuestionPayload.withoutAnswer(): QuestionPayload = when (this) {
    is SingleChoicePayload -> copy(correctId = null)
    is MultiChoicePayload -> copy(correctIds = emptyList())
    is NumericalPayload -> copy(answer = null, tolerance = 0.0)
    is TrueFalsePayload -> copy(answer = null)
    is AssertionReasonPayload -> copy(correctOption = null)
    is MatchPayload -> copy(correctId = null)
    is FillBlankPayload -> copy(acceptedAnswers = emptyList())
}
