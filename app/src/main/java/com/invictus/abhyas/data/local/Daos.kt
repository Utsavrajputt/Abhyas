package com.invictus.abhyas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bank: BankEntity)

    @Query("SELECT * FROM banks WHERE id = :id")
    suspend fun getById(id: String): BankEntity?

    @Query("SELECT * FROM banks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<BankEntity>>

    @Query("DELETE FROM banks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Insert
    suspend fun insertQuestions(questions: List<AttemptQuestionEntity>)

    @Update
    suspend fun updateAttempt(attempt: AttemptEntity)

    @Update
    suspend fun updateQuestion(question: AttemptQuestionEntity)

    @Query("SELECT * FROM attempts WHERE id = :id")
    suspend fun getAttempt(id: Long): AttemptEntity?

    @Query("SELECT * FROM attempts WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getInProgress(): AttemptEntity?

    @Query("SELECT * FROM attempt_questions WHERE attemptId = :attemptId ORDER BY position")
    suspend fun getQuestions(attemptId: Long): List<AttemptQuestionEntity>

    @Query("SELECT * FROM attempts WHERE status = 'SUBMITTED' ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<AttemptEntity>>

    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE questionId = :questionId")
    suspend fun remove(questionId: String)

    @Query("SELECT questionId FROM bookmarks")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE questionId = :questionId")
    suspend fun count(questionId: String): Int
}

@Dao
interface MistakeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mistake: MistakeEntity)

    @Query("SELECT * FROM mistakes WHERE questionId = :questionId")
    suspend fun get(questionId: String): MistakeEntity?

    @Query("SELECT COUNT(*) FROM mistakes WHERE resolved = 0")
    fun observeUnresolvedCount(): Flow<Int>
}
