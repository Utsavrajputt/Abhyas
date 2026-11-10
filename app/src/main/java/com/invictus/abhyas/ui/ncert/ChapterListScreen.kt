@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ncert

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.invictus.abhyas.data.pdf.BookCovers
import com.invictus.abhyas.domain.ncert.BookLayout
import com.invictus.abhyas.domain.ncert.BookNaming
import com.invictus.abhyas.ui.icons.AppIcons
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
    val context = LocalContext.current
    val cover by produceState<Bitmap?>(null, book?.id, book?.status) { value = book?.let { BookCovers.load(context, it) } }
    val label: (Int) -> String = { p -> layout?.printedLabel(p) ?: p.toString() }
    val b = book

    ExpressiveScreen(
        title = b?.let { BookNaming.shortTitle(it.title, it.subject) } ?: "Book",
        subtitle = b?.let { listOfNotNull(BookNaming.classLabel(it.classLevel).takeIf { _ -> it.classLevel != null }, it.subject).joinToString(" - ") } ?: "",
    ) {
        if (b == null) {
            BodyMuted("Book not found.")
            return@ExpressiveScreen
        }
        val pagesRead = read.size
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.width(84.dp).aspectRatio(0.72f)) {
                val bmp = cover
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), contentDescription = b.title, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                } else {
                    Box(contentAlignment = Alignment.Center) { Icon(AppIcons.MenuBook, contentDescription = null, modifier = Modifier.size(28.dp)) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                BodyMuted("${b.pageCount} pages - read $pagesRead")
                if (b.pageCount > 0) LinearWavyProgressIndicator(progress = { (pagesRead.toFloat() / b.pageCount).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (chapters.isEmpty()) {
            BodyMuted("Chapters appear when indexing finishes.")
            if (b.pageCount > 0) Button(onClick = onOpenFullBook, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Open full book") }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chapters.forEach { c ->
                    ChapterCard(c, label, read) { onOpenChapter(c.startPage, c.endPage) }
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(c: BookChapterEntity, label: (Int) -> String, read: Set<Int>, onClick: () -> Unit) {
    val total = (c.endPage - c.startPage + 1).coerceAtLeast(1)
    val done = read.count { it in c.startPage..c.endPage }
    Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            BodyMuted("Pages ${label(c.startPage)}-${label(c.endPage)} - read ${done * 100 / total}%")
            LinearWavyProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }
}
