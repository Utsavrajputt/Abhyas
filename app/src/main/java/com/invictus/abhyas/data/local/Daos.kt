package com.invictus.abhyas.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.invictus.abhyas.domain.analytics.StatRow
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

    @Query(
        "SELECT subject, chapter, topic, difficulty, type, verdict, marksAwarded, timeSpentMs " +
            "FROM attempt_questions WHERE attemptId = :attemptId",
    )
    suspend fun statRows(attemptId: Long): List<StatRow>

    /** Scored questions of every submitted attempt; drives the overview analytics. */
    @Query(
        "SELECT aq.subject AS subject, aq.chapter AS chapter, aq.topic AS topic, aq.difficulty AS difficulty, " +
            "aq.type AS type, aq.verdict AS verdict, aq.marksAwarded AS marksAwarded, aq.timeSpentMs AS timeSpentMs " +
            "FROM attempt_questions aq JOIN attempts a ON a.id = aq.attemptId WHERE a.status = 'SUBMITTED'",
    )
    fun observeAllStatRows(): Flow<List<StatRow>>

    @Query("SELECT * FROM attempt_questions WHERE attemptId = :attemptId ORDER BY position")
    suspend fun getQuestions(attemptId: Long): List<AttemptQuestionEntity>

    @Query("SELECT * FROM attempts WHERE status = 'SUBMITTED' ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<AttemptEntity>>

    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)

    @Query("SELECT * FROM attempts WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<AttemptEntity?>

    @Query(
        "UPDATE attempt_questions SET responseJson = :responseJson, marked = :marked, " +
            "visited = :visited, timeSpentMs = :timeSpentMs WHERE attemptId = :attemptId AND position = :position",
    )
    suspend fun saveQuestionState(
        attemptId: Long,
        position: Int,
        responseJson: String?,
        marked: Boolean,
        visited: Boolean,
        timeSpentMs: Long,
    )

    @Query("UPDATE attempts SET currentIndex = :index WHERE id = :id")
    suspend fun setCurrentIndex(id: Long, index: Int)

    /** Question ids shown in any attempt started at or after [sinceMs]; feeds the randomizer. */
    @Query(
        "SELECT DISTINCT aq.questionId FROM attempt_questions aq " +
            "JOIN attempts a ON a.id = aq.attemptId WHERE a.startedAt >= :sinceMs",
    )
    suspend fun recentQuestionIds(sinceMs: Long): List<String>
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

    @Query("SELECT q.* FROM questions q JOIN bookmarks b ON b.questionId = q.id ORDER BY b.createdAt DESC LIMIT 500")
    fun observeQuestions(): Flow<List<QuestionEntity>>
}

@Dao
interface MistakeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mistake: MistakeEntity)

    @Query("SELECT * FROM mistakes WHERE questionId = :questionId")
    suspend fun get(questionId: String): MistakeEntity?

    @Query("SELECT COUNT(*) FROM mistakes WHERE resolved = 0")
    fun observeUnresolvedCount(): Flow<Int>

    @Query(
        "SELECT q.*, m.wrongCount AS wrongCount FROM questions q JOIN mistakes m ON m.questionId = q.id " +
            "WHERE m.resolved = 0 ORDER BY m.lastWrongAt DESC LIMIT 500",
    )
    fun observeUnresolved(): Flow<List<MistakeRow>>

    @Query("UPDATE mistakes SET resolved = 1 WHERE questionId = :questionId")
    suspend fun resolve(questionId: String)
}

data class MistakeRow(
    @Embedded val question: QuestionEntity,
    val wrongCount: Int,
)
