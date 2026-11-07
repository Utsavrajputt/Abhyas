package com.invictus.abhyas.ui.ncert

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.ncert.BookRepository
import com.invictus.abhyas.domain.ncert.IndexEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun ncertFactory(app: AbhyasApp) = viewModelFactory { initializer { NcertViewModel(app) } }

data class BookRow(val book: BookEntity, val pagesRead: Int)

sealed interface IndexUi {
    data object Idle : IndexUi
    data object Loading : IndexUi
    data class Loaded(val entries: List<IndexEntry>, val problems: List<String>) : IndexUi
    data class Error(val message: String) : IndexUi
}

class NcertViewModel(private val app: AbhyasApp) : ViewModel() {
    private val repo = app.books

    /** null = loading. */
    val books: StateFlow<List<BookRow>?> = combine(repo.observeBooks(), repo.observeReadCounts()) { list, counts ->
        val byBook = counts.associate { it.bookId to it.n }
        list.map { BookRow(it, byBook[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message = MutableStateFlow<String?>(null)
    val index = MutableStateFlow<IndexUi>(IndexUi.Idle)
    val savedUrl: StateFlow<String> = app.settingsStore.settings.map { it.ncertIndexUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun addLocal(uri: Uri, title: String, classLevel: Int?, subject: String, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.addLocal(uri, title.ifBlank { null }, classLevel, subject)) {
                is BookRepository.AddResult.Added -> message.value = "Added. Indexing text in the background; you can already read it."
                is BookRepository.AddResult.Existing -> { message.value = "This book is already in your library."; onOpen(r.id) }
                is BookRepository.AddResult.Failed -> message.value = r.message
            }
        }
    }

    fun loadIndex(url: String) {
        val u = url.trim()
        index.value = IndexUi.Loading
        viewModelScope.launch {
            try {
                app.settingsStore.setNcertIndexUrl(u)
                val r = repo.fetchIndex(u)
                index.value = if (r.entries.isEmpty() && r.errors.isNotEmpty()) IndexUi.Error(r.errors.first()) else IndexUi.Loaded(r.entries, r.errors)
            } catch (e: IllegalArgumentException) {
                index.value = IndexUi.Error(e.message ?: "Invalid URL")
            } catch (e: Exception) {
                index.value = IndexUi.Error("Could not load the index: ${e.message}")
            }
        }
    }

    fun download(entry: IndexEntry) {
        viewModelScope.launch { message.value = repo.startDownload(entry) ?: "Download queued." }
    }

    fun delete(id: String) { viewModelScope.launch { repo.deleteBook(id) } }
    fun retry(id: String) { viewModelScope.launch { repo.retry(id) } }
    fun dismissMessage() { message.value = null }
    fun resetIndex() { index.value = IndexUi.Idle }
    suspend fun installedIds(): Set<String> = repo.observeBooks().first().map { it.id }.toSet()
}
