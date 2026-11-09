@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExpressiveScreen

/**
 * "View all" screen: every installed paper with a row of filter chips (All, subject, PYQ / Theory,
 * Completed, then coaching). Chips of different kinds combine; tapping a selected chip clears it.
 */
@Composable
fun PapersScreen(app: AbhyasApp, onOpenSession: (Long) -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = appViewModelFactory(app))
    val papers by vm.papers.collectAsState()
    val filter by vm.filter.collectAsState()
    val importState by vm.importState.collectAsState()
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())

    val all = papers?.filter { it.bank.exam == settings.exam.name }.orEmpty()
    val shown = all.filter { filter.matches(it) }
    val coachings = all.mapNotNull { it.coaching }.distinctBy { it.lowercase() }.sorted()

    ExpressiveScreen("Question papers", "${all.size} papers on this device") {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DotChip("All ${all.size}", androidx.compose.ui.graphics.Color(0xFF4C8DFF), !filter.active) { vm.setFilter(PaperFilter()) }
            PaperRows.SUBJECTS.forEach { s ->
                val n = all.count { it.subjectGroup == s }
                if (n > 0) DotChip("$s $n", PaperColors.subject(s), filter.subject == s) {
                    vm.setFilter(filter.copy(subject = if (filter.subject == s) null else s))
                }
            }
            listOf(PaperRows.TYPE_PYQ to PaperColors.Pyq, PaperRows.TYPE_THEORY to PaperColors.Theory).forEach { (t, c) ->
                val n = all.count { it.type == t }
                if (n > 0) DotChip("$t $n", c, filter.type == t) { vm.setFilter(filter.copy(type = if (filter.type == t) null else t)) }
            }
            val done = all.count { it.completed }
            if (done > 0) DotChip("Completed $done", PaperColors.Done, filter.completedOnly) {
                vm.setFilter(filter.copy(completedOnly = !filter.completedOnly))
            }
            coachings.forEach { c ->
                val on = filter.coaching.equals(c, ignoreCase = true)
                DotChip(c, PaperColors.Neutral, on) { vm.setFilter(filter.copy(coaching = if (on) null else c)) }
            }
        }

        if (shown.isEmpty()) {
            BodyMuted(if (all.isEmpty()) "No papers installed yet." else "No paper matches these filters.")
        } else {
            shown.forEach { row ->
                PaperCard(
                    row = row,
                    onStart = { vm.startPaper(row, onOpenSession) },
                    onResume = onOpenSession,
                    onSavePdf = { vm.exportPdf(row) },
                )
            }
        }
    }
    ImportDoneDialog(importState, vm::dismiss)
}
