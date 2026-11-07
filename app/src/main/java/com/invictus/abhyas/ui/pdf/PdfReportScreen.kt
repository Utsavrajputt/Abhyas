@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.domain.pdf.DraftStatus
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.SectionTitle

/** Validation and error report for one PDF job: counts plus every problem question with its reasons. */
@Composable
fun PdfReportScreen(app: AbhyasApp, jobId: Long, onBack: () -> Unit) {
    val drafts by app.pdfStudio.observeDrafts(jobId).collectAsState(initial = emptyList())
    val job by app.pdfStudio.observeJob(jobId).collectAsState(initial = null)
    fun count(s: DraftStatus) = drafts.count { it.status == s.name }
    val problems = drafts.filter { it.status != DraftStatus.OK.name || it.issues.isNotBlank() }
        .sortedWith(compareBy({ if (it.status == DraftStatus.INVALID.name) 0 else 1 }, { it.confidence }))

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                TextButton(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Back") }
                Text("Import report", Modifier.padding(start = 8.dp, top = 10.dp), style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(job?.fileName ?: "", style = MaterialTheme.typography.titleMedium)
                            job?.let { BodyMuted("${it.pageCount} pages, ${it.ocrPages} read with OCR") }
                            job?.message?.let { BodyMuted(it) }
                            Text("${drafts.size} questions found")
                            Text("${count(DraftStatus.OK)} ready")
                            Text("${count(DraftStatus.MISSING_ANSWER)} without an answer (imported as unverified, never scored)")
                            Text("${count(DraftStatus.LOW_CONFIDENCE)} low confidence")
                            Text("${count(DraftStatus.DUPLICATE)} duplicates")
                            Text("${count(DraftStatus.INVALID)} invalid (cannot be imported until fixed)")
                        }
                    }
                }
                item { SectionTitle("Questions needing attention") }
                if (problems.isEmpty()) item { BodyMuted("No problems found.") }
                items(problems, key = { it.id }) { d ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Q${d.number} (page ${d.page}) - ${d.status.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.titleSmall)
                            d.errors.lineSequence().filter { it.isNotBlank() }.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                            d.duplicateOf?.let { BodyMuted(it) }
                            d.issues.lineSequence().filter { it.isNotBlank() }.forEach { BodyMuted(it) }
                        }
                    }
                }
            }
        }
    }
}
