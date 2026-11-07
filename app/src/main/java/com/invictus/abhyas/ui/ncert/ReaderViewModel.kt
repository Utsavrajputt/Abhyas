@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.invictus.abhyas.ui.ncert

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BookBookmarkEntity
import com.invictus.abhyas.data.local.BookChapterEntity
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.PassageEntity
import com.invictus.abhyas.data.local.SavedPassageEntity
import com.invictus.abhyas.data.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

fun readerFactory(app: AbhyasApp, bookId: String) = viewModelFactory { initializer { ReaderViewModel(app, bookId) } }

class ReaderViewModel(app: AbhyasApp, private val bookId: String) : ViewModel() {
    private val repo = app.books

    val book: StateFlow<BookEntity?> = repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val chapters: StateFlow<List<BookChapterEntity>> = repo.observeChapters(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarks: StateFlow<List<BookBookmarkEntity>> = repo.observeBookmarks(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saved: StateFlow<List<SavedPassageEntity>> = repo.observeSaved(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val read: StateFlow<Set<Int>> = repo.observeRead(bookId).map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** (page, paragraph) currently highlighted on the page. */
    val highlight = MutableStateFlow<Pair<Int, Int>?>(null)
    val query = MutableStateFlow("")
    val results: StateFlow<List<PassageEntity>> = query.debounce(300).mapLatest { repo.search(bookId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var renderer: PdfPageRenderer? = null
    private val rendererLock = Mutex()
    private val aspects = ConcurrentHashMap<Int, Float>()
    private val cache = object : LruCache<Int, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    private suspend fun renderer(): PdfPageRenderer? = rendererLock.withLock {
        renderer ?: withContext(Dispatchers.IO) {
            repo.book(bookId)?.let { b -> runCatching { PdfPageRenderer(File(b.localPath)) }.getOrNull() }
        }?.also { renderer = it }
    }

    /** Width / height of a page. */
    suspend fun aspect(page: Int): Float {
        aspects[page]?.let { return it }
        val r = renderer() ?: return 0.72f
        val (w, h) = withContext(Dispatchers.IO) { runCatching { r.size(page) }.getOrDefault(0 to 0) }
        val a = if (w > 0 && h > 0) w.toFloat() / h else 0.72f
        aspects[page] = a
        return a
    }

    suspend fun render(page: Int, widthPx: Int): Bitmap? {
        cache.get(page)?.let { if (it.width in (widthPx - 2)..(widthPx + 2)) return it }
        val r = renderer() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { r.render(page, widthPx).bitmap }.getOrNull()?.also { cache.put(page, it) }
        }
    }

    suspend fun passages(page: Int): List<PassageEntity> = repo.passages(bookId, page)

    fun setBookmark(page: Int, on: Boolean) { viewModelScope.launch { repo.setBookmark(bookId, page, on) } }
    fun savePassage(p: PassageEntity) { viewModelScope.launch { repo.savePassage(p) } }
    fun deleteSaved(id: Long) { viewModelScope.launch { repo.deleteSaved(id) } }
    fun markRead(pages: List<Int>) { viewModelScope.launch { repo.markRead(bookId, pages) } }
    fun setLastPage(page: Int) { viewModelScope.launch { repo.setLastPage(bookId, page) } }

    override fun onCleared() {
        renderer?.close()
        renderer = null
        cache.evictAll()
    }
}
