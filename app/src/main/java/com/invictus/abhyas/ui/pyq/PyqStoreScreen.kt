@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.pyq

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.data.pyq.PackageEntry
import com.invictus.abhyas.data.pyq.PackageType
import com.invictus.abhyas.data.pyq.PaperLink
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.library.ImportUi
import com.invictus.abhyas.ui.library.LibraryViewModel
import com.invictus.abhyas.ui.library.appViewModelFactory
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.theme.AbhyasShapes
import java.io.File

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * Packages: everything that puts content on the device. Offline = import a zip, PDF or folder and manage
 * what is installed (books and question banks in separate groups). Online = packages from every index
 * link, grouped by subject, with a search box; a "+" menu adds a link or scans a web page for PDFs.
 */
@Composable
fun PyqStoreScreen(app: AbhyasApp, onOpenSettings: () -> Unit, onOpenStudio: () -> Unit) {
    val vm: PyqViewModel = viewModel(factory = appViewModelFactory(app))
    val lib: LibraryViewModel = viewModel(factory = appViewModelFactory(app))
    var tab by remember { mutableIntStateOf(0) }

    ExpressiveScreen("Packages", "Import offline, or download packages from your links") {
        ConnectedToggleGroup(listOf("Offline", "Online"), tab) { tab = it }
        if (tab == 0) OfflineTab(app, vm, lib, onOpenStudio, onBrowseOnline = { tab = 1 })
        else OnlineTab(app, vm, onOpenSettings, onOpenStudio)
    }
}

// ----------------------------------------------------------------------------------------------
// Shared pieces
// ----------------------------------------------------------------------------------------------

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun GroupLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 6.dp),
)

@Composable
private fun EmptyState(icon: ImageVector, title: String, hint: String, actionLabel: String?, onAction: () -> Unit) {
    Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null) {
                FilledTonalButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(actionLabel) }
            }
        }
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1048576.0) else "${(bytes / 1024).coerceAtLeast(1)} KB"

// ----------------------------------------------------------------------------------------------
// Offline
// ----------------------------------------------------------------------------------------------

