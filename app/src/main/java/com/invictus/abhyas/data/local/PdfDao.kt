package com.invictus.abhyas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class IdStatement(val id: String, val statement: String)

@Dao
interface PdfDao {
    // ---- jobs ----
    @Insert
    suspend fun insertJob(job: PdfJobEntity): Long

    @Update
    suspend fun updateJob(job: PdfJobEntity)

    @Query("SELECT * FROM pdf_jobs WHERE id = :id")
    suspend fun getJob(id: Long): PdfJobEntity?

    @Query("SELECT * FROM pdf_jobs WHERE id = :id")
    fun observeJob(id: Long): Flow<PdfJobEntity?>

    @Query("SELECT * FROM pdf_jobs ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<PdfJobEntity>>

    @Query("UPDATE pdf_jobs SET status = :status, message = :message, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, message: String?, now: Long)

    @Query("UPDATE pdf_jobs SET nextPage = :nextPage, ocrPages = :ocrPages, updatedAt = :now WHERE id = :id")
    suspend fun setProgress(id: Long, nextPage: Int, ocrPages: Int, now: Long)

    @Query("UPDATE pdf_jobs SET configJson = :configJson, updatedAt = :now WHERE id = :id")
    suspend fun setConfig(id: Long, configJson: String, now: Long)

    @Query("DELETE FROM pdf_jobs WHERE id = :id")
    suspend fun deleteJob(id: Long)

    // ---- pages ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PdfPageEntity)

    @Query("SELECT * FROM pdf_pages WHERE jobId = :jobId ORDER BY page")
    suspend fun pages(jobId: Long): List<PdfPageEntity>

    @Query("SELECT * FROM pdf_pages WHERE jobId = :jobId AND page = :page")
    suspend fun page(jobId: Long, page: Int): PdfPageEntity?

    // ---- drafts ----
    @Insert
    suspend fun insertDrafts(drafts: List<PdfDraftEntity>): List<Long>

    @Query("DELETE FROM pdf_drafts WHERE jobId = :jobId")
    suspend fun deleteDrafts(jobId: Long)

    @Query("SELECT * FROM pdf_drafts WHERE jobId = :jobId ORDER BY position")
    fun observeDrafts(jobId: Long): Flow<List<PdfDraftEntity>>

    @Query("SELECT * FROM pdf_drafts WHERE jobId = :jobId ORDER BY position")
    suspend fun drafts(jobId: Long): List<PdfDraftEntity>

    @Query("SELECT * FROM pdf_drafts WHERE id = :id")
    suspend fun draft(id: Long): PdfDraftEntity?

    @Update
    suspend fun updateDraft(draft: PdfDraftEntity)

    @Update
    suspend fun updateDrafts(drafts: List<PdfDraftEntity>)

    @Query("UPDATE pdf_drafts SET selected = :selected WHERE id IN (:ids)")
    suspend fun setSelected(ids: List<Long>, selected: Boolean)

    @Query("DELETE FROM pdf_drafts WHERE id IN (:ids)")
    suspend fun deleteDraftsByIds(ids: List<Long>)

    // ---- library lookups for duplicate detection / suggestions ----
    @Query("SELECT contentHash FROM questions WHERE contentHash IN (:hashes)")
    suspend fun existingHashes(hashes: List<String>): List<String>

    @Query("SELECT id, statement FROM questions WHERE subject = :subject ORDER BY rowid LIMIT :limit OFFSET :offset")
    suspend fun statementsBySubject(subject: String, limit: Int, offset: Int): List<IdStatement>

    @Query("SELECT DISTINCT chapter FROM questions WHERE chapter IS NOT NULL AND subject = :subject ORDER BY chapter")
    suspend fun chaptersOf(subject: String): List<String>
}
