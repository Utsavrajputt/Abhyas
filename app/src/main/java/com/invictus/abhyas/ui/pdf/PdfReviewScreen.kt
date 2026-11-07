@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.PdfDraftEntity
import com.invictus.abhyas.data.local.PdfJobStatus
import com.invictus.abhyas.data.pdf.PdfJobConfig
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.pdf.AnswerLayout
import com.invictus.abhyas.domain.pdf.DraftStatus
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.SectionTitle

@Composable
fun PdfReviewScreen(app: AbhyasApp, jobId: Long, onBack: () -> Unit, onReport: () -> Unit) {
    val vm: PdfReviewViewModel = viewModel(key = "pdf-review-$jobId", factory = pdfReviewFactory(app, jobId))
    val job by vm.job.collectAsState()
    val all by vm.drafts.collectAsState()
    val shown by vm.shown.collectAsState()
    val filter by vm.filter.collectAsState()
    val busy by vm.busy.collectAsState()
    val result by vm.result.collectAsState()
    val chapters by vm.chapters.collectAsState()

    var editing by remember { mutableStateOf<PdfDraftEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var chapterDialog by remember { mutableStateOf(false) }
    var reparseDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var exportName by remember { mutableStateOf<Pair<String, String>?>(null) }

    val cfg = job?.let { PdfJobConfig.decode(it.configJson) } ?: PdfJobConfig()
    val selected = all.count { it.selected }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val n = exportName
        if (uri != null && n != null) vm.export(uri, n.first, n.second)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Back") }
                Column(Modifier.weight(1f)) {
                    Text(job?.fileName ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BodyMuted("${all.size} found, $selected selected")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem("Select all valid") { menu = false; vm.selectImportable() }
                        MenuItem("Select OK only") { menu = false; vm.selectOkOnly() }
                        MenuItem("Deselect duplicates") { menu = false; vm.deselectDuplicates() }
                        MenuItem("Select none") { menu = false; vm.selectNone() }
                        MenuItem("Set chapter for selected") { menu = false; chapterDialog = true }
                        MenuItem("Apply suggested chapters") { menu = false; vm.applySuggestionsToSelected() }
                        MenuItem("Attach figures to flagged") { menu = false; vm.attachFlaggedFigures() }
                        MenuItem("Delete selected") { menu = false; deleteConfirm = true }
                        MenuItem("Parse again") { menu = false; reparseDialog = true }
                        MenuItem("Report") { menu = false; onReport() }
                    }
                }
            }
            job?.message?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ConnectedToggleGroup(DraftFilter.entries.map { it.label }, filter.ordinal) { vm.filter.value = DraftFilter.entries[it] }
            }

            Box(Modifier.weight(1f)) {
                if (all.isEmpty() && job?.status != PdfJobStatus.REVIEW && job?.status != PdfJobStatus.IMPORTED) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                } else if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        BodyMuted(if (all.isEmpty()) "No questions were detected. Try Parse again with another answer layout." else "Nothing in this filter.")
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(shown, key = { it.id }) { d ->
                            DraftCard(d, onToggle = { vm.toggle(d) }, onOpen = { editing = d })
                        }
                    }
                }
                busy?.let { b ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            LoadingIndicator(Modifier.size(72.dp))
                            Text(b, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { importDialog = true }, enabled = selected > 0 && busy == null, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("Import $selected")
                }
                FilledTonalButton(onClick = { exportDialog = true }, enabled = selected > 0 && busy == null, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("Export zip")
                }
            }
        }
    }

    editing?.let { d ->
        DraftEditor(
            draft = d,
            config = cfg,
            chapters = chapters,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onAttach = { id, done -> vm.attachFigure(id, done) },
            onRemoveFigure = { vm.removeFigure(it) },
        )
    }

    if (importDialog) NameDialog("Import into library", "Import", cfg, { importDialog = false }) { id, title -> importDialog = false; vm.import(id, title) }
    if (exportDialog) NameDialog("Export package", "Choose file", cfg, { exportDialog = false }) { id, title ->
        exportDialog = false
        exportName = id to title
        saver.launch("$id.zip")
    }
    if (chapterDialog) ChapterDialog(chapters, { chapterDialog = false }) { vm.setChapterForSelected(it); chapterDialog = false }
    if (reparseDialog) ReparseDialog(cfg, { reparseDialog = false }) { vm.reparse(it); reparseDialog = false }
    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false },
        title = { Text("Delete $selected questions?") },
        text = { Text("They are removed from this review only. The PDF is not changed.") },
        confirmButton = { TextButton(onClick = { vm.deleteSelected(); deleteConfirm = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } },
    )
    result?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            title = { Text(r.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { r.lines.forEach { Text(it) } } },
            confirmButton = { TextButton(onClick = vm::dismissResult) { Text("OK") } },
        )
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(text) }, onClick = onClick)

