@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)

package com.invictus.abhyas.ui.ncert

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.PassageEntity
import com.invictus.abhyas.domain.ai.AiFeature
import com.invictus.abhyas.ui.ai.AiRequestUi
import com.invictus.abhyas.ui.ai.AiSheet
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(app: AbhyasApp, bookId: String, startPage: Int, startPara: Int, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(key = "reader-$bookId", factory = readerFactory(app, bookId))
    val book by vm.book.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val highlight by vm.highlight.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var contents by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<PassageEntity?>(null) }
    var aiRequest by remember { mutableStateOf<AiRequestUi?>(null) }
    var aiMenu by remember { mutableStateOf(false) }
    val aiReady by app.aiGateway.ready.collectAsState(initial = false)
    var initialized by remember { mutableStateOf(false) }
    val pageCount = book?.pageCount ?: 0
    val current = (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount.coerceAtLeast(1))

    suspend fun scrollToPassage(page: Int, para: Int) {
        val p = vm.passages(page).firstOrNull { it.idx == para } ?: return
        val size = snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page - 1 }?.size }.filterNotNull().first()
        listState.scrollBy((p.top / p.pageHeight * size - 96f).coerceAtLeast(0f))
    }

    fun jump(page: Int, para: Int = -1) {
        scope.launch {
            listState.scrollToItem((page - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            if (para >= 0) { vm.highlight.value = page to para; scrollToPassage(page, para) }
        }
    }

    LaunchedEffect(pageCount) {
        if (pageCount > 0 && !initialized) {
            initialized = true
            val b = book ?: return@LaunchedEffect
            val page = (if (startPage > 0) startPage else b.lastPage).coerceIn(1, pageCount)
            listState.scrollToItem(page - 1)
            if (startPage > 0 && startPara >= 0) { vm.highlight.value = page to startPara; scrollToPassage(page, startPara) }
        }
    }
    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index + 1 } }.debounce(1200).collect { pages ->
                if (pages.isNotEmpty()) { vm.markRead(pages); vm.setLastPage(pages.first()) }
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Back") }
                Column(Modifier.weight(1f)) {
                    Text(book?.title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BodyMuted("Page $current of $pageCount")
                }
                if (aiReady) {
                    Box {
                        IconButton(onClick = { aiMenu = true }) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI tools for this page") }
                        DropdownMenu(expanded = aiMenu, onDismissRequest = { aiMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Notes for this page") },
                                onClick = {
                                    aiMenu = false
                                    scope.launch {
                                        val text = vm.passages(current).joinToString("\n\n") { it.text }
                                        aiRequest = AiRequestUi(AiFeature.NOTES, text, "Page $current")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Ask about this page") },
                                onClick = {
                                    aiMenu = false
                                    scope.launch {
                                        val text = vm.passages(current).joinToString("\n\n") { it.text }
                                        aiRequest = AiRequestUi(AiFeature.ASK, text, "Page $current")
                                    }
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search in book") }
                val marked = bookmarks.any { it.page == current }
                IconButton(onClick = { vm.setBookmark(current, !marked) }) {
                    Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = if (marked) "Remove bookmark" else "Bookmark page")
                }
                TextButton(onClick = { contents = true }, shapes = ButtonDefaults.shapes()) { Text("Contents") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val widthPx = constraints.maxWidth
                if (pageCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BodyMuted(if (book == null) "Book not found." else "Nothing to show yet.") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(count = pageCount, key = { it }) { i -> PageView(vm, i + 1, widthPx, highlight) { picked = it } }
                    }
                }
            }
        }
    }

    if (contents) ContentsSheet(vm, onDismiss = { contents = false }) { page, para -> contents = false; jump(page, para) }
    if (searching) SearchDialog(vm, onDismiss = { searching = false }) { page, para -> searching = false; jump(page, para) }
    aiRequest?.let { r -> AiSheet(app, r, onDismiss = { aiRequest = null }) }
    picked?.let { p ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("Page ${p.page}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.text, style = MaterialTheme.typography.bodyMedium)
                    if (aiReady) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { aiRequest = AiRequestUi(AiFeature.EXPLAIN, p.text, "Page ${p.page}"); picked = null }) { Text("Explain") }
                            TextButton(onClick = { aiRequest = AiRequestUi(AiFeature.SIMPLIFY, p.text, "Page ${p.page}"); picked = null }) { Text("Simplify") }
                            TextButton(onClick = { aiRequest = AiRequestUi(AiFeature.ASK, p.text, "Page ${p.page}"); picked = null }) { Text("Ask") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.savePassage(p); picked = null }) { Text("Save passage") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.highlight.value = p.page to p.idx; picked = null }) { Text("Highlight") }
                    TextButton(onClick = { picked = null }) { Text("Close") }
                }
            },
        )
    }
}

