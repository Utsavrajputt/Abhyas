@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.domain.ncert.BookNaming
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle

private val SHELF_ORDER = listOf("Physics", "Chemistry", "Biology", "Maths")

/** How many papers the Library shows before "View all" (which opens the filterable list). */
private const val PREVIEW_COUNT = 7

/**
 * Library: books shelf (covers, one row per class), then the newest papers (unfinished ones first).
 * Search and Packages (every download and import) are in the top bar.
 * Every section only shows when it has something in it.
 */
@Composable
fun LibraryScreen(
    app: AbhyasApp,
    onBrowse: () -> Unit,
    onViewAll: () -> Unit = {},
    onOpenPyq: () -> Unit = {},
    onOpenBook: (String) -> Unit = {},
    onOpenSession: (Long) -> Unit = {},
) {
    val vm: LibraryViewModel = viewModel(factory = appViewModelFactory(app))
    val papers by vm.papers.collectAsState()
    val importState by vm.importState.collectAsState()
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val exam = settings.exam
    val shelf by app.books.observeBooks().collectAsState(initial = emptyList())
    val chapterCounts by app.books.observeChapterCounts().collectAsState(initial = emptyMap())

    val examPapers = papers?.filter { it.bank.exam == exam.name }.orEmpty()
    val hasBooks = shelf.isNotEmpty()

    ExpressiveScreen(
        "Library",
        "",
        topBarActions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onOpenPyq) {
                Icon(AppIcons.Package, contentDescription = "Packages: download and import")
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBrowse) {
                Icon(AppIcons.Search, contentDescription = "Search questions")
            }
        },
    ) {
        if (hasBooks) {
            SectionTitle("Books")
            val byClass = shelf.groupBy { it.classLevel }
            val order: List<Int?> = byClass.keys.filterNotNull().sorted() + (if (byClass.containsKey(null)) listOf<Int?>(null) else emptyList())
            order.forEach { cls ->
                Text(BookNaming.classLabel(cls), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val books = byClass.getValue(cls).sortedWith(
                    compareBy<com.invictus.abhyas.data.local.BookEntity>(
                        { SHELF_ORDER.indexOf(PaperRows.subjectGroup(it.subject)).let { i -> if (i < 0) SHELF_ORDER.size else i } },
                        { BookNaming.shortTitle(it.title, it.subject) },
                    ),
                )
                BookShelf(books = books, chapterCounts = chapterCounts, onOpenBook = onOpenBook)
            }
        }

        when {
            papers == null || importState == ImportUi.Running -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator(modifier = Modifier.size(64.dp)) }

            examPapers.isEmpty() && !hasBooks -> EmptyLibrary(onOpenPyq)

            examPapers.isNotEmpty() -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Question papers") }
                    if (examPapers.size > PREVIEW_COUNT) {
                        TextButton(onClick = onViewAll) { Text("View all (${examPapers.size})") }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    examPapers.take(PREVIEW_COUNT).forEach { row ->
                        PaperCard(
                            row = row,
                            onStart = { vm.startPaper(row, onOpenSession) },
                            onResume = onOpenSession,
                            onSavePdf = { vm.exportPdf(row) },
                        )
                    }
                }
            }
        }
    }

    ImportDoneDialog(importState, vm::dismiss)
}

@Composable
private fun EmptyLibrary(onOpenPackages: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(MaterialShapes.Clover4Leaf.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        )
        Text("Nothing here yet", style = MaterialTheme.typography.titleLargeEmphasized)
        BodyMuted("Books and question papers you download or import show up here.")
        Button(onClick = onOpenPackages, shapes = ButtonDefaults.shapes()) {
            Icon(AppIcons.Package, contentDescription = null)
            Text("  Open Packages")
        }
    }
}
