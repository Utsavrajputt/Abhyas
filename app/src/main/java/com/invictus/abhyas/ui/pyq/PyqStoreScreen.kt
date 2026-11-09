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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.data.ncert.OfficialBook
import com.invictus.abhyas.data.ncert.OfficialBookState
import com.invictus.abhyas.data.ncert.OfficialBooks
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
import com.invictus.abhyas.ui.screens.SectionTitle
import com.invictus.abhyas.ui.theme.AbhyasShapes

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * Packages: everything that puts content on the device. Offline = import a zip, PDF or folder and manage
 * what is installed. Online = packages from every link (NCERT index, PYQ index, Settings > Other links),
 * each with name, description, version and a Download button.
 */
@Composable
fun PyqStoreScreen(app: AbhyasApp, onOpenSettings: () -> Unit, onOpenStudio: () -> Unit) {
    val vm: PyqViewModel = viewModel(factory = appViewModelFactory(app))
    val lib: LibraryViewModel = viewModel(factory = appViewModelFactory(app))
    var tab by remember { mutableIntStateOf(0) }

    ExpressiveScreen("Packages", "Import offline, or download packages from your links") {
        ConnectedToggleGroup(listOf("Offline", "Online"), tab) { tab = it }
        if (tab == 0) OfflineTab(app, vm, lib, onOpenStudio) else OnlineTab(app, vm, onOpenSettings, onOpenStudio)
    }
}

@Composable
private fun OfflineTab(app: AbhyasApp, vm: PyqViewModel, lib: LibraryViewModel, onOpenStudio: () -> Unit) {
    val banks by vm.banks.collectAsState()
    val books by app.books.observeBooks().collectAsState(initial = emptyList())
    val importState by lib.importState.collectAsState()
    var pendingRemove by remember { mutableStateOf<BankEntity?>(null) }
    var pendingRemoveBook by remember { mutableStateOf<BookEntity?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) lib.importZip(uri) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) lib.importFolder(uri) }

    BodyMuted("Add content from your phone. Nothing here needs internet.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { zipPicker.launch(ZIP_TYPES) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Import zip") }
        FilledTonalButton(onClick = onOpenStudio, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Import PDF") }
        FilledTonalButton(onClick = { folderPicker.launch(null) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Folder") }
    }
    lib.demoPackages.forEach { dir ->
        OutlinedButton(onClick = { lib.installDemo(dir) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text("Install demo bank ($dir) - debug build only")
        }
    }
    if (importState == ImportUi.Running) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(modifier = Modifier.size(56.dp)) }
    }

    SectionTitle("Installed question banks")
    if (banks.isEmpty()) BodyMuted("No question banks installed yet.")
    banks.forEach { bank ->
        Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bank.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                BodyMuted(
                    listOfNotNull(
                        "v${bank.version}", "${bank.questionCount} questions", bank.exam.replace('_', ' '),
                        bank.classLevel?.let { "Class $it" }, bank.subject, bank.kind.lowercase(),
                    ).joinToString(" - "),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { zipPicker.launch(ZIP_TYPES) }, shapes = ButtonDefaults.shapes()) { Text("Update") }
                    TextButton(onClick = { pendingRemove = bank }, shapes = ButtonDefaults.shapes()) { Text("Remove") }
                }
            }
        }
    }

    if (books.isNotEmpty()) {
        SectionTitle("Installed books")
        books.forEach { book ->
            Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(book.classLevel?.let { "Class $it" }, book.subject).joinToString(" - "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { pendingRemoveBook = book }) { Text("Remove") }
                }
            }
        }
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

@Composable
private fun OnlineTab(app: AbhyasApp, vm: PyqViewModel, onOpenSettings: () -> Unit, onOpenStudio: () -> Unit) {
    val sources by vm.sources.collectAsState()
    val installing by vm.installing.collectAsState()
    val message by vm.message.collectAsState()
    val banks by vm.banks.collectAsState()
    val books by app.books.observeBooks().collectAsState(initial = emptyList())
    val bankVersions = banks.associate { it.id to it.version }
    val bookIds = books.filter { it.status != BookStatus.FAILED }.map { it.id }.toSet()

    MessageBanner(message, vm::dismissMessage)

    if (sources.isEmpty()) {
        SectionTitle("No online sources yet")
        BodyMuted("Add the link of an index.json in Settings, NCERT & data (NCERT index, PYQ index or Other links). Its packages then show up here with a Download button.")
        Button(onClick = onOpenSettings, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Open settings") }
    }

    sources.forEach { src ->
        val loaded = src.state as? SourceState.Loaded
        SectionTitle(loaded?.name ?: src.label)
        when (val st = src.state) {
            SourceState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            }
            is SourceState.Error -> {
                BodyMuted(st.message)
                OutlinedButton(onClick = vm::refresh, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            }
            is SourceState.Loaded -> {
                if (st.entries.isEmpty()) BodyMuted("This link has no packages.")
                val todo = st.entries.filter { !isUpToDate(it, bankVersions, bookIds) }
                if (todo.size > 1) {
                    FilledTonalButton(onClick = { vm.downloadAll(todo) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.Download, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Download all (${todo.size})")
                    }
                }
                st.entries.forEach { entry ->
                    PackageCard(
                        entry = entry,
                        busy = entry.id in installing,
                        installed = isUpToDate(entry, bankVersions, bookIds),
                        updatable = entry.type == PackageType.PAPER && (bankVersions[entry.id] ?: Int.MAX_VALUE) < entry.version,
                        onDownload = { vm.download(entry) },
                    )
                }
                if (st.skipped > 0) BodyMuted("${st.skipped} invalid entries in this index were skipped.")
            }
        }
    }

    SectionTitle("NCERT textbooks (official site)")
    BooksSection(app)
    LinksSection(vm, installing, onOpenStudio)
}

private fun isUpToDate(entry: PackageEntry, bankVersions: Map<String, Int>, bookIds: Set<String>): Boolean = when (entry.type) {
    PackageType.PAPER -> (bankVersions[entry.id] ?: -1) >= entry.version
    PackageType.BOOK -> entry.id in bookIds
}

/** One downloadable package: name, description and facts on the left; Download / Update / installed on the right. */
@Composable
private fun PackageCard(entry: PackageEntry, busy: Boolean, installed: Boolean, updatable: Boolean, onDownload: () -> Unit) {
    Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.description.isNotBlank()) {
                    Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = listOfNotNull(
                        if (entry.type == PackageType.BOOK) "Book" else "Question paper",
                        "v${entry.version}",
                        entry.questions.takeIf { it > 0 }?.let { "$it questions" },
                        entry.year?.toString(),
                        entry.subject,
                        entry.coaching,
                        entry.sizeBytes?.let { "${(it / (1024 * 1024)).coerceAtLeast(1)} MB" },
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                busy -> LoadingIndicator(modifier = Modifier.size(40.dp))
                installed -> Icon(AppIcons.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
                else -> FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                    Icon(AppIcons.Download, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (updatable) "Update" else "Download")
                }
            }
        }
    }
}

