@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)

package com.invictus.abhyas.ui.ncert

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.mutableFloatStateOf
import com.invictus.abhyas.ui.components.AbhyasTopBar
import com.invictus.abhyas.ui.components.PillTabs
import com.invictus.abhyas.ui.components.BackNavigationIcon
import kotlin.math.abs
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
import androidx.compose.runtime.withFrameNanos
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
fun ReaderScreen(app: AbhyasApp, bookId: String, startPage: Int, startPara: Int, from: Int, to: Int, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(key = "reader-$bookId", factory = readerFactory(app, bookId))
    val book by vm.book.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val layout by vm.layout.collectAsState()
    val label: (Int) -> String = { p -> layout?.printedLabel(p) ?: p.toString() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var contents by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val aiReady by app.aiGateway.ready.collectAsState(initial = false)
    var initialized by remember { mutableStateOf(false) }
    val pageCount = book?.pageCount ?: 0
    val chapterList by vm.chapters.collectAsState()
    // from/to > 0 = a single chapter; 0 = the whole book. A jump to another chapter switches the range.
    var rangeFrom by remember { mutableStateOf(from) }
    var rangeTo by remember { mutableStateOf(to) }
    val inChapter = rangeFrom > 0 && rangeTo >= rangeFrom && pageCount > 0
    val first = if (inChapter) rangeFrom.coerceIn(1, pageCount) else 1
    val last = if (inChapter) rangeTo.coerceIn(first, pageCount) else pageCount
    val span = (last - first + 1).coerceAtLeast(0)
    val current = (first + listState.firstVisibleItemIndex).coerceIn(first, last.coerceAtLeast(first))

    suspend fun scrollToPassage(page: Int, para: Int) {
        val p = vm.passages(page).firstOrNull { it.idx == para } ?: return
        val size = snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page - first }?.size }.filterNotNull().first()
        listState.scrollBy((p.top / p.pageHeight * size - 96f).coerceAtLeast(0f))
    }

    fun jump(page: Int, para: Int = -1) {
        scope.launch {
            val target = page.coerceIn(1, pageCount.coerceAtLeast(1))
            if (inChapter && target !in first..last) {
                val ch = chapterList.firstOrNull { target in it.startPage..it.endPage }
                rangeFrom = ch?.startPage ?: 0
                rangeTo = ch?.endPage ?: 0
                // let the list recompose with the new range before scrolling
                withFrameNanos { }
                withFrameNanos { }
            }
            val newFirst = if (rangeFrom > 0) rangeFrom else 1
            listState.scrollToItem((target - newFirst).coerceAtLeast(0))
            if (para >= 0) scrollToPassage(target, para)
        }
    }

    LaunchedEffect(pageCount) {
        if (pageCount > 0 && !initialized) {
            initialized = true
            val b = book ?: return@LaunchedEffect
            val wanted = when {
                inChapter && startPage == from -> b.lastPage.takeIf { it in first..last } ?: first
                startPage > 0 -> startPage
                else -> b.lastPage
            }
            val page = wanted.coerceIn(first, last.coerceAtLeast(first))
            listState.scrollToItem(page - first)
            if (startPage > 0 && startPara >= 0) scrollToPassage(page, startPara)
        }
    }
    LaunchedEffect(pageCount, first) {
        if (pageCount > 0) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index + first } }.debounce(1200).collect { pages ->
                if (pages.isNotEmpty()) { vm.markRead(pages); vm.setLastPage(pages.first()) }
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AbhyasTopBar(
                title = {},
                navigationIcon = { BackNavigationIcon(onBack = onBack) },
                actions = {
                    if (aiReady) IconButton(onClick = { aiOpen = true }) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Ask AI about this page") }
                    IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search in book") }
                    val marked = bookmarks.any { it.page == current }
                    IconButton(onClick = { vm.setBookmark(current, !marked) }) {
                        Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = if (marked) "Remove bookmark" else "Bookmark page")
                    }
                    IconButton(onClick = { contents = true }) { Icon(Icons.Rounded.Menu, contentDescription = "Menu") }
                },
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val viewW = constraints.maxWidth
                // Zoomed pages are drawn sharper, but only after the pinch settles at a clearly larger size.
                val renderW = if (zoom > 1.4f) (viewW * 1.75f).toInt() else viewW
                if (pageCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BodyMuted(if (book == null) "Book not found." else "Nothing to show yet.") }
                } else {
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(viewW) {
                                val w = viewW.toFloat()
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = event.changes.count { it.pressed }
                                        if (pressed >= 2) {
                                            val z = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            val c = event.calculateCentroid()
                                            if (z != 1f || pan != Offset.Zero) {
                                                val old = zoom
                                                val new = (old * z).coerceIn(1f, 4f)
                                                val nx = c.x - (c.x - panX) * (new / old) + pan.x
                                                zoom = new
                                                panX = nx.coerceIn(w * (1f - new), 0f)
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        } else if (pressed == 1 && zoom > 1f) {
                                            val ch = event.changes.first { it.pressed }
                                            val d = ch.positionChange()
                                            if (d.x != 0f && abs(d.x) > abs(d.y)) {
                                                panX = (panX + d.x).coerceIn(w * (1f - zoom), 0f)
                                                ch.consume()
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(viewW) {
                                detectTapGestures(onDoubleTap = { o ->
                                    if (zoom > 1f) { zoom = 1f; panX = 0f } else { zoom = 2f; panX = (-o.x).coerceIn(viewW * (1f - 2f), 0f) }
                                })
                            },
                    ) {
                        LazyColumn(
                            Modifier
                                .layout { measurable, c ->
                                    val h = (c.maxHeight / zoom).toInt().coerceAtLeast(1)
                                    val p = measurable.measure(Constraints.fixed(c.maxWidth, h))
                                    layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                                }
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = panX
                                },
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(count = span, key = { first + it }) { i -> PageView(vm, first + i, renderW) }
                        }
                    }
                }
            }
        }
    }

    if (contents) ContentsSheet(vm, label, onDismiss = { contents = false }) { page, para -> contents = false; jump(page, para) }
    if (searching) SearchDialog(vm, label, onDismiss = { searching = false }) { page, para -> searching = false; jump(page, para) }
    if (aiOpen) {
        val page = current
        ReaderAiSheet(app, label(page), pageText = { vm.passages(page).joinToString("\n\n") { it.text } }, onDismiss = { aiOpen = false })
    }
}

