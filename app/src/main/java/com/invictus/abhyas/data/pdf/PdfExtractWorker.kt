package com.invictus.abhyas.data.pdf

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.invictus.abhyas.AbhyasApp

/** Background extraction of one PDF job. Progress is saved per page, so a stop/crash resumes where it left off. */
class PdfExtractWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getLong(KEY_JOB, -1L)
        if (jobId < 0) return Result.failure()
        val repo = (applicationContext as AbhyasApp).pdfStudio
        return when (repo.extract(jobId) { isStopped }) {
            PdfStudioRepository.Outcome.DONE -> Result.success()
            PdfStudioRepository.Outcome.STOPPED -> Result.retry()
            PdfStudioRepository.Outcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_JOB = "jobId"
    }
}
