package com.invictus.abhyas.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM questions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE exam = :exam")
    fun observeCountForExam(exam: String): Flow<Int>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Query("DELETE FROM questions WHERE bankId = :bankId")
    suspend fun deleteByBank(bankId: String)

    @Query("SELECT COUNT(*) FROM questions WHERE bankId = :bankId")
    suspend fun countByBank(bankId: String): Int

    @Query("DELETE FROM bookmarks WHERE questionId IN (SELECT id FROM questions WHERE bankId = :bankId)")
    suspend fun deleteBookmarksOfBank(bankId: String)

    @Query("DELETE FROM mistakes WHERE questionId IN (SELECT id FROM questions WHERE bankId = :bankId)")
    suspend fun deleteMistakesOfBank(bankId: String)

    // --- Filtered access (SQL comes from QuestionQuery, always parameterised) ---

    @RawQuery(observedEntities = [QuestionEntity::class, BookmarkEntity::class, MistakeEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, QuestionEntity>

    @RawQuery(observedEntities = [QuestionEntity::class, BookmarkEntity::class, MistakeEntity::class])
    fun observeCountRaw(query: SupportSQLiteQuery): Flow<Int>

    @RawQuery
    suspend fun idsRaw(query: SupportSQLiteQuery): List<String>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<QuestionEntity>

    // --- Filter facets ---

    @Query("SELECT DISTINCT subject FROM questions ORDER BY subject")
    fun observeSubjects(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT chapter FROM questions WHERE chapter IS NOT NULL " +
            "AND (:subject IS NULL OR subject = :subject) ORDER BY chapter",
    )
    fun observeChapters(subject: String?): Flow<List<String>>

    @Query(
        "SELECT DISTINCT topic FROM questions WHERE topic IS NOT NULL " +
            "AND (:subject IS NULL OR subject = :subject) " +
            "AND (:chapter IS NULL OR chapter = :chapter) ORDER BY topic",
    )
    fun observeTopics(subject: String?, chapter: String?): Flow<List<String>>

    @Query("SELECT DISTINCT year FROM questions WHERE year IS NOT NULL ORDER BY year DESC")
    fun observeYears(): Flow<List<Int>>
}
