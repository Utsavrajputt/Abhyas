package com.invictus.abhyas.ui.pyq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import android.net.Uri
import com.invictus.abhyas.data.ncert.BookRepository
import com.invictus.abhyas.data.pyq.FoundLink
import com.invictus.abhyas.data.pyq.GitHubLinks
import com.invictus.abhyas.data.pyq.IndexLink
import com.invictus.abhyas.data.pyq.PaperLink
import com.invictus.abhyas.data.pyq.PendingPaper
import com.invictus.abhyas.data.pyq.PackageEntry
import com.invictus.abhyas.data.pyq.PackageType
import com.invictus.abhyas.data.pyq.PaperMeta
import com.invictus.abhyas.data.pyq.PyqInstallResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.invictus.abhyas.domain.ncert.IndexEntry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScanUi {
    data object Idle : ScanUi
    data object Loading : ScanUi
    data class Error(val message: String) : ScanUi
    data class Found(val items: List<FoundLink>) : ScanUi
}

sealed interface SourceState {
    data object Loading : SourceState
    data class Error(val message: String) : SourceState
    data class Loaded(val name: String?, val entries: List<PackageEntry>, val skipped: Int) : SourceState
}

/** One online source: the NCERT index, the PYQ index, or a link added in Settings. */
data class SourceUi(val key: String, val label: String, val url: String, val state: SourceState)

class PyqViewModel(private val app: AbhyasApp) : ViewModel() {
    private val repo = app.pyqRepository

    val sources = MutableStateFlow<List<SourceUi>>(emptyList())
    /** Pack ids being downloaded right now. */
    val installing = MutableStateFlow<Set<String>>(emptySet())
    val message = MutableStateFlow<String?>(null)

    val banks: StateFlow<List<BankEntity>> = app.database.bankDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Links the user added themselves (any https source they are allowed to download from). */
    val links: StateFlow<List<PaperLink>> = app.settingsStore.paperLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    val scan = MutableStateFlow<ScanUi>(ScanUi.Idle)

