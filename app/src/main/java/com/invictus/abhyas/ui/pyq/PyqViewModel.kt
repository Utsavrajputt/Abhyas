package com.invictus.abhyas.ui.pyq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.pyq.PyqEntry
import com.invictus.abhyas.data.pyq.PyqInstallResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    init { refresh() }

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
