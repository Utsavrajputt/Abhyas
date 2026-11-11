package com.invictus.abhyas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Number of numbered chapters ("Ch N: ...") a book has; prelims and appendices are not counted. */
data class ChapterCount(val bookId: String, val n: Int)

@Dao
interface BookDao {
    @Query("SELECT bookId, COUNT(*) AS n FROM book_chapters WHERE title LIKE 'Ch%' GROUP BY bookId")
    fun observeChapterCounts(): Flow<List<ChapterCount>>

    // ---- books ----
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE checksum = :checksum LIMIT 1")
    suspend fun byChecksum(checksum: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY classLevel, subject, title")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY classLevel, subject, title")
    suspend fun books(): List<BookEntity>

    @Query("UPDATE books SET status = :status, indexedPages = :indexed, message = :message WHERE id = :id")
    suspend fun setState(id: String, status: String, indexed: Int, message: String?)

    @Query("UPDATE books SET lastPage = :page WHERE id = :id")
    suspend fun setLastPage(id: String, page: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)

    // ---- chapters ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<BookChapterEntity>)

    @Query("DELETE FROM book_chapters WHERE bookId = :bookId")
    suspend fun deleteChapters(bookId: String)

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY idx")
    fun observeChapters(bookId: String): Flow<List<BookChapterEntity>>

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY idx")
    suspend fun chapters(bookId: String): List<BookChapterEntity>

    // ---- passages ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassages(passages: List<PassageEntity>)

    @Query("DELETE FROM passages WHERE bookId = :bookId AND page = :page")
    suspend fun deletePagePassages(bookId: String, page: Int)

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND page = :page ORDER BY idx")
    suspend fun passagesOnPage(bookId: String, page: Int): List<PassageEntity>

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND idx < 3 ORDER BY page, idx")
    suspend fun pageHeads(bookId: String): List<PassageEntity>

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND page BETWEEN :from AND :to ORDER BY page, idx")
    suspend fun passagesInRange(bookId: String, from: Int, to: Int): List<PassageEntity>

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND page = :page AND idx = :idx")
    suspend fun passage(bookId: String, page: Int, idx: Int): PassageEntity?

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND textHash = :hash LIMIT 1")
    suspend fun passageByHash(bookId: String, hash: String): PassageEntity?

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND text LIKE '%' || :q || '%' ESCAPE '\\' ORDER BY page, idx LIMIT :limit")
    suspend fun search(bookId: String, q: String, limit: Int): List<PassageEntity>

    @Query("SELECT COUNT(*) FROM passages WHERE bookId = :bookId")
    suspend fun passageCount(bookId: String): Int

    // ---- bookmarks / saved passages / progress ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(b: BookBookmarkEntity): Long

    @Query("DELETE FROM book_bookmarks WHERE bookId = :bookId AND page = :page")
    suspend fun deleteBookmark(bookId: String, page: Int)

    @Query("SELECT * FROM book_bookmarks WHERE bookId = :bookId ORDER BY page")
    fun observeBookmarks(bookId: String): Flow<List<BookBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSaved(p: SavedPassageEntity): Long

    @Query("DELETE FROM saved_passages WHERE id = :id")
    suspend fun deleteSaved(id: Long)

    @Query("SELECT * FROM saved_passages WHERE bookId = :bookId ORDER BY page, paragraph")
    fun observeSaved(bookId: String): Flow<List<SavedPassageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markRead(r: List<ReadPageEntity>)

    @Query("SELECT page FROM read_pages WHERE bookId = :bookId")
    fun observeRead(bookId: String): Flow<List<Int>>

    @Query("SELECT bookId, COUNT(*) AS n FROM read_pages GROUP BY bookId")
    fun observeReadCounts(): Flow<List<ReadCount>>
}

data class ReadCount(val bookId: String, val n: Int)