@Composable
private fun DraftCard(d: PdfDraftEntity, onToggle: () -> Unit, onOpen: () -> Unit) {
    val status = runCatching { DraftStatus.valueOf(d.status) }.getOrDefault(DraftStatus.OK)
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = d.selected, onCheckedChange = { onToggle() }, enabled = d.errors.isBlank())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Q${d.number}", style = MaterialTheme.typography.titleSmall)
                    BodyMuted("p.${d.page} - ${d.type.replace('_', ' ').lowercase()}")
                }
                Text(d.statement, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                BodyMuted(DraftSummary.answer(d))
                StatusLabel(status, d)
                if (d.imagePath != null) BodyMuted("Figure attached")
                if (d.chapter.isNullOrBlank() && !d.suggestedChapter.isNullOrBlank()) BodyMuted("Suggested chapter: ${d.suggestedChapter}")
            }
        }
    }
}

@Composable
private fun StatusLabel(status: DraftStatus, d: PdfDraftEntity) {
    val (text, color) = when (status) {
        DraftStatus.OK -> "OK" to MaterialTheme.colorScheme.primary
        DraftStatus.MISSING_ANSWER -> "No answer" to MaterialTheme.colorScheme.tertiary
        DraftStatus.LOW_CONFIDENCE -> "Check (${(d.confidence * 100).toInt()}%)" to MaterialTheme.colorScheme.tertiary
        DraftStatus.DUPLICATE -> (d.duplicateOf ?: "Duplicate") to MaterialTheme.colorScheme.secondary
        DraftStatus.INVALID -> "Invalid: ${d.errors.lineSequence().firstOrNull().orEmpty()}" to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    d.issues.lineSequence().firstOrNull { it.isNotBlank() }?.takeIf { status != DraftStatus.OK }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NameDialog(title: String, confirm: String, cfg: PdfJobConfig, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var id by remember { mutableStateOf(cfg.bankId) }
    var name by remember { mutableStateOf(cfg.title) }
    val valid = Regex("^[A-Za-z0-9._-]{1,64}$").matches(id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(id, { id = it }, label = { Text("Bank id") }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Letters, digits, . _ - only. Re-using an id replaces that bank with a newer version.") })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(id.trim(), name.trim()) }, enabled = valid) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChapterDialog(known: List<String>, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapter for selected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Chapter (empty clears it)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                known.filter { it.contains(text, true) }.take(5).forEach { c ->
                    TextButton(onClick = { text = c }, shapes = ButtonDefaults.shapes()) { Text(c) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim().ifBlank { null }) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReparseDialog(cfg: PdfJobConfig, onDismiss: () -> Unit, onConfirm: (PdfJobConfig) -> Unit) {
    var layout by remember { mutableStateOf(runCatching { AnswerLayout.valueOf(cfg.layout) }.getOrDefault(AnswerLayout.AUTO).ordinal) }
    var ncert by remember { mutableStateOf(cfg.ncert) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parse again") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyMuted("Re-reads the stored text with new settings. Your edits and selection in this review are lost.")
                SectionTitle("Answers")
                ConnectedToggleGroup(AnswerLayout.entries.map { it.label }, layout) { layout = it }
                SwitchLine("NCERT numbering (5.3)", ncert) { ncert = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(cfg.copy(layout = AnswerLayout.entries[layout].name, ncert = ncert)) }) { Text("Parse") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One-line answer summary for a draft card. */
object DraftSummary {
    fun answer(d: PdfDraftEntity): String = try {
        val type = QuestionType.valueOf(d.type)
        when (val p = com.invictus.abhyas.domain.pack.PayloadCodec.decodeString(type, d.payloadJson)) {
            is com.invictus.abhyas.domain.model.SingleChoicePayload -> p.correctId?.let { "Answer: ${it.uppercase()}" } ?: "No answer"
            is com.invictus.abhyas.domain.model.MultiChoicePayload -> if (p.correctIds.isEmpty()) "No answer" else "Answer: ${p.correctIds.joinToString(",") { it.uppercase() }}"
            is com.invictus.abhyas.domain.model.NumericalPayload -> p.answer?.let { "Answer: ${trim(it)}" } ?: "No answer"
            is com.invictus.abhyas.domain.model.TrueFalsePayload -> p.answer?.let { "Answer: ${if (it) "True" else "False"}" } ?: "No answer"
            is com.invictus.abhyas.domain.model.AssertionReasonPayload -> p.correctOption?.let { "Answer: option $it" } ?: "No answer"
            is com.invictus.abhyas.domain.model.MatchPayload -> p.correctId?.let { "Answer: ${it.uppercase()}" } ?: "No answer"
            is com.invictus.abhyas.domain.model.FillBlankPayload -> if (p.acceptedAnswers.isEmpty()) "No answer" else "Answer: ${p.acceptedAnswers.first()}"
        }
    } catch (e: Exception) {
        "Payload unreadable"
    }

    private fun trim(d: Double) = if (d == Math.floor(d) && d < 1e12) d.toLong().toString() else d.toString()
}
