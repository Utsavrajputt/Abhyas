@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.pdf

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.PdfJobEntity
import com.invictus.abhyas.data.local.PdfJobStatus
import com.invictus.abhyas.data.pdf.PdfJobConfig
import com.invictus.abhyas.domain.pdf.AnswerLayout
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle

@Composable
fun PdfStudioScreen(app: AbhyasApp, onOpenJob: (Long) -> Unit) {
    val vm: PdfStudioViewModel = viewModel(factory = pdfStudioFactory(app))
    val jobs by vm.jobs.collectAsState()
    val adding by vm.adding.collectAsState()
    val message by vm.message.collectAsState()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var hint by remember { mutableStateOf<com.invictus.abhyas.data.pyq.PendingPaper?>(null) }
    // A paper downloaded from Downloads > Previous papers > My links arrives here for its import settings.
    val incoming by app.pendingPaper.collectAsState()
    androidx.compose.runtime.LaunchedEffect(incoming) {
        incoming?.let {
            hint = it
            pendingUri = it.uri
            app.pendingPaper.value = null
        }
    }
    var toDelete by remember { mutableStateOf<PdfJobEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    ExpressiveScreen("PDF Studio", "Turn PDFs into reviewed question banks, offline") {
        Button(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            enabled = !adding,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
            Text("  Choose a PDF")
        }
        BodyMuted("Digital PDFs are read directly; scanned pages use on-device OCR. Nothing is imported until you review it.")
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        when {
            jobs == null || adding -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(64.dp))
            }
            jobs!!.isEmpty() -> BodyMuted("No PDFs yet. Tap Choose a PDF to read a question paper or chapter; you review every question before anything is imported.")
            else -> {
                SectionTitle("Your PDFs")
                jobs!!.forEach { job ->
                    JobCard(
                        job = job,
                        onOpen = { onOpenJob(job.id) },
                        onCancel = { vm.cancel(job.id) },
                        onResume = { vm.resume(job.id) },
                        onDelete = { toDelete = job },
                    )
                }
            }
        }
    }

    pendingUri?.let { uri ->
        SetupDialog(
            initialYear = hint?.year?.toString() ?: "",
            onDismiss = { pendingUri = null; hint = null },
            onConfirm = { cfg ->
                val h = hint
                vm.add(uri, if (h != null) cfg.copy(title = h.title, bankId = h.bankId) else cfg)
                pendingUri = null
                hint = null
            },
        )
    }
    toDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${job.fileName}?") },
            text = { Text("The stored copy, extracted text and unreviewed questions are removed. Banks already imported are kept.") },
            confirmButton = { TextButton(onClick = { vm.delete(job.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun JobCard(job: PdfJobEntity, onOpen: () -> Unit, onCancel: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    val working = job.status == PdfJobStatus.QUEUED || job.status == PdfJobStatus.EXTRACTING || job.status == PdfJobStatus.PARSING
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(job.fileName, style = MaterialTheme.typography.titleMediumEmphasized)
            val done = (job.nextPage - 1).coerceIn(0, job.pageCount)
            val label = when (job.status) {
                PdfJobStatus.QUEUED -> "Waiting to start"
                PdfJobStatus.EXTRACTING -> "Reading page $done of ${job.pageCount}" + if (job.ocrPages > 0) " (OCR on ${job.ocrPages})" else ""
                PdfJobStatus.PARSING -> "Finding questions"
                PdfJobStatus.REVIEW -> "Ready to review (${job.pageCount} pages)"
                PdfJobStatus.IMPORTED -> "Imported"
                PdfJobStatus.CANCELLED -> "Cancelled at page ${job.nextPage}"
                else -> "Failed"
            }
            BodyMuted(label)
            if (working) {
                if (job.status == PdfJobStatus.EXTRACTING) {
                    LinearWavyProgressIndicator(progress = { done.toFloat() / job.pageCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                } else LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            job.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (job.status == PdfJobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.status == PdfJobStatus.REVIEW || job.status == PdfJobStatus.IMPORTED) {
                    FilledTonalButton(onClick = onOpen, shapes = ButtonDefaults.shapes()) { Text(if (job.status == PdfJobStatus.REVIEW) "Review" else "Open") }
                }
                if (working) TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
                if (job.status == PdfJobStatus.CANCELLED || job.status == PdfJobStatus.FAILED) {
                    FilledTonalButton(onClick = onResume, shapes = ButtonDefaults.shapes()) { Text("Resume") }
                }
                if (!working) TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun SetupDialog(initialYear: String, onDismiss: () -> Unit, onConfirm: (PdfJobConfig) -> Unit) {
    var exam by remember { mutableStateOf(0) }
    var cls by remember { mutableStateOf(2) }
    var subject by remember { mutableStateOf("Physics") }
    var chapter by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(initialYear) }
    var kind by remember { mutableStateOf(0) }
    var layout by remember { mutableStateOf(0) }
    var ncert by remember { mutableStateOf(false) }
    var ocr by remember { mutableStateOf(true) }
    val kinds = listOf("PYQS", "THEORY", "NUMERICALS", "MIXED")
    val layouts = AnswerLayout.entries
    val subjects = if (exam == 0) listOf("Physics", "Chemistry", "Botany", "Zoology") else listOf("Physics", "Chemistry", "Mathematics")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Exam")
                ConnectedToggleGroup(listOf("NEET", "JEE Main"), exam) { exam = it; subject = "Physics" }
                SectionTitle("Class")
                ConnectedToggleGroup(listOf("11", "12", "Any"), cls) { cls = it }
                SectionTitle("Subject")
                ConnectedToggleGroup(subjects, subjects.indexOf(subject).coerceAtLeast(0)) { subject = subjects[it] }
                OutlinedTextField(chapter, { chapter = it }, label = { Text("Chapter for all (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionTitle("Kind")
                ConnectedToggleGroup(listOf("PYQs", "Theory", "Numerical", "Mixed"), kind) { kind = it }
                SectionTitle("Answers")
                ConnectedToggleGroup(layouts.map { it.label }, layout) { layout = it }
                SwitchLine("NCERT numbering (5.3)", ncert) { ncert = it }
                SwitchLine("OCR for scanned pages", ocr) { ocr = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    PdfJobConfig(
                        exam = if (exam == 0) "NEET" else "JEE_MAIN",
                        classLevel = when (cls) { 0 -> 11; 1 -> 12; else -> null },
                        subject = subject,
                        chapter = chapter.trim().ifBlank { null },
                        kind = kinds[kind],
                        layout = layouts[layout].name,
                        ncert = ncert,
                        year = year.toIntOrNull(),
                        allowOcr = ocr,
                    ),
                )
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SwitchLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
