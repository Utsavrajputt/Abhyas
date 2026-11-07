package com.invictus.abhyas.ui.pdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.PdfDraftEntity
import com.invictus.abhyas.data.local.PdfJobEntity
import com.invictus.abhyas.data.pdf.PdfJobConfig
import com.invictus.abhyas.data.pdf.PdfStudioRepository
import com.invictus.abhyas.data.repo.ImportResult
import com.invictus.abhyas.domain.pdf.DraftStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun pdfStudioFactory(app: AbhyasApp) = viewModelFactory { initializer { PdfStudioViewModel(app) } }
fun pdfReviewFactory(app: AbhyasApp, jobId: Long) = viewModelFactory { initializer { PdfReviewViewModel(app, jobId) } }

class PdfStudioViewModel(private val app: AbhyasApp) : ViewModel() {
    private val repo = app.pdfStudio

    /** null = loading. */
    val jobs: StateFlow<List<PdfJobEntity>?> = repo.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val adding = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    /** Returns the new job id through [onAdded] (so the UI can open it right away if it wants). */
    fun add(uri: Uri, config: PdfJobConfig) {
        if (adding.value) return
        adding.value = true
        viewModelScope.launch {
            when (val r = repo.addPdf(uri, config)) {
                is PdfStudioRepository.AddResult.Added -> message.value = null
                is PdfStudioRepository.AddResult.Failed -> message.value = r.message
            }
            adding.value = false
        }
    }

    fun cancel(id: Long) { viewModelScope.launch { repo.cancel(id) } }
    fun resume(id: Long) { viewModelScope.launch { repo.resume(id) } }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteJob(id) } }
    fun dismissMessage() { message.value = null }
}

enum class DraftFilter(val label: String) { ALL("All"), ATTENTION("Check"), OK("OK"), DUPLICATES("Dupes") }

data class ResultDialog(val title: String, val lines: List<String>)

class PdfReviewViewModel(private val app: AbhyasApp, val jobId: Long) : ViewModel() {
    private val repo = app.pdfStudio

    val job: StateFlow<PdfJobEntity?> = repo.observeJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val drafts: StateFlow<List<PdfDraftEntity>> = repo.observeDrafts(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filter = MutableStateFlow(DraftFilter.ALL)
    val busy = MutableStateFlow<String?>(null)
    val result = MutableStateFlow<ResultDialog?>(null)
    val chapters = MutableStateFlow<List<String>>(emptyList())

    /** Lowest confidence first; invalid ones on top. */
    val shown: StateFlow<List<PdfDraftEntity>> = combine(drafts, filter) { list, f ->
        val filtered = when (f) {
            DraftFilter.ALL -> list
            DraftFilter.ATTENTION -> list.filter { it.status in ATTENTION }
            DraftFilter.OK -> list.filter { it.status == DraftStatus.OK.name }
            DraftFilter.DUPLICATES -> list.filter { it.status == DraftStatus.DUPLICATE.name }
        }
        filtered.sortedWith(compareBy<PdfDraftEntity> { if (it.status == DraftStatus.INVALID.name) 0 else 1 }
            .thenBy { it.confidence }.thenBy { it.position })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val j = repo.observeJob(jobId).first { it != null }
            if (j != null) chapters.value = repo.chapters(PdfJobConfig.decode(j.configJson).subject)
        }
    }

    fun config(): PdfJobConfig = job.value?.let { PdfJobConfig.decode(it.configJson) } ?: PdfJobConfig()

    fun toggle(d: PdfDraftEntity) {
        if (d.errors.isNotBlank()) return
        viewModelScope.launch { repo.setSelected(listOf(d.id), !d.selected) }
    }

    fun selectIds(ids: List<Long>, selected: Boolean) { viewModelScope.launch { repo.setSelected(ids, selected) } }
    fun selectNone() = selectIds(drafts.value.filter { it.selected }.map { it.id }, false)
    fun selectImportable() = selectIds(drafts.value.filter { it.errors.isBlank() && !it.selected }.map { it.id }, true)
    fun selectOkOnly() {
        val all = drafts.value
        selectIds(all.filter { it.selected && it.status != DraftStatus.OK.name }.map { it.id }, false)
        selectIds(all.filter { !it.selected && it.status == DraftStatus.OK.name }.map { it.id }, true)
    }
    fun deselectDuplicates() = selectIds(drafts.value.filter { it.status == DraftStatus.DUPLICATE.name && it.selected }.map { it.id }, false)

