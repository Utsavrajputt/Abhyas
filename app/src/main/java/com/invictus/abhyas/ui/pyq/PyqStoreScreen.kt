@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.pyq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.data.pyq.PaperLink
import com.invictus.abhyas.data.pyq.PyqEntry
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.library.appViewModelFactory
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.data.ncert.OfficialBook
import com.invictus.abhyas.data.ncert.OfficialBookState
import com.invictus.abhyas.data.ncert.OfficialBooks
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle
import com.invictus.abhyas.ui.theme.AbhyasShapes

private val YEARS = (2025 downTo 2016).toList()

/** Previous-year papers: one row per year (2016-2025), download = install as a question bank. */
@Composable
fun PyqStoreScreen(app: AbhyasApp, onOpenSettings: () -> Unit, onOpenStudio: () -> Unit) {
    val vm: PyqViewModel = viewModel(factory = appViewModelFactory(app))
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val index by vm.index.collectAsState()
    val installing by vm.installing.collectAsState()
    val message by vm.message.collectAsState()
    val banks by vm.banks.collectAsState()
    val installedIds = banks.map { it.id }.toSet()

    var tab by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current

    ExpressiveScreen("Downloads", "Books and previous year papers, download once and use offline") {
        ConnectedToggleGroup(listOf("Books", "Previous papers"), tab) { tab = it }
        if (tab == 0) {
            BooksSection(app)
            return@ExpressiveScreen
        }
        MessageBanner(message, vm::dismissMessage)
        LinksSection(vm, installing, onOpenStudio)
        if (settings.exam != Exam.NEET) {
            BodyMuted("Previous-year packs are only published for NEET for now. Switch the exam to NEET in Settings to see them.")
            return@ExpressiveScreen
        }

        when (val state = index) {
            PyqIndexUi.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(72.dp))
            }

            PyqIndexUi.NoUrl -> {
                SectionTitle("Set the PYQ source first")
                BodyMuted(
                    "Papers are downloaded as ready-made question banks from an HTTPS index you host " +
                        "(see tools/build_pyq_index.py). Add its address in Settings, NCERT & data, PYQ index URL.",
                )
                Button(onClick = onOpenSettings, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text("Open settings")
                }
                SectionTitle("Or use the official papers")
                BodyMuted(
                    "NTA publishes the papers and answer keys as PDFs. Download one from the official NEET site, " +
                        "then use + > Import PDF: PDF Studio turns it into a question bank you can review and practise.",
                )
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri("https://neet.nta.nic.in/") } },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open the official NTA NEET site") }
            }

            is PyqIndexUi.Error -> {
                SectionTitle("Could not load the list")
                BodyMuted(state.message)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = vm::refresh, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Retry") }
                    OutlinedButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Settings") }
                }
            }

            is PyqIndexUi.Loaded -> {
                val neet = state.entries.filter { it.exam.equals("NEET", ignoreCase = true) }
                val byYear = neet.associateBy { it.year }
                val missing = neet.count { it.id !in installedIds }
                if (missing > 1) {
                    FilledTonalButton(
                        onClick = { vm.downloadAll(neet, installedIds) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(AppIcons.Download, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Download all ($missing)")
                    }
                }
                YEARS.forEach { year ->
                    YearRow(
                        year = year,
                        entry = byYear[year],
                        installed = byYear[year]?.id in installedIds,
                        busy = byYear[year]?.id in installing,
                        onDownload = { byYear[year]?.let(vm::download) },
                    )
                }
            }
        }
    }
}

@Composable
private fun YearRow(year: Int, entry: PyqEntry?, installed: Boolean, busy: Boolean, onDownload: () -> Unit) {
    Surface(shape = AbhyasShapes.L, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry?.title ?: "NEET UG $year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        entry == null -> "Not published yet"
                        entry.questions > 0 -> "${entry.questions} questions"
                        else -> "Question bank"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                busy -> LoadingIndicator(modifier = Modifier.size(40.dp))
                installed -> Icon(AppIcons.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
                entry != null -> FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                    Icon(AppIcons.Download, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Download")
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
