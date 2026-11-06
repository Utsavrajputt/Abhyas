package com.invictus.abhyas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey val id: String,
    val title: String,
    val version: Int,
    val exam: String,
    val classLevel: Int?,
    val subject: String,
    val kind: String,
    val checksum: String,
    val questionCount: Int,
    val installedAt: Long,
)

@Entity(
    tableName = "attempts",
    indices = [Index(value = ["status"]), Index(value = ["startedAt"])],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val title: String,
    /** IN_PROGRESS or SUBMITTED. */
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    /** Wall-clock deadline (epoch ms) for timed attempts; lets the timer survive process death. */
    val endsAt: Long?,
    val currentIndex: Int,
    val templateJson: String?,
    val configJson: String,
    val score: Double,
    val maxScore: Double,
    val correctCount: Int,
    val wrongCount: Int,
    val unattemptedCount: Int,
    val unscoredCount: Int,
)

/**
 * Frozen copy of a question as it appeared in one attempt (order, shuffled options and content),
 * so later bank edits/removals never change past results. Analytics columns are denormalised.
 */
@Entity(
    tableName = "attempt_questions",
    primaryKeys = ["attemptId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["questionId"])],
)
data class AttemptQuestionEntity(
    val attemptId: Long,
    val position: Int,
    val questionId: String,
    val snapshotJson: String,
    val type: String,
    val subject: String,
    val chapter: String?,
    val topic: String?,
    val difficulty: String?,
    val responseJson: String?,
    val marked: Boolean,
    val visited: Boolean,
    val timeSpentMs: Long,
    val verdict: String?,
    val marksAwarded: Double,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val questionId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "mistakes",
    indices = [Index(value = ["resolved"])],
)
data class MistakeEntity(
    @PrimaryKey val questionId: String,
    val wrongCount: Int,
    val lastWrongAt: Long,
    val lastAttemptId: Long?,
    val resolved: Boolean,
)
