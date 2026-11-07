package com.invictus.abhyas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object PdfJobStatus {
    const val QUEUED = "QUEUED"
    const val EXTRACTING = "EXTRACTING"
    const val PARSING = "PARSING"
    const val REVIEW = "REVIEW"
    const val IMPORTED = "IMPORTED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

/** One PDF import. [nextPage] (1-based) is the resume point of the extraction worker. */
@Entity(tableName = "pdf_jobs", indices = [Index(value = ["status"])])
data class PdfJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val localPath: String,
    val pageCount: Int,
    val status: String,
    val nextPage: Int,
    val ocrPages: Int,
    val message: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** [com.invictus.abhyas.data.pdf.PdfJobConfig] as JSON. */
    val configJson: String,
)

/** Raw text of one extracted page (digital text or OCR). Lets parsing be re-run without re-extracting. */
@Entity(
    tableName = "pdf_pages",
    primaryKeys = ["jobId", "page"],
    foreignKeys = [
        ForeignKey(
            entity = PdfJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PdfPageEntity(
    val jobId: Long,
    val page: Int,
    val text: String,
    val method: String,
    val hasImages: Boolean,
    /** JSON list of [com.invictus.abhyas.domain.pdf.LinePos]; used to crop figures. */
    val linesJson: String?,
    val pageHeight: Float,
)

/** A parsed question awaiting review. Edited in place by the review screen. */
@Entity(
    tableName = "pdf_drafts",
    foreignKeys = [
        ForeignKey(
            entity = PdfJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["jobId", "position"])],
)
data class PdfDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val position: Int,
    val number: String,
    val page: Int,
    val type: String,
    val statement: String,
    val payloadJson: String,
    val explanation: String?,
    val subject: String?,
    val chapter: String?,
    val topic: String?,
    val difficulty: String?,
    val confidence: Double,
    /** Parser notes, joined with "\n". */
    val issues: String,
    /** Validation errors (QuestionFactory), joined with "\n"; non-empty = cannot be imported. */
    val errors: String,
    /** OK, MISSING_ANSWER, LOW_CONFIDENCE, DUPLICATE, INVALID. */
    val status: String,
    val duplicateOf: String?,
    val selected: Boolean,
    val edited: Boolean,
    val hasFigureHint: Boolean,
    val imagePath: String?,
    /** Suggested chapter (from keywords), never applied automatically. */
    val suggestedChapter: String?,
)