/** NCERT PCBM textbooks for Class 11 and 12, downloaded from the official NCERT website. */
@Composable
private fun BooksSection(app: AbhyasApp) {
    val installed by app.books.observeBooks().collectAsState(initial = emptyList())
    val dl by app.officialBooks.state.collectAsState()
    val installedIds = installed.filter { it.status != BookStatus.FAILED }.map { it.id }.toSet()

    BodyMuted("Official NCERT textbooks from ncert.nic.in. Needs internet once; reading and search work offline afterwards.")
    listOf(11, 12).forEach { cls ->
        val list = OfficialBooks.all.filter { it.classLevel == cls }
        val todo = list.filter { it.id !in installedIds && dl[it.id] !is OfficialBookState.Running }
        SectionTitle("Class $cls - Physics, Chemistry, Biology, Maths")
        if (todo.size > 1) {
            FilledTonalButton(
                onClick = { todo.forEach(app.officialBooks::download) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Download all Class $cls (${todo.size})")
            }
        }
        list.forEach { book ->
            BookRow(
                book = book,
                installedStatus = installed.firstOrNull { it.id == book.id }?.takeIf { it.status != BookStatus.FAILED }?.status,
                state = dl[book.id],
                onDownload = { app.officialBooks.download(book) },
            )
        }
    }
}

@Composable
private fun BookRow(book: OfficialBook, installedStatus: String?, state: OfficialBookState?, onDownload: () -> Unit) {
    Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        state is OfficialBookState.Running -> state.label
                        state is OfficialBookState.Failed -> state.message
                        installedStatus == BookStatus.DOWNLOADING || installedStatus == BookStatus.INDEXING -> "Preparing for search..."
                        installedStatus != null -> "In your Library"
                        else -> "Class ${book.classLevel} - ${book.subject}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state is OfficialBookState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state is OfficialBookState.Running -> LoadingIndicator(modifier = Modifier.size(40.dp))
                state is OfficialBookState.Failed -> FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes()) { Text("Retry") }
                installedStatus != null -> Icon(AppIcons.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
                else -> FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                    Icon(AppIcons.Download, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Download")
                }
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

/**
 * "My links": the user adds the address of a paper PDF (reviewed in PDF Studio) or of an Abhyas
 * question-bank zip from any https source they are allowed to download from. Nothing is built in.
 */
@Composable
private fun LinksSection(vm: PyqViewModel, installing: Set<String>, onOpenStudio: () -> Unit) {
    val links by vm.links.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    SectionTitle("My links")
    BodyMuted("Add the link of a paper PDF or a question-bank zip from any website you may download from. Only add content you have the right to use.")
    links.forEach { link ->
        Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(link.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = listOfNotNull(
                            link.year?.toString(),
                            if (link.type == PaperLink.TYPE_PACK) "Question bank" else "PDF paper",
                            link.url.removePrefix("https://").substringBefore('/'),
                        ).joinToString(" - "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (link.id in installing) {
                    LoadingIndicator(modifier = Modifier.size(40.dp))
                } else {
                    FilledTonalButton(onClick = { vm.fetchLink(link, onOpenStudio) }, shapes = ButtonDefaults.shapes()) {
                        Icon(AppIcons.Download, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (link.type == PaperLink.TYPE_PACK) "Install" else "Download")
                    }
                    IconButton(onClick = { vm.removeLink(link.id) }) {
                        Icon(AppIcons.Close, contentDescription = "Remove link")
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { adding = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
        Icon(AppIcons.Add, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Add a link")
    }
    FilledTonalButton(onClick = { vm.resetScan(); scanning = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
        Text("Find PDFs on a web page")
    }
    if (adding) AddLinkDialog(vm = vm, onDismiss = { adding = false })
    if (scanning) ScanPageDialog(vm = vm, onDismiss = { scanning = false })
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
