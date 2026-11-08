@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ncert

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.domain.ncert.IndexEntry
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle

@Composable
fun NcertScreen(app: AbhyasApp, onOpenBook: (String) -> Unit, onPractice: () -> Unit = {}) {
    val vm: NcertViewModel = viewModel(factory = ncertFactory(app))
    val books by vm.books.collectAsState()
    val message by vm.message.collectAsState()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var indexDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<BookRow?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingUri = uri }

    ExpressiveScreen("NCERT Connect", "Read textbooks offline and link questions to them") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Add PDF") }
            FilledTonalButton(onClick = { indexDialog = true; vm.resetIndex() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("From index") }
        }
        FilledTonalButton(onClick = onPractice, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Practice from your books") }
        BodyMuted("Use books you are allowed to keep. NCERT publishes free PDFs; downloads from an index are checked against the checksum it lists.")
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = vm::dismissMessage, shapes = ButtonDefaults.shapes()) { Text("OK") }
        }
        val list = books
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            list.isEmpty() -> BodyMuted("No books yet.")
            else -> {
                SectionTitle("Your books")
                list.forEach { row -> BookCard(row, onOpen = { onOpenBook(row.book.id) }, onRetry = { vm.retry(row.book.id) }, onDelete = { toDelete = row }) }
            }
        }
    }

    pendingUri?.let { uri ->
        AddBookDialog(onDismiss = { pendingUri = null }) { title, cls, subject ->
            pendingUri = null
            vm.addLocal(uri, title, cls, subject, onOpenBook)
        }
    }
    if (indexDialog) IndexDialog(vm, books?.map { it.book.id }?.toSet().orEmpty()) { indexDialog = false }
    toDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${row.book.title}?") },
            text = { Text("The stored PDF, its text index, bookmarks, saved passages and progress are removed.") },
            confirmButton = { TextButton(onClick = { vm.delete(row.book.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BookCard(row: BookRow, onOpen: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit) {
    val b = row.book
    val readable = b.pageCount > 0 && b.status != BookStatus.DOWNLOADING
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(b.title, style = MaterialTheme.typography.titleMediumEmphasized)
            BodyMuted(listOfNotNull(b.classLevel?.let { "Class $it" }, b.subject, if (b.pageCount > 0) "${b.pageCount} pages" else null).joinToString(" - "))
            when (b.status) {
                BookStatus.DOWNLOADING -> { BodyMuted(b.message ?: "Downloading"); LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
                BookStatus.INDEXING -> {
                    BodyMuted("Indexing text ${b.indexedPages}/${b.pageCount}")
                    LinearWavyProgressIndicator(progress = { b.indexedPages.toFloat() / b.pageCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                }
                BookStatus.FAILED -> Text(b.message ?: "Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                else -> {
                    b.message?.let { BodyMuted(it) }
                    if (b.pageCount > 0) {
                        BodyMuted("Read ${row.pagesRead} of ${b.pageCount} pages")
                        LinearWavyProgressIndicator(progress = { row.pagesRead.toFloat() / b.pageCount }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (readable) FilledTonalButton(onClick = onOpen, shapes = ButtonDefaults.shapes()) { Text("Read") }
                if (b.status == BookStatus.FAILED) FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Retry") }
                TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AddBookDialog(onDismiss: () -> Unit, onConfirm: (String, Int?, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf(0) }
    var subject by remember { mutableStateOf("Physics") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add book") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionTitle("Class")
                ConnectedToggleGroup(listOf("11", "12", "Any"), cls) { cls = it }
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title.trim(), when (cls) { 0 -> 11; 1 -> 12; else -> null }, subject.trim().ifBlank { "General" }) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IndexDialog(vm: NcertViewModel, installed: Set<String>, onDismiss: () -> Unit) {
    val saved by vm.savedUrl.collectAsState()
    val state by vm.index.collectAsState()
    var url by remember { mutableStateOf("") }
    LaunchedEffect(saved) { if (url.isEmpty()) url = saved }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book index") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Index URL (https)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { vm.loadIndex(url) }, enabled = url.startsWith("https://") && state != IndexUi.Loading, shapes = ButtonDefaults.shapes()) { Text("Load index") }
                when (val s = state) {
                    IndexUi.Idle -> BodyMuted("An index is a small JSON file listing books with their download URL and SHA-256.")
                    IndexUi.Loading -> LoadingIndicator(Modifier.size(48.dp))
                    is IndexUi.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    is IndexUi.Loaded -> {
                        s.problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (s.entries.isEmpty()) BodyMuted("The index lists no usable books.")
                        s.entries.forEach { e -> IndexRow(e, e.id in installed) { vm.download(e) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun IndexRow(e: IndexEntry, installed: Boolean, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall)
            BodyMuted(listOfNotNull(e.classLevel?.let { "Class $it" }, e.subject, e.sizeBytes?.let { "${it / 1024 / 1024} MB" }).joinToString(" - "))
            e.note?.let { BodyMuted(it) }
        }
        FilledTonalButton(onClick = onDownload, enabled = !installed, shapes = ButtonDefaults.shapes()) { Text(if (installed) "Added" else "Get") }
    }
}
