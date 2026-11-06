package com.invictus.abhyas.data.repo

import androidx.room.withTransaction
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.local.toEntity
import com.invictus.abhyas.domain.pack.PackageParseResult
import com.invictus.abhyas.domain.pack.PackageParser
import com.invictus.abhyas.domain.pack.QuestionError
import kotlin.coroutines.cancellation.CancellationException

data class ImportReport(
    val bankId: String,
    val version: Int,
    val imported: Int,
    /** Skipped: same content already present (this package or another bank). */
    val duplicatesSkipped: Int,
    val errors: List<QuestionError>,
    val replacedVersion: Int?,
)

sealed interface ImportResult {
    data class Success(val report: ImportReport) : ImportResult
    data class AlreadyInstalled(val bankId: String, val version: Int) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * Installs a question-bank package. Checksum-verified, one DB transaction (all-or-nothing),
 * idempotent (same version = no-op; dedupe by contentHash), per-question errors reported.
 * Reading the manifest/questions bytes from SAF/zip is the caller's job.
 */
class BankImporter(
    private val db: AbhyasDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun install(manifestBytes: ByteArray, questionsBytes: ByteArray): ImportResult {
        val parsed = when (val r = PackageParser.parse(manifestBytes, questionsBytes)) {
            is PackageParseResult.Failure -> return ImportResult.Failure(r.reason)
            is PackageParseResult.Success -> r
        }
        val manifest = parsed.manifest
        if (parsed.questions.isEmpty()) {
            return ImportResult.Failure("No valid questions in package (${parsed.errors.size} invalid)")
        }

        return try {
            db.withTransaction {
                val existing = db.bankDao().getById(manifest.id)
                if (existing != null && existing.version == manifest.version) {
                    return@withTransaction ImportResult.AlreadyInstalled(existing.id, existing.version)
                }
                if (existing != null && existing.version > manifest.version) {
                    return@withTransaction ImportResult.Failure(
                        "Installed version ${existing.version} is newer than package version ${manifest.version}",
                    )
                }
                // Upgrade: replace this bank's questions (ids are stable, so bookmarks/mistakes survive).
                if (existing != null) db.questionDao().deleteByBank(manifest.id)

                var imported = 0
                parsed.questions.chunked(500).forEach { chunk ->
                    val results = db.questionDao().insertAll(chunk.map { it.question.toEntity(it.contentHash) })
                    imported += results.count { it != -1L }
                }
                val skippedAgainstDb = parsed.questions.size - imported

                db.bankDao().upsert(
                    BankEntity(
                        id = manifest.id,
                        title = manifest.title?.takeIf { it.isNotBlank() } ?: manifest.id,
                        version = manifest.version,
                        exam = parsed.exam.name,
                        classLevel = manifest.classLevel,
                        subject = manifest.subject,
                        kind = parsed.kind.name,
                        checksum = manifest.sha256.lowercase(),
                        questionCount = imported,
                        installedAt = clock(),
                    ),
                )
                ImportResult.Success(
                    ImportReport(
                        bankId = manifest.id,
                        version = manifest.version,
                        imported = imported,
                        duplicatesSkipped = parsed.duplicatesInPackage + skippedAgainstDb,
                        errors = parsed.errors,
                        replacedVersion = existing?.version,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.Failure("Import failed and was rolled back: ${e.message}")
        }
    }

    /** Removes a bank with its questions, bookmarks and mistakes. Past attempts keep their snapshots. */
    suspend fun remove(bankId: String) {
        db.withTransaction {
            db.questionDao().deleteBookmarksOfBank(bankId)
            db.questionDao().deleteMistakesOfBank(bankId)
            db.questionDao().deleteByBank(bankId)
            db.bankDao().delete(bankId)
        }
    }
}