@Composable
private fun OfflineTab(app: AbhyasApp, vm: PyqViewModel, lib: LibraryViewModel, onOpenStudio: () -> Unit, onBrowseOnline: () -> Unit) {
    val banks by vm.banks.collectAsState()
    val books by app.books.observeBooks().collectAsState(initial = emptyList())
    val importState by lib.importState.collectAsState()
    var pendingRemove by remember { mutableStateOf<BankEntity?>(null) }
    var pendingRemoveBook by remember { mutableStateOf<BookEntity?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val group = picked ?: if (books.isEmpty() && banks.isNotEmpty()) 1 else 0

    val message by vm.message.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var pickedPdf by remember { mutableStateOf<android.net.Uri?>(null) }
    var bookPdf by remember { mutableStateOf<android.net.Uri?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) lib.importZip(uri) }
    // One entry point for everything on the phone: a PDF is asked "book or paper?", anything else is a question-bank zip.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val isPdf = context.contentResolver.getType(uri) == "application/pdf" || uri.toString().lowercase().contains(".pdf")
            if (isPdf) pickedPdf = uri else lib.importZip(uri)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) lib.importFolder(uri) }

    MessageBanner(message, vm::dismissMessage)

    Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add from your phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Hint("A textbook PDF, a question paper PDF, a question-bank zip or a folder. No internet needed.")
                }
                if (importState == ImportUi.Running) LoadingIndicator(modifier = Modifier.size(36.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { filePicker.launch(arrayOf("application/pdf") + ZIP_TYPES) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Add file") }
                FilledTonalButton(onClick = { folderPicker.launch(null) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Folder") }
            }
            lib.demoPackages.forEach { dir ->
                OutlinedButton(onClick = { lib.installDemo(dir) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text("Install demo bank ($dir) - debug build only")
                }
            }
        }
    }

    ConnectedToggleGroup(listOf("Books (${books.size})", "Question banks (${banks.size})"), group) { picked = it }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (group == 0) {
            if (books.isEmpty()) {
                EmptyState(
                    AppIcons.MenuBook, "No books yet",
                    "Download a textbook from Online, or import a PDF from your phone.",
                    "Browse online packages", onBrowseOnline,
                )
            }
            books.forEach { book ->
                InstalledBookCard(book, onRemove = { pendingRemoveBook = book }, onRetry = { vm.retryBook(book.id) })
            }
        } else {
            if (banks.isEmpty()) {
                EmptyState(
                    AppIcons.Database, "No question banks yet",
                    "Import a bank zip from your phone, or download papers from Online.",
                    "Browse online packages", onBrowseOnline,
                )
            }
            banks.forEach { bank ->
                Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(bank.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Hint(
                                listOfNotNull(
                                    "v${bank.version}", "${bank.questionCount} questions", bank.exam.replace('_', ' '),
                                    bank.classLevel?.let { "Class $it" }, bank.subject, bank.kind.lowercase(),
                                ).joinToString(" - "),
                            )
                            Text("Installed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { zipPicker.launch(ZIP_TYPES) }, shapes = ButtonDefaults.shapes()) { Text("Update") }
                        TextButton(onClick = { pendingRemove = bank }, shapes = ButtonDefaults.shapes()) { Text("Remove") }
                    }
                }
            }
        }
    }

    pickedPdf?.let { uri ->
        AlertDialog(
            onDismissRequest = { pickedPdf = null },
            title = { Text("What is this PDF?") },
            text = { Text("A textbook is added to your Library with its chapters. A question paper is read into questions that you review before importing.") },
            confirmButton = { TextButton(onClick = { bookPdf = uri; pickedPdf = null }) { Text("Textbook") } },
            dismissButton = {
                TextButton(onClick = {
                    app.pendingPaper.value = com.invictus.abhyas.data.pyq.PendingPaper(uri, null, "", "")
                    pickedPdf = null
                    onOpenStudio()
                }) { Text("Question paper") }
            },
        )
    }
    bookPdf?.let { uri ->
        AddBookDialog(
            onDismiss = { bookPdf = null },
            onConfirm = { title, cls, subject -> vm.addLocalBook(uri, title, cls, subject); bookPdf = null },
        )
    }
    pendingRemove?.let { bank ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${bank.title}?") },
            text = { Text("Its questions, bookmarks and mistakes are deleted. Past test results are kept.") },
            confirmButton = { TextButton(onClick = { vm.removeBank(bank.id); pendingRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }
    pendingRemoveBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemoveBook = null },
            title = { Text("Remove ${book.title}?") },
            text = { Text("The book file is deleted from this device. You can download it again later.") },
            confirmButton = { TextButton(onClick = { vm.removeBook(book.id); pendingRemoveBook = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingRemoveBook = null }) { Text("Cancel") } },
        )
    }
    (importState as? ImportUi.Done)?.let { done ->
        AlertDialog(
            onDismissRequest = lib::dismiss,
            title = { Text(done.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { done.lines.forEach { Text(it) } } },
            confirmButton = { TextButton(onClick = lib::dismiss) { Text("OK") } },
        )
    }
}

/** An installed book: class and subject, then status (ready with pages and size, preparing, downloading or failed). */
@Composable
private fun InstalledBookCard(book: BookEntity, onRemove: () -> Unit, onRetry: () -> Unit) {
    val bytes = remember(book.localPath, book.status) { File(book.localPath).takeIf { it.exists() }?.length() }
    val failed = book.status == BookStatus.FAILED
    val status = when (book.status) {
        BookStatus.READY -> listOfNotNull("Ready", "${book.pageCount} pages", bytes?.let(::sizeLabel)).joinToString(" - ")
        BookStatus.INDEXING -> "Preparing for search ${book.indexedPages}/${book.pageCount}"
        BookStatus.DOWNLOADING -> book.message ?: "Downloading"
        else -> book.message ?: "Failed"
    }
    Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Hint(listOfNotNull(book.classLevel?.let { "Class $it" }, book.subject).joinToString(" - "))
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (failed) TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Retry") }
            else if (book.status != BookStatus.READY) LoadingIndicator(modifier = Modifier.size(28.dp))
            TextButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) { Text("Remove") }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Online
// ----------------------------------------------------------------------------------------------

@Composable
private fun OnlineTab(app: AbhyasApp, vm: PyqViewModel, onOpenSettings: () -> Unit, onOpenStudio: () -> Unit) {
    val sources by vm.sources.collectAsState()
    val installing by vm.installing.collectAsState()
    val message by vm.message.collectAsState()
    val banks by vm.banks.collectAsState()
    val books by app.books.observeBooks().collectAsState(initial = emptyList())
    val bankVersions = banks.associate { it.id to it.version }
    val bookIds = books.filter { it.status != BookStatus.FAILED }.map { it.id }.toSet()
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val total = sources.sumOf { ((it.state as? SourceState.Loaded)?.entries?.size) ?: 0 }
    val showSearch = total >= 6

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSearch) {
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Search packages") },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text("Online packages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Box {
            FilledTonalIconButton(onClick = { menu = true }) { Icon(AppIcons.Add, contentDescription = "Add a link") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Add a link") }, onClick = { menu = false; adding = true })
                DropdownMenuItem(text = { Text("Find PDFs on a web page") }, onClick = { menu = false; vm.resetScan(); scanning = true })
            }
        }
    }

    MessageBanner(message, vm::dismissMessage)

    if (sources.isEmpty()) {
        EmptyState(
            AppIcons.Package, "No online sources yet",
            "Add a GitHub link in Settings, Sources. Its books and papers then show up here.",
            "Open settings", onOpenSettings,
        )
    }

    sources.forEach { src ->
        val loaded = src.state as? SourceState.Loaded
        val needle = query.trim()
        val shown = loaded?.entries.orEmpty().filter { e ->
            needle.isEmpty() || listOfNotNull(e.name, e.subject, e.coaching, e.description).any { it.contains(needle, ignoreCase = true) }
        }
        val todo = shown.filter { !isUpToDate(it, bankVersions, bookIds) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(loaded?.name ?: src.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (todo.size > 1) {
                    TextButton(onClick = { vm.downloadAll(todo) }, shapes = ButtonDefaults.shapes()) { Text("Download all (${todo.size})") }
                }
            }
            when (val st = src.state) {
                SourceState.Loading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                }
                is SourceState.Error -> {
                    Hint(st.message)
                    OutlinedButton(onClick = vm::refresh, shapes = ButtonDefaults.shapes()) { Text("Retry") }
                }
                is SourceState.Loaded -> {
                    if (st.entries.isEmpty()) Hint("This link has no packages.")
                    else if (shown.isEmpty()) Hint("Nothing matches \"$needle\".")
                    val groups = shown.groupBy { it.subject?.takeIf { s -> s.isNotBlank() } ?: "Other" }
                        .toList().sortedWith(compareBy({ it.first == "Other" }, { it.first.lowercase() }))
                    groups.forEach { (subject, list) ->
                        if (groups.size > 1) GroupLabel(subject)
                        val ordered = if (list.all { it.type == PackageType.BOOK }) list.sortedWith(compareBy({ it.classLevel ?: 99 }, { it.name })) else list
                        ordered.forEach { entry ->
                            PackageCard(
                                entry = entry,
                                book = books.firstOrNull { it.id == entry.id },
                                busy = entry.id in installing,
                                installed = isUpToDate(entry, bankVersions, bookIds),
                                updatable = entry.type == PackageType.PAPER && (bankVersions[entry.id] ?: Int.MAX_VALUE) < entry.version,
                                onDownload = { vm.download(entry) },
                            )
                        }
                    }
                    if (st.skipped > 0) Hint("${st.skipped} invalid entries in this index were skipped.")
                }
            }
        }
    }

    SavedLinks(vm, installing, onOpenStudio)

    if (adding) AddLinkDialog(vm = vm, onDismiss = { adding = false })
    if (scanning) ScanPageDialog(vm = vm, onDismiss = { scanning = false })
}