    fun scanPage(url: String) {
        val u = url.trim()
        if (!u.startsWith("https://")) { scan.value = ScanUi.Error("The page link must start with https://"); return }
        scan.value = ScanUi.Loading
        viewModelScope.launch {
            scan.value = try {
                val found = repo.scanPage(u)
                if (found.isEmpty()) {
                    ScanUi.Error("No .pdf or .zip links found in the page source. It may need a login or build its links with JavaScript.")
                } else ScanUi.Found(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanUi.Error("Could not read the page: ${e.message}")
            }
        }
    }

    fun resetScan() { scan.value = ScanUi.Idle }

    /** Saves the chosen found files as links (skipping ones already saved). */
    fun addFound(items: List<FoundLink>) {
        val have = links.value.map { it.url }.toSet()
        val added = items.filter { it.url !in have }.map {
            PaperLink(java.util.UUID.randomUUID().toString(), it.title, null, it.url, it.type)
        }
        if (added.isNotEmpty()) viewModelScope.launch { app.settingsStore.setPaperLinks(links.value + added) }
        message.value = "${added.size} link(s) added" + if (added.size < items.size) " (${items.size - added.size} already saved)" else ""
    }

    /** Returns an error message, or null when the link was saved. */
    fun addLink(title: String, yearText: String, url: String, type: String): String? {
        val u = url.trim()
        if (!u.startsWith("https://") || u.length < 12) return "The link must start with https://"
        val t = title.trim().ifEmpty { "Paper" }
        val year = yearText.trim().toIntOrNull()?.takeIf { it in 1990..2100 }
        val link = PaperLink(java.util.UUID.randomUUID().toString(), t, year, u, type)
        viewModelScope.launch { app.settingsStore.setPaperLinks(links.value + link) }
        return null
    }

    fun removeLink(id: String) {
        viewModelScope.launch { app.settingsStore.setPaperLinks(links.value.filter { it.id != id }) }
    }

    /**
     * PDF link: download it, then hand it to PDF Studio ([onPdfReady] opens that screen).
     * Pack link: download and install the question bank right away.
     */
    fun fetchLink(link: PaperLink, onPdfReady: () -> Unit) {
        if (link.id in installing.value) return
        installing.value = installing.value + link.id
        viewModelScope.launch {
            try {
                if (link.type == PaperLink.TYPE_PACK) {
                    message.value = when (val r = repo.installPack(link)) {
                        is PyqInstallResult.Installed -> "${link.title}: ${r.message}"
                        is PyqInstallResult.Failed -> "${link.title} failed: ${r.message}"
                    }
                } else {
                    val file = repo.downloadPaperPdf(link)
                    app.pendingPaper.value = PendingPaper(
                        uri = Uri.fromFile(file),
                        year = link.year,
                        title = link.title,
                        bankId = "pyq-" + link.id.take(8),
                    )
                    onPdfReady()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "${link.title} failed: ${e.message}"
            } finally {
                installing.value = installing.value - link.id
            }
        }
    }

    /** Older versions kept an NCERT index and a PYQ index in Settings; they are now ordinary links. */
    private suspend fun migrateLegacyUrls() {
        val st = app.settingsStore.settings.first()
        val legacy = listOf(st.ncertIndexUrl, st.pyqIndexUrl).map { it.trim() }.filter { it.startsWith("https://") }
        if (legacy.isEmpty()) return
        var links = app.settingsStore.indexLinks.first()
        legacy.forEach { raw ->
            val url = GitHubLinks.normalize(raw)
            if (links.none { it.url == url }) {
                val name = Regex("^https://github\\.com/([^/]+/[^/?#]+)").find(raw)?.groupValues?.get(1)
                    ?: raw.removePrefix("https://").substringBefore('/')
                links = links + IndexLink(java.util.UUID.randomUUID().toString(), name, url)
            }
        }
        app.settingsStore.setIndexLinks(links)
        app.settingsStore.setNcertIndexUrl("")
        app.settingsStore.setPyqIndexUrl("")
    }

    /** Loads every source link (books and papers alike) at the same time. */
    fun refresh() {
        viewModelScope.launch {
            migrateLegacyUrls()
            val extra = app.settingsStore.indexLinks.first()
            val list = extra.map { SourceUi(it.id, it.name, it.url, SourceState.Loading) }.distinctBy { it.url }
            sources.value = list
            list.forEach { src ->
                launch {
                    val result = loadSource(src.url)
                    sources.update { cur -> cur.map { if (it.key == src.key) it.copy(state = result) else it } }
                }
            }
        }
    }

    /** Adds a PDF from the phone as a textbook. */
    fun addLocalBook(uri: Uri, title: String, classLevel: Int?, subject: String) {
        viewModelScope.launch {
            message.value = when (val r = app.books.addLocal(uri, title.ifBlank { null }, classLevel, subject)) {
                is BookRepository.AddResult.Added -> "Added. Indexing text in the background; you can already read it."
                is BookRepository.AddResult.Existing -> "This book is already in your library."
                is BookRepository.AddResult.Failed -> r.message
            }
        }
    }

    private suspend fun loadSource(url: String): SourceState = try {
        val parsed = repo.fetchPackageIndex(url)
        if (parsed.entries.isEmpty() && parsed.errors.isNotEmpty()) SourceState.Error(parsed.errors.first())
        else SourceState.Loaded(parsed.name, parsed.entries, parsed.errors.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        SourceState.Error(e.message ?: "Invalid link")
    } catch (e: Exception) {
        SourceState.Error("Could not load: ${e.message}")
    }

    /** Papers install as question banks (and remember coaching / subject / kind); books download in the background. */
    fun download(entry: PackageEntry) {
        if (entry.id in installing.value) return
        viewModelScope.launch { installOne(entry) }
    }

    /** Downloads everything in [entries] that is not installed yet, one after another. */
    fun downloadAll(entries: List<PackageEntry>) {
        val todo = entries.filter { it.id !in installing.value }
        if (todo.isEmpty()) return
        viewModelScope.launch { todo.forEach { installOne(it) } }
    }

    private suspend fun installOne(entry: PackageEntry) {
        installing.value = installing.value + entry.id
        try {
            message.value = when (entry.type) {
                PackageType.BOOK -> {
                    val err = app.books.startDownload(
                        IndexEntry(
                            id = entry.id, title = entry.name, classLevel = entry.classLevel,
                            subject = entry.subject ?: "Other", url = entry.url, sha256 = entry.sha256.orEmpty(),
                            sizeBytes = entry.sizeBytes, note = entry.description.ifBlank { null },
                        ),
                    )
                    if (err == null) "${entry.name}: downloading in the background" else "${entry.name}: $err"
                }
                PackageType.PAPER -> when (val r = repo.installPackage(entry)) {
                    is PyqInstallResult.Installed -> {
                        val bankId = r.bankId ?: entry.id
                        app.settingsStore.putPaperMeta(
                            bankId,
                            PaperMeta(name = entry.name, coaching = entry.coaching, subject = entry.subject, kind = entry.kind, year = entry.year),
                        )
                        "${entry.name}: ${r.message}"
                    }
                    is PyqInstallResult.Failed -> "${entry.name} failed: ${r.message}"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message.value = "${entry.name} failed: ${e.message}"
        } finally {
            installing.value = installing.value - entry.id
        }
    }

    fun removeBank(id: String) {
        viewModelScope.launch { app.bankImporter.remove(id) }
    }

    fun removeBook(id: String) {
        viewModelScope.launch { app.books.deleteBook(id) }
    }

    fun retryBook(id: String) {
        viewModelScope.launch { app.books.retry(id) }
    }

    fun dismissMessage() { message.value = null }
}
