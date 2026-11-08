package com.invictus.abhyas.ui.pyq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import android.net.Uri
import com.invictus.abhyas.data.pyq.FoundLink
import com.invictus.abhyas.data.pyq.PaperLink
import com.invictus.abhyas.data.pyq.PendingPaper
import com.invictus.abhyas.data.pyq.PyqEntry
import com.invictus.abhyas.data.pyq.PyqInstallResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScanUi {
    data object Idle : ScanUi
    data object Loading : ScanUi
    data class Error(val message: String) : ScanUi
    data class Found(val items: List<FoundLink>) : ScanUi
}

sealed interface PyqIndexUi {
    data object NoUrl : PyqIndexUi
    data object Loading : PyqIndexUi
    data class Error(val message: String) : PyqIndexUi
    data class Loaded(val entries: List<PyqEntry>) : PyqIndexUi
}

class PyqViewModel(private val app: AbhyasApp) : ViewModel() {
    private val repo = app.pyqRepository

    val index = MutableStateFlow<PyqIndexUi>(PyqIndexUi.Loading)
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

    fun refresh() {
        index.value = PyqIndexUi.Loading
        viewModelScope.launch {
            val url = app.settingsStore.settings.first().pyqIndexUrl.trim()
            if (url.isEmpty()) {
                index.value = PyqIndexUi.NoUrl
                return@launch
            }
            index.value = try {
                PyqIndexUi.Loaded(repo.fetchIndex(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                PyqIndexUi.Error(e.message ?: "Invalid URL")
            } catch (e: Exception) {
                PyqIndexUi.Error("Could not load the list: ${e.message}")
            }
        }
    }

    fun download(entry: PyqEntry) {
        if (entry.id in installing.value) return
        viewModelScope.launch { installOne(entry) }
    }

    /** Downloads every listed pack that is not installed yet, one after another. */
    fun downloadAll(entries: List<PyqEntry>, installedIds: Set<String>) {
        val todo = entries.filter { it.id !in installedIds && it.id !in installing.value }
        if (todo.isEmpty()) return
        viewModelScope.launch { todo.forEach { installOne(it) } }
    }

    private suspend fun installOne(entry: PyqEntry) {
        installing.value = installing.value + entry.id
        val r = repo.install(entry)
        installing.value = installing.value - entry.id
        message.value = when (r) {
            is PyqInstallResult.Installed -> "${entry.title}: ${r.message}"
            is PyqInstallResult.Failed -> "${entry.title} failed: ${r.message}"
        }
    }

    fun dismissMessage() { message.value = null }
}
