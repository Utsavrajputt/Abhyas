package com.invictus.abhyas.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.repo.ImportResult
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.pack.PackageFiles
import com.invictus.abhyas.domain.pack.PackageReadException
import com.invictus.abhyas.domain.query.QuestionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.data.repo.StartResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

fun appViewModelFactory(app: AbhyasApp) = viewModelFactory {
    initializer { LibraryViewModel(app) }
    initializer { BrowseViewModel(app) }
    initializer { com.invictus.abhyas.ui.pyq.PyqViewModel(app) }
    initializer { com.invictus.abhyas.ui.progress.ProgressViewModel(app) }
}

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Running : ImportUi
    data class Done(val title: String, val lines: List<String>) : ImportUi
}

class LibraryViewModel(private val app: AbhyasApp) : ViewModel() {
    /** null = still loading. */
    val banks: StateFlow<List<BankEntity>?> = app.database.bankDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val importState: StateFlow<ImportUi> = _import

    /** Debug builds only: demo packages shipped in src/debug/assets/demo. Empty in release. */
    val demoPackages: List<String> = app.packageReader.assetPackages("demo")

    fun importZip(uri: Uri) = startImport { stage -> app.packageReader.readZip(uri, stage) }
    fun importFolder(uri: Uri) = startImport { _ -> app.packageReader.readFolder(uri) }
    fun installDemo(dir: String) = startImport { _ -> app.packageReader.readAsset("demo/$dir") }

    fun dismiss() { _import.value = ImportUi.Idle }

    // ---- question papers (Library list) ----

    val filter = MutableStateFlow(PaperFilter())

    /** Installed papers with their coaching / type / subject and completed flag. null = still loading. */
    private val status = combine(
        app.settingsStore.paperAttempts,
        app.database.attemptDao().observeHistory(),
        app.database.attemptDao().observeInProgress(),
    ) { byAttempt, history, running ->
        PaperStatus(
            submitted = history.mapNotNull { byAttempt[it.id] }.toSet(),
            inProgress = running?.let { a -> byAttempt[a.id]?.let { mapOf(it to a.id) } } ?: emptyMap(),
        )
    }

    val papers: StateFlow<List<PaperRow>?> = combine(
        app.database.bankDao().observeAll(),
        app.settingsStore.paperMeta,
        app.settingsStore.completedPapers,
        status,
    ) { banks, meta, done, st -> PaperRows.build(banks, meta, done, st) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(f: PaperFilter) { filter.value = f }

    fun setCompleted(bankId: String, done: Boolean) {
        viewModelScope.launch { app.settingsStore.setPaperCompleted(bankId, done) }
    }

    /** Starts the whole paper in its original question order. */
    fun startPaper(row: PaperRow, onStarted: (Long) -> Unit) {
        if (_import.value == ImportUi.Running) return
        viewModelScope.launch {
            val ids = PaperRows.paperOrder(app.questionRepository.matchingIds(QuestionFilter(bankId = row.bank.id)))
            val questions = app.questionRepository.load(ids)
            val mode = if (row.type == PaperRows.TYPE_PYQ) PracticeMode.PYQ else PracticeMode.CUSTOM
            when (val r = app.attemptRepository.startFromQuestions(row.title, mode.name, questions)) {
                is StartResult.Started -> {
                    app.settingsStore.linkAttemptToPaper(r.attemptId, row.bank.id)
                    onStarted(r.attemptId)
                }
                is StartResult.Failed -> _import.value = ImportUi.Done("Cannot start", listOf(r.message))
            }
        }
    }

    /** Exports the paper (questions, then answer key) as a PDF in Downloads. */
    fun exportPdf(row: PaperRow) {
        if (_import.value == ImportUi.Running) return
        _import.value = ImportUi.Running
        viewModelScope.launch {
            _import.value = try {
                val ids = PaperRows.paperOrder(app.questionRepository.matchingIds(QuestionFilter(bankId = row.bank.id)))
                val saved = app.paperPdfExporter.export(row.title, app.questionRepository.load(ids))
                ImportUi.Done("PDF saved", listOf(saved.fileName, "Saved in ${saved.where}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportUi.Done("Could not save the PDF", listOf(e.message ?: "Unknown error"))
            }
        }
    }

    fun remove(bankId: String) {
        viewModelScope.launch { app.bankImporter.remove(bankId) }
    }

    private fun startImport(read: (java.io.File) -> PackageFiles) {
        if (_import.value == ImportUi.Running) return
        _import.value = ImportUi.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val stage = java.io.File(app.cacheDir, "pkgstage_${System.nanoTime()}")
                try {
                    val files = read(stage)
                    app.bankImporter.install(files.manifest, files.questions, stage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PackageReadException) {
                    ImportResult.Failure(e.message ?: "Unreadable package")
                } catch (e: Exception) {
                    ImportResult.Failure("Could not read the package: ${e.message}")
                } finally {
                    stage.deleteRecursively()
                }
            }
            _import.value = describe(result)
        }
    }

    private fun describe(r: ImportResult): ImportUi.Done = when (r) {
        is ImportResult.Failure -> ImportUi.Done("Import failed", listOf(r.message))
        is ImportResult.AlreadyInstalled ->
            ImportUi.Done("Already installed", listOf("${r.bankId} v${r.version} is already in your library."))
        is ImportResult.Success -> {
            val rep = r.report
            val lines = mutableListOf<String>()
            lines += "${rep.imported} questions imported"
            if (rep.replacedVersion != null) lines += "Updated from v${rep.replacedVersion} to v${rep.version}"
            if (rep.duplicatesSkipped > 0) lines += "${rep.duplicatesSkipped} duplicates skipped"
            if (rep.errors.isNotEmpty()) {
                lines += "${rep.errors.size} invalid questions skipped:"
                rep.errors.take(5).forEach { e ->
                    lines += "  #${e.index + 1}${e.id?.let { " ($it)" } ?: ""}: ${e.messages.first()}"
                }
                if (rep.errors.size > 5) lines += "  ... and ${rep.errors.size - 5} more"
            }
            ImportUi.Done("Import complete", lines)
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowseViewModel(app: AbhyasApp) : ViewModel() {
    private val repo = app.questionRepository
    private val dao = app.database.questionDao()

    val filter = MutableStateFlow(QuestionFilter())

    init {
        // Start from the app-wide exam (Settings > Exam); the "All" toggle still widens it.
        viewModelScope.launch {
            val exam = app.settingsStore.settings.first().exam
            filter.value = filter.value.copy(exam = exam)
        }
    }

    fun update(transform: (QuestionFilter) -> QuestionFilter) { filter.value = transform(filter.value) }

    val questions: Flow<PagingData<Question>> = filter
        .debounce(200)
        .distinctUntilChanged()
        .flatMapLatest { repo.paged(it) }
        .cachedIn(viewModelScope)

    val count: StateFlow<Int> = filter
        .flatMapLatest { repo.observeCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val subjects: StateFlow<List<String>> = dao.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chapters: StateFlow<List<String>> = filter.map { it.subject }.distinctUntilChanged()
        .flatMapLatest { dao.observeChapters(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topics: StateFlow<List<String>> = filter.map { it.subject to it.chapter }.distinctUntilChanged()
        .flatMapLatest { (s, c) -> dao.observeTopics(s, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val years: StateFlow<List<Int>> = dao.observeYears()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