    fun deleteSelected() {
        val ids = drafts.value.filter { it.selected }.map { it.id }
        viewModelScope.launch { repo.deleteDrafts(ids) }
    }

    fun setChapterForSelected(chapter: String?) {
        val ids = drafts.value.filter { it.selected }.map { it.id }.toSet()
        viewModelScope.launch { repo.setChapter(jobId, ids, chapter) }
    }

    fun applySuggestionsToSelected() {
        val ids = drafts.value.filter { it.selected }.map { it.id }.toSet()
        viewModelScope.launch {
            val n = repo.applySuggestedChapters(jobId, ids)
            result.value = ResultDialog("Suggested chapters", listOf(if (n == 0) "Nothing to apply: selected questions already have a chapter or have no suggestion." else "$n questions updated."))
        }
    }

    fun save(d: PdfDraftEntity) { viewModelScope.launch { repo.saveDraft(d) } }

    fun attachFigure(id: Long, onDone: (String?, String?) -> Unit) {
        viewModelScope.launch { val r = repo.attachFigure(id); onDone(r.path, r.error) }
    }

    fun removeFigure(id: Long) { viewModelScope.launch { repo.removeFigure(id) } }

    fun attachFlaggedFigures() {
        if (busy.value != null) return
        val targets = drafts.value.filter { it.selected && it.hasFigureHint && it.imagePath == null }
        if (targets.isEmpty()) {
            result.value = ResultDialog("Figures", listOf("No selected question is flagged as needing a figure."))
            return
        }
        viewModelScope.launch {
            var done = 0
            var failed = 0
            for ((i, d) in targets.withIndex()) {
                busy.value = "Extracting figures ${i + 1}/${targets.size}"
                if (repo.attachFigure(d.id).error == null) done++ else failed++
            }
            busy.value = null
            result.value = ResultDialog("Figures", listOf("$done figures attached" + if (failed > 0) ", $failed failed" else ""))
        }
    }

    fun reparse(config: PdfJobConfig) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = "Parsing again"
            repo.reparse(jobId, config)
            busy.value = null
        }
    }

    fun import(bankId: String, title: String) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = "Importing"
            val r = repo.importSelected(jobId, bankId.trim(), title.trim())
            busy.value = null
            result.value = when (r) {
                is ImportResult.Failure -> ResultDialog("Import failed", listOf(r.message))
                is ImportResult.AlreadyInstalled -> ResultDialog("Already installed", listOf("${r.bankId} v${r.version} is already in your library."))
                is ImportResult.Success -> {
                    val rep = r.report
                    val lines = mutableListOf("${rep.imported} questions imported into ${rep.bankId}")
                    if (rep.replacedVersion != null) lines += "Updated from v${rep.replacedVersion} to v${rep.version}"
                    if (rep.duplicatesSkipped > 0) lines += "${rep.duplicatesSkipped} duplicates skipped"
                    if (rep.errors.isNotEmpty()) lines += "${rep.errors.size} invalid questions skipped"
                    ResultDialog("Import complete", lines)
                }
            }
        }
    }

    fun export(uri: Uri, bankId: String, title: String) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = "Exporting"
            val count = try {
                withContext(Dispatchers.IO) {
                    val out = app.contentResolver.openOutputStream(uri) ?: return@withContext -1
                    repo.exportZip(jobId, out, bankId.trim().ifBlank { "export" }, title.trim())
                }
            } catch (e: Exception) {
                -2
            }
            busy.value = null
            result.value = when {
                count > 0 -> ResultDialog("Exported", listOf("$count questions saved as an Abhyas bank package.", "Import it from Library, or host it as an update."))
                count == 0 -> ResultDialog("Nothing exported", listOf("Select at least one valid question."))
                else -> ResultDialog("Export failed", listOf("Could not write the file."))
            }
        }
    }

    fun dismissResult() { result.value = null }

    private companion object {
        val ATTENTION = setOf(DraftStatus.INVALID.name, DraftStatus.MISSING_ANSWER.name, DraftStatus.LOW_CONFIDENCE.name)
    }
}
