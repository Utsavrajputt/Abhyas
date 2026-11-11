@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ncert

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BookChapterEntity
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.ui.components.InfoPill
import com.invictus.abhyas.ui.library.BookCoverArt
import com.invictus.abhyas.domain.ncert.BookLayout
import com.invictus.abhyas.domain.ncert.BookNaming
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.theme.AbhyasShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The tapped book plus the other parts of the same class and subject, so chapters run on from Part 1 into Part 2. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChapterListViewModel(app: AbhyasApp, bookId: String) : ViewModel() {
    private val repo = app.books

    val books: StateFlow<List<BookEntity>> = combine(repo.observeBook(bookId), repo.observeBooks()) { lead, all ->
        if (lead == null) emptyList()
        else all.filter { it.subject == lead.subject && it.classLevel == lead.classLevel }.sortedBy { BookNaming.part(it.title) ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Chapters of every part, in reading order, each tagged with its book. */
    val chapters: StateFlow<List<Pair<BookEntity, BookChapterEntity>>> = books.flatMapLatest { bs ->
        if (bs.isEmpty()) flowOf(emptyList())
        else combine(bs.map { b -> repo.observeChapters(b.id).map { list -> list.map { b to it } } }) { arr -> arr.toList().flatten() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val read: StateFlow<Map<String, Set<Int>>> = books.flatMapLatest { bs ->
        if (bs.isEmpty()) flowOf(emptyMap())
        else combine(bs.map { b -> repo.observeRead(b.id).map { b.id to it.toSet() } }) { arr -> arr.toMap() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val layouts = MutableStateFlow<Map<String, BookLayout?>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            books.collect { bs ->
                val missing = bs.filter { it.id !in layouts.value }
                if (missing.isNotEmpty()) layouts.value = layouts.value + missing.associate { it.id to repo.layout(it.id) }
            }
        }
    }
}

/** What opens when a book is tapped: its chapters by name, each opening the reader on just that chapter. */
@Composable
fun ChapterListScreen(
    app: AbhyasApp,
    bookId: String,
    onOpenChapter: (bookId: String, from: Int, to: Int) -> Unit,
    onOpenFullBook: (bookId: String) -> Unit,
) {
    val vm: ChapterListViewModel = viewModel(key = "chapters-$bookId", factory = viewModelFactory { initializer { ChapterListViewModel(app, bookId) } })
    val books by vm.books.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val read by vm.read.collectAsState()
    val layouts by vm.layouts.collectAsState()
    val b = books.firstOrNull()

    ExpressiveScreen(title = b?.let { BookNaming.baseTitle(it.title, it.subject) } ?: "Book", subtitle = "") {
        if (b == null) {
            BodyMuted("Book not found.")
            return@ExpressiveScreen
        }
        val totalPages = books.sumOf { it.pageCount }
        val readPages = read.values.sumOf { it.size }
        val pct = if (totalPages > 0) (readPages * 100 / totalPages).coerceIn(0, 100) else 0
        val numbered = chapters.count { it.second.title.startsWith("Ch") }
        Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.width(92.dp).aspectRatio(0.72f)) {
                    BookCoverArt(b)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(BookNaming.baseTitle(b.title, b.subject), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.classLevel?.let { InfoPill("Class $it") }
                        InfoPill(b.subject)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (numbered > 0) InfoPill("$numbered chapters")
                        InfoPill("$pct% read", highlight = pct > 0)
                    }
                }
            }
        }

        if (chapters.isEmpty()) {
            BodyMuted("Chapters appear when indexing finishes.")
            if (b.pageCount > 0) FilledTonalButton(onClick = { onOpenFullBook(b.id) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Open full book") }
        } else {
            Text("Chapters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chapters.forEach { (book, c) ->
                    val label: (Int) -> String = { p -> layouts[book.id]?.displayLabel(p) ?: p.toString() }
                    ChapterCard(badgeOf(c.title), c, label, read[book.id].orEmpty()) { onOpenChapter(book.id, c.startPage, c.endPage) }
                }
            }
            FilledTonalButton(onClick = { onOpenFullBook(b.id) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Read full book") }
        }
    }
}

@Composable
private fun ChapterCard(badge: String, c: BookChapterEntity, label: (Int) -> String, read: Set<Int>, onClick: () -> Unit) {
    val total = (c.endPage - c.startPage + 1).coerceAtLeast(1)
    val done = read.count { it in c.startPage..c.endPage }
    val frac = (done.toFloat() / total).coerceIn(0f, 1f)
    Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clip(AbhyasShapes.M).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxSize(), strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                Text(badge, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Pages ${label(c.startPage)}-${label(c.endPage)}" + if (done > 0) " - ${frac.times(100).toInt()}% read" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Ch 5: Nuclei" -> "5"; Prelims, Appendices and Answers get their first letter. */
private fun badgeOf(title: String): String =
    Regex("""^Ch\s*(\d+)""").find(title)?.groupValues?.get(1) ?: title.take(1).uppercase()