@Composable
private fun PageView(vm: ReaderViewModel, page: Int, widthPx: Int) {
    val aspect by produceState(0.72f, page) { value = vm.aspect(page) }
    val bitmap by produceState<Bitmap?>(null, page, widthPx) { value = vm.render(page, widthPx) }
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.White)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Page $page", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

@Composable
private fun ContentsSheet(vm: ReaderViewModel, label: (Int) -> String, onDismiss: () -> Unit, onJump: (Int, Int) -> Unit) {
    val chapters by vm.chapters.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val read by vm.read.collectAsState()
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTabs(listOf("Chapters", "Bookmarks"), tab) { tab = it }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tab == 0) {
                    if (chapters.isEmpty()) item { BodyMuted("Chapters appear when indexing finishes.") }
                    items(chapters, key = { it.idx }) { c ->
                        val total = (c.endPage - c.startPage + 1).coerceAtLeast(1)
                        val done = read.count { it in c.startPage..c.endPage }
                        Column(Modifier.fillMaxWidth().clickable { onJump(c.startPage, -1) }.padding(vertical = 10.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            BodyMuted("Pages ${label(c.startPage)}-${label(c.endPage)} - ${done * 100 / total}% read")
                        }
                    }
                } else {
                    if (bookmarks.isEmpty()) item { BodyMuted("No bookmarks. Tap the bookmark icon while reading.") }
                    items(bookmarks, key = { it.id }) { b ->
                        Text("Page ${label(b.page)}", Modifier.fillMaxWidth().clickable { onJump(b.page, -1) }.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDialog(vm: ReaderViewModel, label: (Int) -> String, onDismiss: () -> Unit, onJump: (Int, Int) -> Unit) {
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
                            BodyMuted("Page ${label(p.page)}")
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
