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
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle

private val SHELF_ORDER = listOf("Physics", "Chemistry", "Biology", "Maths")

/** How many papers the Library shows before "View all" (which opens the filterable list). */
private const val PREVIEW_COUNT = 7

/**
 * Library: books shelf (thumbnails, per subject), then the newest papers (unfinished ones first).
 * Search and Packages are in the top bar; NCERT Connect is in the Create (+) menu.
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

    val examPapers = papers?.filter { it.bank.exam == exam.name }.orEmpty()
    val hasBooks = shelf.isNotEmpty()

    ExpressiveScreen(
        "Library",
        "${exam.name.replace('_', ' ')} books and papers, offline",
        topBarActions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBrowse) {
                Icon(AppIcons.Search, contentDescription = "Search questions")
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onOpenPyq) {
                Icon(AppIcons.Package, contentDescription = "Packages: download and import")
            }
        },
    ) {
        if (hasBooks) {
            SectionTitle("Books")
            val bySubject = shelf.groupBy { PaperRows.subjectGroup(it.subject) }
            val order = SHELF_ORDER.filter { it in bySubject } + bySubject.keys.filter { it !in SHELF_ORDER }.sorted()
            order.forEach { subject ->
                Text(subject, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BookShelf(books = bySubject.getValue(subject), onOpenBook = onOpenBook)
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
