package com.invictus.abhyas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object BookStatus {
    const val DOWNLOADING = "DOWNLOADING"
    const val INDEXING = "INDEXING"
    const val READY = "READY"
    const val FAILED = "FAILED"
}

/** An NCERT (or any textbook) PDF stored in app storage. */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val classLevel: Int?,
    val subject: String,
    val localPath: String,
    val pageCount: Int,
    val checksum: String,
    /** LOCAL (imported from the phone) or DOWNLOAD (from a book index). */
    val origin: String,
    val sourceUrl: String?,
    val status: String,
    /** Pages whose text has been indexed (resume point for the indexer). */
    val indexedPages: Int,
    val message: String?,
    val lastPage: Int,
    val addedAt: Long,
)

@Entity(
    tableName = "book_chapters",
    primaryKeys = ["bookId", "idx"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class BookChapterEntity(
    val bookId: String,
    val idx: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
)

/** One paragraph of a page, with its vertical band (points from the top) for highlighting. */
@Entity(
    tableName = "passages",
    primaryKeys = ["bookId", "page", "idx"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["bookId", "textHash"])],
)
data class PassageEntity(
    val bookId: String,
    val page: Int,
    val idx: Int,
    val text: String,
    val textHash: String,
    val top: Float,
    val bottom: Float,
    val pageHeight: Float,
)

@Entity(
    tableName = "book_bookmarks",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["bookId", "page"], unique = true)],
)
data class BookBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val page: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "saved_passages",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["bookId", "textHash"], unique = true)],
)
data class SavedPassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val page: Int,
    val paragraph: Int,
    val textHash: String,
    val text: String,
    val createdAt: Long,
)

/** Pages the reader has actually shown; drives per-chapter progress. */
@Entity(
    tableName = "read_pages",
    primaryKeys = ["bookId", "page"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class ReadPageEntity(val bookId: String, val page: Int)
