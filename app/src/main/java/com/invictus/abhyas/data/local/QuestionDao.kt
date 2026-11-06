package com.invictus.abhyas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
