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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.data.pyq.PyqEntry
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.library.appViewModelFactory
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle
import com.invictus.abhyas.ui.theme.AbhyasShapes

private val YEARS = (2025 downTo 2016).toList()

/** Previous-year papers: one row per year (2016-2025), download = install as a question bank. */
@Composable
fun PyqStoreScreen(app: AbhyasApp, onOpenSettings: () -> Unit) {
    val vm: PyqViewModel = viewModel(factory = appViewModelFactory(app))
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val index by vm.index.collectAsState()
    val installing by vm.installing.collectAsState()
    val message by vm.message.collectAsState()
    val banks by vm.banks.collectAsState()
    val installedIds = banks.map { it.id }.toSet()

    ExpressiveScreen("Previous year papers", "NEET 2016-2025, download once and practise offline") {
        if (settings.exam != Exam.NEET) {
            BodyMuted("Previous-year packs are only published for NEET for now. Switch the exam to NEET in Settings to see them.")
            return@ExpressiveScreen
        }

        message?.let { msg ->
            Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::dismissMessage) { Text("OK") }
                }
            }
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