private fun isUpToDate(entry: PackageEntry, bankVersions: Map<String, Int>, bookIds: Set<String>): Boolean = when (entry.type) {
    PackageType.PAPER -> (bankVersions[entry.id] ?: -1) >= entry.version
    PackageType.BOOK -> entry.id in bookIds
}

/** One downloadable package, compact: name and facts on the left; download button, progress or a tick on the right. */
@Composable
private fun PackageCard(entry: PackageEntry, book: BookEntity?, busy: Boolean, installed: Boolean, updatable: Boolean, onDownload: () -> Unit) {
    val live = book?.takeIf { it.status == BookStatus.DOWNLOADING || it.status == BookStatus.INDEXING }
    val failed = book?.takeIf { it.status == BookStatus.FAILED }
    val status = when {
        live != null && live.status == BookStatus.INDEXING -> "Preparing for search ${live.indexedPages}/${live.pageCount}"
        live != null -> live.message ?: "Downloading"
        failed != null -> failed.message ?: "Download failed"
        else -> null
    }
    Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.description.isNotBlank()) {
                    Hint(entry.description)
                }
                Hint(
                    listOfNotNull(
                        entry.classLevel?.let { "Class $it" },
                        "v${entry.version}",
                        entry.questions.takeIf { it > 0 }?.let { "$it questions" },
                        entry.year?.toString(),
                        entry.coaching,
                        entry.sizeBytes?.let { sizeLabel(it) },
                    ).joinToString(" - "),
                )
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                busy || live != null -> LoadingIndicator(modifier = Modifier.size(32.dp))
                installed -> Icon(AppIcons.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                updatable -> FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes()) { Text("Update") }
                failed != null -> FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes()) { Text("Retry") }
                else -> FilledTonalIconButton(onClick = onDownload) { Icon(AppIcons.Download, contentDescription = "Download") }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String?, onDismiss: () -> Unit) {
    message ?: return
    Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

/** Links the user added (a paper PDF or a question-bank zip). Added from the "+" menu; shown only when there are some. */
@Composable
private fun SavedLinks(vm: PyqViewModel, installing: Set<String>, onOpenStudio: () -> Unit) {
    val links by vm.links.collectAsState()
    if (links.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("My links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        links.forEach { link ->
            Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(link.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint(
                            listOfNotNull(
                                link.year?.toString(),
                                if (link.type == PaperLink.TYPE_PACK) "Question bank" else "PDF paper",
                                link.url.removePrefix("https://").substringBefore('/'),
                            ).joinToString(" - "),
                        )
                    }
                    if (link.id in installing) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        FilledTonalIconButton(onClick = { vm.fetchLink(link, onOpenStudio) }) {
                            Icon(AppIcons.Download, contentDescription = if (link.type == PaperLink.TYPE_PACK) "Install" else "Download")
                        }
                        IconButton(onClick = { vm.removeLink(link.id) }) { Icon(AppIcons.Close, contentDescription = "Remove link") }
                    }
                }
            }
        }
    }
}

