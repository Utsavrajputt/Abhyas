@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.attempt.ExamTemplate
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.ui.library.BrowseViewModel
import com.invictus.abhyas.ui.library.FilterControls
import com.invictus.abhyas.ui.library.FilterDropdown
import com.invictus.abhyas.ui.library.appViewModelFactory
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val COUNTS = listOf(10, 20, 40, 60)
private val TIMERS = listOf<Int?>(null, 15, 30, 60)

@Composable
fun SetupScreen(app: AbhyasApp, onStarted: (Long) -> Unit, onDismiss: () -> Unit = {}) {
    val vm: BrowseViewModel = viewModel(key = "setup", factory = appViewModelFactory(app))
    val filter by vm.filter.collectAsState()
    val matching by vm.count.collectAsState()
    val scope = rememberCoroutineScope()
    val templates by produceState(emptyList<ExamTemplate>()) {
        value = withContext(Dispatchers.IO) { app.templateRepository.builtIn() }
    }

    var mode by remember { mutableStateOf(PracticeMode.CUSTOM) }
    var countIdx by remember { mutableStateOf(1) }
    var timerIdx by remember { mutableStateOf(0) }
    var starting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<StartResult.Started?>(null) }

    fun handle(r: StartResult) {
        starting = false
        when (r) {
            is StartResult.Failed -> message = r.message
            is StartResult.Started -> if (r.warnings.isEmpty()) onStarted(r.attemptId) else pending = r
        }
    }

    com.invictus.abhyas.ui.components.PracticeDialog("Custom practice", onDismiss) {
        SectionTitle("Mode")
        FilterDropdown("Mode", PracticeMode.entries.map { it.label }, mode.label.takeIf { mode != PracticeMode.CUSTOM }) { v ->
            mode = PracticeMode.entries.firstOrNull { it.label == v } ?: PracticeMode.CUSTOM
            vm.update { mode.apply(it) }
        }

        SectionTitle("Filters")
        FilterControls(vm, showSearch = false)
        BodyMuted("$matching questions match")

        SectionTitle("Questions")
        ConnectedToggleGroup(COUNTS.map { "$it" }, countIdx) { countIdx = it }
        SectionTitle("Timer")
        ConnectedToggleGroup(TIMERS.map { if (it == null) "Off" else "$it min" }, timerIdx) { timerIdx = it }

        Button(
            onClick = {
                val problem = mode.validate(filter)
                if (problem != null) { message = problem; return@Button }
                starting = true
                scope.launch {
                    handle(
                        app.attemptRepository.startPractice(
                            PracticeConfig(mode, filter, COUNTS[countIdx], TIMERS[timerIdx]?.let { it * 60 }),
                        ),
                    )
                }
            },
            enabled = !starting && matching > 0,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start practice", style = MaterialTheme.typography.titleMediumEmphasized) }

        if (starting) LoadingIndicator()

        if (templates.isNotEmpty()) {
            SectionTitle("Full tests")
            BodyMuted("Timed, with the exam's marking scheme. Templates are JSON files in app assets.")
            templates.forEach { t ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMediumEmphasized)
                        BodyMuted(
                            listOfNotNull(
                                "${t.totalQuestions} questions",
                                t.durationMinutes?.let { "$it min" },
                                t.marking?.let { "+${trimNum(it.correct)} / -${trimNum(it.wrong)}" },
                            ).joinToString(" - "),
                        )
                        Row {
                            FilledTonalButton(
                                onClick = {
                                    starting = true
                                    scope.launch { handle(app.attemptRepository.startTemplate(t)) }
                                },
                                enabled = !starting,
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Start test") }
                        }
                    }
                }
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    pending?.let { started ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Starting with fewer questions") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { started.warnings.forEach { Text(it) } } },
            confirmButton = {
                TextButton(onClick = { pending = null; onStarted(started.attemptId) }) { Text("Start anyway") }
            },
        )
    }
}

private fun trimNum(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else v.toString()
