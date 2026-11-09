@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle
import com.invictus.abhyas.ui.theme.AbhyasShapes

private val SHELF_ORDER = listOf("Physics", "Chemistry", "Biology", "Maths")

/**
 * Library: the books shelf (per subject), then the installed question papers with Start / save-as-PDF /
 * mark-as-completed on every row. Importing and downloading live in the Packages screen (package icon).
 * Every section only shows when it has something in it.
 */
@Composable
fun LibraryScreen(app: AbhyasApp, onBrowse: () -> Unit, onOpenNcert: () -> Unit = {}, onOpenPyq: () -> Unit = {}, onOpenBook: (String) -> Unit = {}, onOpenSession: (Long) -> Unit = {}) {
    val vm: LibraryViewModel = viewModel(factory = appViewModelFactory(app))
    val papers by vm.papers.collectAsState()
    val filter by vm.filter.collectAsState()
    val importState by vm.importState.collectAsState()
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val exam = settings.exam
    val shelf by app.books.observeBooks().collectAsState(initial = emptyList())
    var showFilters by remember { mutableStateOf(false) }

    val examPapers = papers?.filter { it.bank.exam == exam.name }
    val shown = examPapers?.filter { filter.matches(it) }
    val hasBooks = shelf.isNotEmpty()

    ExpressiveScreen(
        "Library",
        "${exam.name.replace('_', ' ')} books and papers, offline",
        topBarActions = {
            if (!examPapers.isNullOrEmpty()) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showFilters = true }) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "Filter papers",
                        tint = if (filter.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onOpenPyq) {
                Icon(AppIcons.Package, contentDescription = "Packages: download and import")
            }
        },
    ) {
        if (hasBooks) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Books") }
                TextButton(onClick = onOpenNcert) { Text("NCERT Connect") }
            }
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

            examPapers.isNullOrEmpty() && !hasBooks -> EmptyLibrary(onOpenPyq)

            !examPapers.isNullOrEmpty() -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Question papers") }
                    if (filter.active) TextButton(onClick = { vm.setFilter(PaperFilter()) }) { Text("Clear filters (${filter.count})") }
                }
                if (shown.isNullOrEmpty()) {
                    BodyMuted("No paper matches these filters.")
                } else {
                    shown.forEach { row ->
                        PaperCard(
                            row = row,
                            onStart = { vm.startPaper(row, onOpenSession) },
                            onSavePdf = { vm.exportPdf(row) },
                            onToggleCompleted = { vm.setCompleted(row.bank.id, !row.completed) },
                        )
                    }
                }
            }
        }

        if (hasBooks || !examPapers.isNullOrEmpty()) {
            FilledTonalButton(
                onClick = onBrowse,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Text("  Browse and search questions")
            }
        }
        if (!hasBooks) {
            OutlinedButton(onClick = onOpenNcert, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text("NCERT Connect: read books, link questions")
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filter = filter,
            coachings = examPapers.orEmpty().mapNotNull { it.coaching }.distinctBy { it.lowercase() }.sorted(),
            onChange = vm::setFilter,
            onDismiss = { showFilters = false },
        )
    }

    (importState as? ImportUi.Done)?.let { done ->
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(done.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { done.lines.forEach { Text(it) } } },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("OK") } },
        )
    }
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

/** One paper: name and facts on the left; Start, save-as-PDF and mark-as-completed on the right. */
@Composable
private fun PaperCard(row: PaperRow, onStart: () -> Unit, onSavePdf: () -> Unit, onToggleCompleted: () -> Unit) {
    Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
                Text(
                    text = listOfNotNull(
                        "${row.bank.questionCount} questions",
                        row.year?.toString(),
                        row.subjectGroup,
                        row.type.takeIf { it != PaperRows.TYPE_OTHER },
                        row.coaching,
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onStart,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("Start") }
            IconButton(onClick = onSavePdf) { Icon(AppIcons.Pdf, contentDescription = "Save as PDF") }
            IconButton(onClick = onToggleCompleted) {
                if (row.completed) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Completed. Tap to unmark", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Mark as completed", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Subject, type (PYQ / Theory), completed and coaching filters. Coaching is last, and only appears if any paper has one. */
@Composable
private fun FilterSheet(filter: PaperFilter, coachings: List<String>, onChange: (PaperFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Filter papers") }
                TextButton(onClick = { onChange(PaperFilter()) }, enabled = filter.active) { Text("Clear") }
            }
            Text("Subject", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperRows.SUBJECTS.forEach { s ->
                    FilterChip(
                        selected = filter.subject == s,
                        onClick = { onChange(filter.copy(subject = if (filter.subject == s) null else s)) },
                        label = { Text(s) },
                    )
                }
            }
            Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaperRows.TYPE_PYQ, PaperRows.TYPE_THEORY).forEach { t ->
                    FilterChip(
                        selected = filter.type == t,
                        onClick = { onChange(filter.copy(type = if (filter.type == t) null else t)) },
                        label = { Text(t) },
                    )
                }
                FilterChip(
                    selected = filter.completedOnly,
                    onClick = { onChange(filter.copy(completedOnly = !filter.completedOnly)) },
                    label = { Text("Completed") },
                )
            }
            if (coachings.isNotEmpty()) {
                Text("Coaching", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    coachings.forEach { c ->
                        FilterChip(
                            selected = filter.coaching.equals(c, ignoreCase = true),
                            onClick = { onChange(filter.copy(coaching = if (filter.coaching.equals(c, ignoreCase = true)) null else c)) },
                            label = { Text(c) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