/** Paste a page address; the app lists the .pdf / .zip links in its source and you tick the ones to save. */
@Composable
private fun ScanPageDialog(vm: PyqViewModel, onDismiss: () -> Unit) {
    val scan by vm.scan.collectAsState()
    var pageUrl by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find PDFs on a page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    pageUrl, { pageUrl = it },
                    label = { Text("Page address (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val st = scan) {
                    ScanUi.Idle -> BodyMuted("Works on simple pages whose links are in the page source. Pages that need a login or JavaScript show nothing.")
                    ScanUi.Loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                    is ScanUi.Error -> Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is ScanUi.Found -> Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    ) {
                        st.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    picked = if (item.url in picked) picked - item.url else picked + item.url
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = item.url in picked, onCheckedChange = {
                                    picked = if (item.url in picked) picked - item.url else picked + item.url
                                })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    Text(
                                        if (item.type == PaperLink.TYPE_PACK) "zip" else "PDF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val st = scan
            if (st is ScanUi.Found) {
                TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = { vm.addFound(st.items.filter { it.url in picked }); onDismiss() },
                ) { Text("Add selected (${picked.size})") }
            } else {
                TextButton(enabled = scan != ScanUi.Loading, onClick = { vm.scanPage(pageUrl) }) { Text("Search") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddLinkDialog(vm: PyqViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectedToggleGroup(listOf("PDF paper", "Question-bank zip"), type) { type = it }
                OutlinedTextField(title, { title = it }, label = { Text("Name (e.g. NEET 2024)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("Direct link (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.addLink(title, year, url, if (type == 1) PaperLink.TYPE_PACK else PaperLink.TYPE_PDF)
                if (err == null) onDismiss() else error = err
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
