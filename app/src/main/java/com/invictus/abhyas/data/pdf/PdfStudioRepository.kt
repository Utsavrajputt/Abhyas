package com.invictus.abhyas.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.local.PdfDraftEntity
import com.invictus.abhyas.data.local.PdfJobEntity
import com.invictus.abhyas.data.local.PdfJobStatus
import com.invictus.abhyas.data.local.PdfPageEntity
import com.invictus.abhyas.data.repo.BankImporter
import com.invictus.abhyas.data.repo.ImportResult
import com.invictus.abhyas.domain.pack.AbhyasJson
import com.invictus.abhyas.domain.pdf.DraftStatus
import com.invictus.abhyas.domain.pdf.ExportMeta
import com.invictus.abhyas.domain.pdf.ExportQuestion
import com.invictus.abhyas.domain.pdf.ExtractMethod
import com.invictus.abhyas.domain.pdf.AnswerLayout
import com.invictus.abhyas.domain.pdf.ChapterSuggester
import com.invictus.abhyas.domain.pdf.FigureRegion
import com.invictus.abhyas.domain.pdf.LinePos
import com.invictus.abhyas.domain.pdf.PackageExporter
import com.invictus.abhyas.domain.pdf.PageQuality
import com.invictus.abhyas.domain.pdf.PageText
import com.invictus.abhyas.domain.pdf.ParseOptions
import com.invictus.abhyas.domain.pdf.ParsedDraft
import com.invictus.abhyas.domain.pdf.PdfParser
import com.invictus.abhyas.domain.pdf.Similarity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * PDF Studio pipeline: add PDF -> background extraction (digital text, OCR for scanned pages) ->
 * deterministic parsing -> review (edit, select) -> import as an Abhyas bank or export as a package.
 */
