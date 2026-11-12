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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChapterListViewModel(app: AbhyasApp, bookId: String) : ViewModel() {
    private val repo = app.books
    val book: StateFlow<BookEntity?> = repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val chapters: StateFlow<List<BookChapterEntity>> = repo.observeChapters(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val read: StateFlow<Set<Int>> = repo.observeRead(bookId).map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val layout = MutableStateFlow<BookLayout?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) { layout.value = repo.layout(bookId) }
    }
}

/** What opens when a book is tapped: its chapters by name, each opening the reader on just that chapter. */
@Composable
fun ChapterListScreen(
    app: AbhyasApp,
    bookId: String,
    onOpenChapter: (from: Int, to: Int) -> Unit,
    onOpenFullBook: () -> Unit,
) {
    val vm: ChapterListViewModel = viewModel(key = "chapters-$bookId", factory = viewModelFactory { initializer { ChapterListViewModel(app, bookId) } })
    val book by vm.book.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val read by vm.read.collectAsState()
    val layout by vm.layout.collectAsState()
    val label: (Int) -> String = { p -> layout?.displayLabel(p) ?: p.toString() }
    val b = book

    ExpressiveScreen(title = b?.let { BookNaming.shortTitle(it.title, it.subject) } ?: "Book", subtitle = "") {
        if (b == null) {
            BodyMuted("Book not found.")
            return@ExpressiveScreen
        }
        val pct = if (b.pageCount > 0) (read.size * 100 / b.pageCount).coerceIn(0, 100) else 0
        Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.width(92.dp).aspectRatio(0.72f)) {
                    BookCoverArt(b)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.classLevel?.let { InfoPill("Class $it") }
                        InfoPill(b.subject)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoPill("${b.pageCount} pages")
                        InfoPill("$pct% read", highlight = pct > 0)
                    }
                }
            }
        }

        if (chapters.isEmpty()) {
            BodyMuted("Chapters appear when indexing finishes.")
            if (b.pageCount > 0) FilledTonalButton(onClick = onOpenFullBook, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Open full book") }
        } else {
            Text("Chapters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chapters.forEach { c -> ChapterCard(badgeOf(c.title), c, label, read) { onOpenChapter(c.startPage, c.endPage) } }
            }
            FilledTonalButton(onClick = onOpenFullBook, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Read full book") }
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
