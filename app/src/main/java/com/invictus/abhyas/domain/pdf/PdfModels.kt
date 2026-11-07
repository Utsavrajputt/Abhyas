package com.invictus.abhyas.domain.pdf

import kotlinx.serialization.Serializable

/** How the text of one PDF page was obtained. */
enum class ExtractMethod { TEXT, OCR }

/** One page of raw text plus where each line sits (for figure cropping). */
data class PageText(
    val page: Int,
    val text: String,
    val method: ExtractMethod,
    val hasImages: Boolean = false,
    val lines: List<LinePos> = emptyList(),
    val pageHeight: Float = 0f,
)

/** Start of a text line and its vertical position (PDF points from the top of the page). */
@Serializable
data class LinePos(val t: String, val y: Float)

/** Where answers are expected. AUTO tries both and prefers an inline answer over the key. */
enum class AnswerLayout(val label: String) {
    AUTO("Auto"),
    KEY_AT_END("Key at end"),
    INLINE("Inline"),
}

enum class DraftStatus { OK, MISSING_ANSWER, LOW_CONFIDENCE, DUPLICATE, INVALID }

/** A parsed (not yet imported) question. Payload is kept as JSON so the review UI can edit freely. */
data class ParsedDraft(
    val position: Int,
    /** Original numbering from the source, e.g. "17" or "5.3". */
    val number: String,
    val page: Int,
    val typeName: String,
    val statement: String,
    val payloadJson: String,
    val explanation: String?,
    val confidence: Double,
    val issues: List<String>,
    val hasFigureHint: Boolean,
)

data class ParseOptions(
    val layout: AnswerLayout = AnswerLayout.AUTO,
    val ncert: Boolean = false,
)

data class ParseOutput(
    val drafts: List<ParsedDraft>,
    val keyEntries: Int,
    val notes: List<String>,
)