@Composable
private fun PageView(vm: ReaderViewModel, page: Int, widthPx: Int, highlight: Pair<Int, Int>?, onPick: (PassageEntity) -> Unit) {
    val aspect by produceState(0.72f, page) { value = vm.aspect(page) }
    val bitmap by produceState<Bitmap?>(null, page, widthPx) { value = vm.render(page, widthPx) }
    val passages by produceState(emptyList<PassageEntity>(), page) { value = vm.passages(page) }
    val hl = highlight?.takeIf { it.first == page }?.second
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.White)
            .pointerInput(passages) {
                detectTapGestures { off ->
                    val frac = off.y / size.height
                    passages.firstOrNull { frac * it.pageHeight in (it.top - 6f)..(it.bottom + 6f) }?.let(onPick)
                }
            }
            .drawWithContent {
                drawContent()
                if (hl != null) passages.firstOrNull { it.idx == hl }?.let { p ->
                    drawRect(
                        color,
                        topLeft = Offset(0f, p.top / p.pageHeight * size.height),
                        size = Size(size.width, (p.bottom - p.top) / p.pageHeight * size.height),
                    )
                }
            },
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Page $page", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

@Composable
private fun ContentsSheet(vm: ReaderViewModel, onDismiss: () -> Unit, onJump: (Int, Int) -> Unit) {
    val chapters by vm.chapters.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val saved by vm.saved.collectAsState()
    val read by vm.read.collectAsState()
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectedToggleGroup(listOf("Chapters", "Bookmarks", "Saved"), tab) { tab = it }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (tab) {
                    0 -> {
                        if (chapters.isEmpty()) item { BodyMuted("Chapters appear when indexing finishes.") }
                        items(chapters, key = { it.idx }) { c ->
                            val total = (c.endPage - c.startPage + 1).coerceAtLeast(1)
                            val done = read.count { it in c.startPage..c.endPage }
                            Column(Modifier.fillMaxWidth().clickable { onJump(c.startPage, -1) }.padding(vertical = 8.dp)) {
                                Text(c.title, style = MaterialTheme.typography.titleSmall)
                                BodyMuted("Pages ${c.startPage}-${c.endPage}, read ${done * 100 / total}%")
                                LinearWavyProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    1 -> {
                        if (bookmarks.isEmpty()) item { BodyMuted("No bookmarks. Tap the bookmark icon while reading.") }
                        items(bookmarks, key = { it.id }) { b ->
                            Text("Page ${b.page}", Modifier.fillMaxWidth().clickable { onJump(b.page, -1) }.padding(vertical = 12.dp))
                        }
                    }
                    else -> {
                        if (saved.isEmpty()) item { BodyMuted("No saved passages. Tap a paragraph on a page to save it.") }
                        items(saved, key = { it.id }) { s ->
                            Row(Modifier.fillMaxWidth().clickable { onJump(s.page, s.paragraph) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    BodyMuted("Page ${s.page}")
                                    Text(s.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = { vm.deleteSaved(s.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDialog(vm: ReaderViewModel, onDismiss: () -> Unit, onJump: (Int, Int) -> Unit) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { vm.query.value = it }, label = { Text("Search this book") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Close") }
                }
                if (query.trim().length < 2) BodyMuted("Type at least 2 characters.")
                else if (results.isEmpty()) BodyMuted("No matches (scanned books have no searchable text).")
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(results, key = { "${it.page}-${it.idx}" }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { onJump(p.page, p.idx) }.padding(vertical = 8.dp)) {
                            BodyMuted("Page ${p.page}")
                            Text(snippet(p.text, query.trim()), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun snippet(text: String, q: String): String {
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0) return text.take(160)
    val from = (i - 40).coerceAtLeast(0)
    val to = (i + q.length + 100).coerceAtMost(text.length)
    return (if (from > 0) "..." else "") + text.substring(from, to) + (if (to < text.length) "..." else "")
}
