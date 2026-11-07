package com.invictus.abhyas.data.ncert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.invictus.abhyas.AbhyasApp

/** Builds the paragraph index and chapter list of a book. Resumable: progress is saved per page. */
class BookIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_BOOK) ?: return Result.failure()
        val repo = (applicationContext as AbhyasApp).books
        return when (repo.indexBook(id) { isStopped }) {
            BookRepository.Outcome.DONE -> Result.success()
            BookRepository.Outcome.STOPPED, BookRepository.Outcome.RETRY -> Result.retry()
            BookRepository.Outcome.FAILED -> Result.failure()
        }
    }

    companion object { const val KEY_BOOK = "bookId" }
}

/** Downloads a book from a hosted index entry, resuming partial files and verifying its SHA-256. */
class BookDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_BOOK) ?: return Result.failure()
        val repo = (applicationContext as AbhyasApp).books
        return when (repo.downloadBook(id) { isStopped }) {
            BookRepository.Outcome.DONE -> Result.success()
            BookRepository.Outcome.STOPPED, BookRepository.Outcome.RETRY -> Result.retry()
            BookRepository.Outcome.FAILED -> Result.failure()
        }
    }

    companion object { const val KEY_BOOK = "bookId" }
}
