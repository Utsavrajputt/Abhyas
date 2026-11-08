@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ncert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.domain.ncert.GenKind
import com.invictus.abhyas.ui.library.FilterDropdown
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle

private val KIND_HELP = mapOf(
    GenKind.FILL_BLANK to "Defined terms from the text, with the term blanked out. Scored.",
    GenKind.TRUE_FALSE to "Sentences from the book, some changed to be false. Scored.",
    GenKind.EXERCISE to "The end-of-chapter exercises. NCERT prints no answers, so these are not scored.",
    GenKind.AI_MCQ to "Four-option questions written by AI from the text. Unverified and not scored.",
    GenKind.ASSERTION_REASON to "Written by AI from the text. Unverified and not scored.",
)

@Composable
private fun KindRow(kind: GenKind, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToggleButton(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(kind.label) }
        BodyMuted(KIND_HELP.getValue(kind))
    }
}

@Composable
fun NcertPracticeScreen(app: AbhyasApp, onStarted: (Long) -> Unit, onOpenSettings: () -> Unit = {}) {
    val vm: NcertPracticeViewModel = viewModel(factory = ncertPracticeFactory(app))
    val ui by vm.ui.collectAsState()
    val book = ui.books.firstOrNull { it.id == ui.bookId }

    ExpressiveScreen("NCERT practice", "Questions made from your books") {
        if (ui.books.isEmpty()) {
            BodyMuted("Add a book in NCERT Connect first. Practice is made from its text, so scanned books cannot be used.")
            return@ExpressiveScreen
        }

        SectionTitle("Book")
        FilterDropdown("Book", ui.books.map { it.title }, book?.title) { title ->
            vm.selectBook(ui.books.firstOrNull { it.title == title }?.id)
        }
        if (book != null) {
            SectionTitle("Chapter")
            FilterDropdown("Chapter", ui.chapters.map { it.title }, ui.chapters.getOrNull(ui.chapterIdx)?.title) { title ->
                vm.selectChapter(ui.chapters.indexOfFirst { it.title == title })
            }
            if (ui.chapterIdx < 0) BodyMuted("Whole book. Pick a chapter for book exercises.")
        }

        SectionTitle("Question types")
        GenKind.entries.filter { !it.needsAi }.forEach { kind -> KindRow(kind, kind in ui.kinds, true) { vm.toggle(kind) } }

        SectionTitle("Make with AI")
        if (!ui.aiReady) {
            BodyMuted("Needs AI turned on and your Gemini API key saved in Settings.")
            FilledTonalButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Open AI settings") }
        }
        GenKind.entries.filter { it.needsAi }.forEach { kind -> KindRow(kind, kind in ui.kinds, ui.aiReady) { vm.toggle(kind) } }

        SectionTitle("Questions")
        ConnectedToggleGroup(PRACTICE_COUNTS.map { "$it" }, ui.countIdx) { vm.selectCount(it) }

        Button(
            onClick = { vm.start() },
            enabled = !ui.busy && book != null && ui.kinds.isNotEmpty(),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Make questions and start", style = MaterialTheme.typography.titleMediumEmphasized) }
        if (ui.busy) LoadingIndicator()
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }

    ui.pending?.let { (attemptId, notes) ->
        if (notes.isEmpty()) {
            // Nothing to tell the student: go straight to the session.
            androidx.compose.runtime.LaunchedEffect(attemptId) { vm.dismissPending(); onStarted(attemptId) }
        } else {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Ready") },
                text = {
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.dismissPending(); onStarted(attemptId) }) { Text("Start") } },
            )
        }
    }
}