class PdfStudioRepository(
    private val context: Context,
    private val db: AbhyasDatabase,
    private val bankImporter: BankImporter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Outcome { DONE, STOPPED, FAILED }

    class FigureResult(val path: String?, val error: String?)

    sealed interface AddResult {
        data class Added(val jobId: Long) : AddResult
        data class Failed(val message: String) : AddResult
    }

    private val dao get() = db.pdfDao()
    private val root = File(context.filesDir, "pdfstudio")
    private val textExtractor by lazy { PdfTextExtractor(context) }
    private val linesSerializer = ListSerializer(LinePos.serializer())

    fun observeJobs(): Flow<List<PdfJobEntity>> = dao.observeJobs()
    fun observeJob(id: Long): Flow<PdfJobEntity?> = dao.observeJob(id)
    fun observeDrafts(jobId: Long): Flow<List<PdfDraftEntity>> = dao.observeDrafts(jobId)
    suspend fun chapters(subject: String): List<String> = dao.chaptersOf(subject)

    // ------------------------------------------------------------------------------------------
    // Add / schedule
    // ------------------------------------------------------------------------------------------

    suspend fun addPdf(uri: Uri, config: PdfJobConfig): AddResult = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "document.pdf"
        root.mkdirs()
        val tmp = File(root, "incoming_${clock()}.pdf")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext AddResult.Failed("Cannot open the selected file")
            input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
            val head = ByteArray(1024)
            val n = tmp.inputStream().use { it.read(head) }
            if (n <= 0 || !String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF")) {
                tmp.delete()
                return@withContext AddResult.Failed("This file is not a PDF")
            }
            val pages = try {
                PdfPageRenderer(tmp).use { it.pageCount }
            } catch (e: Exception) {
                tmp.delete()
                return@withContext AddResult.Failed("The PDF is password-protected or damaged")
            }
            if (pages <= 0) {
                tmp.delete()
                return@withContext AddResult.Failed("The PDF has no pages")
            }
            val now = clock()
            val cfg = config.copy(
                bankId = config.bankId.ifBlank { defaultBankId(name) },
                title = config.title.ifBlank { name.substringBeforeLast('.') },
            )
            val id = dao.insertJob(
                PdfJobEntity(
                    fileName = name, localPath = tmp.absolutePath, pageCount = pages, status = PdfJobStatus.QUEUED,
                    nextPage = 1, ocrPages = 0, message = null, createdAt = now, updatedAt = now, configJson = cfg.encode(),
                ),
            )
            val dir = File(root, id.toString()).apply { mkdirs() }
            val dest = File(dir, "source.pdf")
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            dao.updateJob(dao.getJob(id)!!.copy(localPath = dest.absolutePath))
            enqueue(id, replace = false)
            AddResult.Added(id)
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: IOException) {
            tmp.delete()
            AddResult.Failed("Could not read the file: ${e.message}")
        }
    }

    private fun displayName(uri: Uri): String? = if (uri.scheme == "file") uri.lastPathSegment else try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun defaultBankId(fileName: String): String {
        val slug = fileName.substringBeforeLast('.').lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(28)
            .ifEmpty { "pdf" }
        return "pdf_${slug}_${SimpleDateFormat("yyMMddHHmm", Locale.ROOT).format(Date(clock()))}"
    }

    fun enqueue(jobId: Long, replace: Boolean) {
        val req = OneTimeWorkRequestBuilder<PdfExtractWorker>()
            .setInputData(workDataOf(PdfExtractWorker.KEY_JOB to jobId))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pdf-$jobId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            req,
        )
    }

    suspend fun cancel(jobId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("pdf-$jobId")
        val job = dao.getJob(jobId) ?: return
        if (job.status == PdfJobStatus.QUEUED || job.status == PdfJobStatus.EXTRACTING || job.status == PdfJobStatus.PARSING) {
            dao.setStatus(jobId, PdfJobStatus.CANCELLED, "Cancelled at page ${job.nextPage}", clock())
        }
    }

    /** Continues a cancelled/failed job from the page where it stopped. */
    suspend fun resume(jobId: Long) {
        val job = dao.getJob(jobId) ?: return
        if (job.status == PdfJobStatus.CANCELLED || job.status == PdfJobStatus.FAILED) {
            dao.setStatus(jobId, PdfJobStatus.QUEUED, null, clock())
            enqueue(jobId, replace = true)
        }
    }

    suspend fun deleteJob(jobId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("pdf-$jobId")
        dao.deleteJob(jobId)
        withContext(Dispatchers.IO) { File(root, jobId.toString()).deleteRecursively() }
    }

    // ------------------------------------------------------------------------------------------
    // Extraction (runs inside the worker)
    // ------------------------------------------------------------------------------------------

    suspend fun extract(jobId: Long, stop: () -> Boolean): Outcome = withContext(Dispatchers.IO) {
        val job = dao.getJob(jobId) ?: return@withContext Outcome.FAILED
        if (job.status == PdfJobStatus.IMPORTED || job.status == PdfJobStatus.CANCELLED || job.status == PdfJobStatus.REVIEW) {
            return@withContext Outcome.DONE
        }
        val cfg = PdfJobConfig.decode(job.configJson)
        val file = File(job.localPath)
        if (!file.exists()) return@withContext fail(jobId, "The stored PDF file is missing")
        dao.setStatus(jobId, PdfJobStatus.EXTRACTING, null, clock())

        val ocr by lazy { PdfOcr() }
        var ocrUsed = false
        var renderer: PdfPageRenderer? = null
        val failedPages = ArrayList<Int>()
        try {
            textExtractor.open(file).use { doc ->
                var ocrPages = job.ocrPages
                for (p in job.nextPage..job.pageCount) {
                    if (stop()) return@withContext Outcome.STOPPED
                    var text = ""
                    var lines: List<LinePos> = emptyList()
                    var height = 0f
                    var images = false
                    var method = ExtractMethod.TEXT
                    try {
                        val raw = textExtractor.extractPage(doc, p)
                        text = raw.text; lines = raw.lines; height = raw.height; images = raw.hasImages
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedPages += p
                    } catch (e: OutOfMemoryError) {
                        failedPages += p
                    }
                    if (cfg.allowOcr && PageQuality.needsOcr(text)) {
                        try {
                            val r = renderer ?: PdfPageRenderer(file).also { renderer = it }
                            val rendered = r.render(p, OCR_WIDTH_PX)
                            try {
                                ocrUsed = true
                                val res = ocr.recognize(rendered.bitmap, rendered.pxPerPoint)
                                if (res.text.isNotBlank()) {
                                    text = res.text; lines = res.lines; method = ExtractMethod.OCR
                                    height = rendered.bitmap.height / rendered.pxPerPoint
                                    ocrPages++
                                    failedPages.remove(p)
                                }
                            } finally {
                                rendered.bitmap.recycle()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (p !in failedPages) failedPages += p
                        } catch (e: OutOfMemoryError) {
                            if (p !in failedPages) failedPages += p
                        }
                    }
                    db.withTransaction {
                        dao.upsertPage(
                            PdfPageEntity(
                                jobId, p, text, method.name, images,
                                if (lines.isEmpty()) null else AbhyasJson.json.encodeToString(linesSerializer, lines),
                                height,
                            ),
                        )
                        dao.setProgress(jobId, p + 1, ocrPages, clock())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            return@withContext fail(jobId, "The PDF is password-protected")
        } catch (e: IOException) {
            return@withContext fail(jobId, "Could not read the PDF: ${e.message}")
        } catch (e: OutOfMemoryError) {
            return@withContext fail(jobId, "Ran out of memory while reading the PDF")
        } finally {
            renderer?.close()
            if (ocrUsed) ocr.close()
        }

        val pages = dao.pages(jobId)
        if (pages.all { it.text.isBlank() }) {
            return@withContext fail(
                jobId,
                if (cfg.allowOcr) "No text could be read, even with OCR" else "No digital text found (turn OCR on for scanned PDFs)",
            )
        }
        parse(jobId, failedPages)
        Outcome.DONE
    }

    private suspend fun fail(jobId: Long, message: String): Outcome {
        dao.setStatus(jobId, PdfJobStatus.FAILED, message, clock())
        return Outcome.FAILED
    }

    // ------------------------------------------------------------------------------------------
    // Parsing (also re-run from the review screen)
    // ------------------------------------------------------------------------------------------

    suspend fun reparse(jobId: Long, config: PdfJobConfig) {
        dao.setConfig(jobId, config.encode(), clock())
        parse(jobId, emptyList())
    }

    private suspend fun parse(jobId: Long, failedPages: List<Int>) {
        val job = dao.getJob(jobId) ?: return
        val cfg = PdfJobConfig.decode(job.configJson)
        dao.setStatus(jobId, PdfJobStatus.PARSING, null, clock())
        val pages = dao.pages(jobId).map { p ->
            PageText(
                page = p.page,
                text = p.text,
                method = if (p.method == ExtractMethod.OCR.name) ExtractMethod.OCR else ExtractMethod.TEXT,
                hasImages = p.hasImages,
                lines = p.linesJson?.let { runCatching { AbhyasJson.json.decodeFromString(linesSerializer, it) }.getOrNull() }.orEmpty(),
                pageHeight = p.pageHeight,
            )
        }
        val layout = runCatching { AnswerLayout.valueOf(cfg.layout) }.getOrDefault(AnswerLayout.AUTO)
        val output = withContext(Dispatchers.Default) { PdfParser.parse(pages, ParseOptions(layout, cfg.ncert)) }
        val entities = assemble(jobId, job.fileName, cfg, output.drafts)
        val notes = ArrayList<String>(output.notes)
        if (failedPages.isNotEmpty()) notes += "Pages that could not be read: ${failedPages.sorted().joinToString()}"
        db.withTransaction {
            dao.deleteDrafts(jobId)
            dao.insertDrafts(entities)
            dao.setStatus(jobId, PdfJobStatus.REVIEW, notes.joinToString("; ").ifBlank { null }, clock())
        }
    }

    private suspend fun assemble(jobId: Long, source: String, cfg: PdfJobConfig, parsed: List<ParsedDraft>): List<PdfDraftEntity> {
        val evals = parsed.map { d ->
            val base = PdfDraftEntity(
                jobId = jobId, position = d.position, number = d.number, page = d.page, type = d.typeName,
                statement = d.statement, payloadJson = d.payloadJson, explanation = d.explanation,
                subject = null, chapter = cfg.chapter, topic = null, difficulty = null,
                confidence = d.confidence, issues = d.issues.joinToString("\n"), errors = "",
                status = DraftStatus.OK.name, duplicateOf = null, selected = true, edited = false,
                hasFigureHint = d.hasFigureHint, imagePath = null, suggestedChapter = null,
            )
            base to DraftEvaluator.evaluate(base, cfg, source)
        }

        // Duplicates inside this PDF and against the library (exact content hash, then near-duplicate text).
        val duplicateOf = arrayOfNulls<String>(evals.size)
        val firstSeen = HashMap<String, Int>()
        evals.forEachIndexed { i, (_, ev) ->
            val h = ev.contentHash ?: return@forEachIndexed
            val prev = firstSeen[h]
            if (prev != null) duplicateOf[i] = "Same as #${evals[prev].first.number} in this PDF" else firstSeen[h] = i
        }
        val hashes = evals.mapNotNull { it.second.contentHash }.distinct()
        val inLibrary = HashSet<String>()
        hashes.chunked(500).forEach { inLibrary += dao.existingHashes(it) }
        evals.forEachIndexed { i, (_, ev) ->
            if (duplicateOf[i] == null && ev.contentHash != null && ev.contentHash in inLibrary) duplicateOf[i] = "Already in your library"
        }
        if (evals.isNotEmpty()) {
            val index = Similarity.Index(evals.map { it.first.statement })
            val bestScore = DoubleArray(evals.size)
            val bestId = arrayOfNulls<String>(evals.size)
            var offset = 0
            while (true) {
                val rows = dao.statementsBySubject(cfg.subject, 2000, offset)
                if (rows.isEmpty()) break
                for (r in rows) {
                    val m = index.best(r.statement) ?: continue
                    if (m.second > bestScore[m.first]) { bestScore[m.first] = m.second; bestId[m.first] = r.id }
                }
                offset += rows.size
            }
            for (i in evals.indices) {
                if (duplicateOf[i] == null && bestId[i] != null) {
                    duplicateOf[i] = "Similar (${(bestScore[i] * 100).toInt()}%) to ${bestId[i]}"
                }
            }
        }

        val libraryChapters = dao.chaptersOf(cfg.subject)
        return evals.mapIndexed { i, (base, ev) ->
            val status = DraftEvaluator.statusOf(ev.errors, duplicateOf[i], ev.hasAnswer, base.confidence)
            base.copy(
                errors = ev.errors.joinToString("\n"),
                duplicateOf = duplicateOf[i],
                status = status.name,
                selected = status != DraftStatus.INVALID && status != DraftStatus.DUPLICATE,
                suggestedChapter = ChapterSuggester.suggest(cfg.subject, base.statement, libraryChapters)?.chapter,
            )
        }
    }

    // ------------------------------------------------------------------------------------------
    // Review actions
    // ------------------------------------------------------------------------------------------

    suspend fun saveDraft(draft: PdfDraftEntity) {
        val job = dao.getJob(draft.jobId) ?: return
        val cfg = PdfJobConfig.decode(job.configJson)
        val ev = DraftEvaluator.evaluate(draft, cfg, job.fileName)
        val confidence = if (ev.errors.isEmpty() && ev.hasAnswer) maxOf(draft.confidence, 0.95) else draft.confidence
        val status = DraftEvaluator.statusOf(ev.errors, draft.duplicateOf, ev.hasAnswer, confidence)
        dao.updateDraft(
            draft.copy(
                errors = ev.errors.joinToString("\n"),
                confidence = confidence,
                status = status.name,
                edited = true,
                selected = ev.errors.isEmpty() && (draft.selected || draft.status == DraftStatus.INVALID.name),
            ),
        )
    }

    suspend fun setSelected(ids: List<Long>, selected: Boolean) {
        ids.chunked(500).forEach { dao.setSelected(it, selected) }
    }

    suspend fun deleteDrafts(ids: List<Long>) {
        ids.chunked(500).forEach { dao.deleteDraftsByIds(it) }
    }

    suspend fun setChapter(jobId: Long, ids: Set<Long>, chapter: String?) {
        val changed = dao.drafts(jobId).filter { it.id in ids }.map { it.copy(chapter = chapter?.takeIf { c -> c.isNotBlank() }, edited = true) }
        dao.updateDrafts(changed)
    }

    suspend fun applySuggestedChapters(jobId: Long, ids: Set<Long>): Int {
        val changed = dao.drafts(jobId)
            .filter { it.id in ids && it.chapter.isNullOrBlank() && !it.suggestedChapter.isNullOrBlank() }
            .map { it.copy(chapter = it.suggestedChapter, edited = true) }
        dao.updateDrafts(changed)
        return changed.size
    }

    // ------------------------------------------------------------------------------------------
    // Figures
    // ------------------------------------------------------------------------------------------

    /** Crops the question's region from its page and links the image. */
    suspend fun attachFigure(draftId: Long): FigureResult = withContext(Dispatchers.IO) {
        val draft = dao.draft(draftId) ?: return@withContext FigureResult(null, "Question not found")
        val job = dao.getJob(draft.jobId) ?: return@withContext FigureResult(null, "Job not found")
        val file = File(job.localPath)
        if (!file.exists()) return@withContext FigureResult(null, "The stored PDF file is missing")
        try {
            var savedPath: String? = null
            val page = dao.page(draft.jobId, draft.page)
            val lines = page?.linesJson?.let { AbhyasJson.json.decodeFromString(linesSerializer, it) }.orEmpty()
            val next = dao.drafts(draft.jobId).firstOrNull { it.position == draft.position + 1 && it.page == draft.page }
            val band = FigureRegion.band(lines, page?.pageHeight ?: 0f, draft.statement, next?.statement)
            PdfPageRenderer(file).use { r ->
                val rendered = r.render(draft.page, FIGURE_WIDTH_PX)
                val bmp = rendered.bitmap
                val crop = if (band != null) {
                    val top = (band.first * rendered.pxPerPoint).toInt().coerceIn(0, bmp.height - 2)
                    val bottom = (band.second * rendered.pxPerPoint).toInt().coerceIn(top + 1, bmp.height)
                    Bitmap.createBitmap(bmp, 0, top, bmp.width, bottom - top)
                } else bmp
                val dir = File(root, "${draft.jobId}/figs").apply { mkdirs() }
                draft.imagePath?.let { File(it).delete() }
                val out = File(dir, "q${draft.position + 1}_${clock()}.jpg")
                out.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (crop !== bmp) crop.recycle()
                bmp.recycle()
                dao.updateDraft(draft.copy(imagePath = out.absolutePath, edited = true))
                savedPath = out.absolutePath
            }
            FigureResult(savedPath, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FigureResult(null, "Could not extract the figure: ${e.message}")
        } catch (e: OutOfMemoryError) {
            FigureResult(null, "Not enough memory to render this page")
        }
    }

    suspend fun removeFigure(draftId: Long) {
        val d = dao.draft(draftId) ?: return
        d.imagePath?.let { File(it).delete() }
        dao.updateDraft(d.copy(imagePath = null, edited = true))
    }

    // ------------------------------------------------------------------------------------------
    // Import / export
    // ------------------------------------------------------------------------------------------

    private class ExportSet(val meta: ExportMeta, val questions: List<ExportQuestion>)

    private suspend fun exportSet(jobId: Long, bankId: String, title: String, version: Int): ExportSet? {
        val job = dao.getJob(jobId) ?: return null
        val cfg = PdfJobConfig.decode(job.configJson)
        val chosen = dao.drafts(jobId).filter { it.selected && it.errors.isBlank() }
        val questions = chosen.map { d ->
            val img = d.imagePath?.let { File(it) }?.takeIf { it.exists() }
            val name = img?.let { "q${d.position + 1}.jpg" }
            ExportQuestion(DraftEvaluator.toDto(d, cfg, job.fileName, imagePath = null), name, img?.let { f -> { f.readBytes() } })
        }
        val meta = ExportMeta(bankId, title, version, cfg.exam, cfg.classLevel, cfg.subject, cfg.kind)
        return ExportSet(meta, questions)
    }

    suspend fun importSelected(jobId: Long, bankId: String, title: String): ImportResult = withContext(Dispatchers.IO) {
        if (!Regex("^[A-Za-z0-9._-]{1,64}$").matches(bankId)) {
            return@withContext ImportResult.Failure("Bank id may only use letters, digits, '.', '_' and '-' (max 64)")
        }
        val version = (db.bankDao().getById(bankId)?.version ?: 0) + 1
        val set = exportSet(jobId, bankId, title.ifBlank { bankId }, version)
            ?: return@withContext ImportResult.Failure("Job not found")
        if (set.questions.isEmpty()) return@withContext ImportResult.Failure("No valid questions are selected")
        val built = PackageExporter.build(set.meta, set.questions)
        val stage = File(context.cacheDir, "pdfstage_$jobId").apply { deleteRecursively(); mkdirs() }
        try {
            File(stage, "images").mkdirs()
            for (q in set.questions) {
                val n = q.imageName ?: continue
                q.imageBytes?.invoke()?.let { File(stage, "images/$n").writeBytes(it) }
            }
            val result = bankImporter.install(built.manifestBytes, built.questionsBytes, stage)
            if (result is ImportResult.Success) {
                dao.setStatus(jobId, PdfJobStatus.IMPORTED, "Imported ${result.report.imported} questions into $bankId", clock())
            }
            result
        } finally {
            stage.deleteRecursively()
        }
    }

    /** Writes the selected, valid questions as an Abhyas bank package zip. Returns the question count. */
    suspend fun exportZip(jobId: Long, out: OutputStream, bankId: String, title: String): Int = withContext(Dispatchers.IO) {
        val set = exportSet(jobId, bankId, title.ifBlank { bankId }, 1) ?: return@withContext 0
        if (set.questions.isEmpty()) return@withContext 0
        PackageExporter.writeZip(out, set.meta, set.questions)
        set.questions.size
    }

    companion object {
        private const val OCR_WIDTH_PX = 1600
        private const val FIGURE_WIDTH_PX = 1400
    }
}
