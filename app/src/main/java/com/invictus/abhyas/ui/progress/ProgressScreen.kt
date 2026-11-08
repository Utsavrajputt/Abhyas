@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.invictus.abhyas.ui.components.MainTopBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.AttemptEntity
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.analytics.Analytics
import com.invictus.abhyas.domain.analytics.Dimension
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.domain.query.QuestionFilter
import com.invictus.abhyas.ui.library.appViewModelFactory
import com.invictus.abhyas.ui.math.MathText
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.SectionTitle
import com.invictus.abhyas.ui.screens.ShapeStat
import kotlinx.coroutines.launch

private val TABS = listOf("Overview", "History", "Mistakes", "Saved")

@Composable
fun ProgressScreen(app: AbhyasApp, onOpenResult: (Long) -> Unit, onOpenSession: (Long) -> Unit) {
    val vm: ProgressViewModel = viewModel(factory = appViewModelFactory(app))
    val history by vm.history.collectAsState()
    val stats by vm.stats.collectAsState()
    val mistakes by vm.mistakes.collectAsState()
    val saved by vm.bookmarks.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<AttemptEntity?>(null) }

    fun practice(mode: PracticeMode) {
        scope.launch {
            val filter = mode.apply(QuestionFilter())
            when (val r = app.attemptRepository.startPractice(PracticeConfig(mode, filter, 30))) {
                is StartResult.Started -> onOpenSession(r.attemptId)
                is StartResult.Failed -> message = r.message
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
    MainTopBar(title = "Progress")
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BodyMuted("Results and analytics") }
        item { ConnectedToggleGroup(TABS, tab) { tab = it } }

        val h = history
        if (h == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) } }
        } else when (tab) {
            0 -> overview(h, stats)
            1 -> historyTab(h, onOpenResult) { pendingDelete = it }
            2 -> mistakesTab(vm, mistakes, onPractice = { practice(PracticeMode.INCORRECT) })
            else -> savedTab(vm, saved, onPractice = { practice(PracticeMode.BOOKMARKED) })
        }
        message?.let { item { BodyMuted(it) } }
    }
    }

    pendingDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this result?") },
            text = { Text("${a.title} - this cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteAttempt(a.id); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overview(
    history: List<AttemptEntity>,
    stats: List<com.invictus.abhyas.domain.analytics.StatRow>,
) {
    if (history.isEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("No attempts yet")
                BodyMuted("Your scores, weak topics and the mistake notebook will appear here after your first test.")
            }
        }
        return
    }
    val overall = Analytics.overall(stats)
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val cs = MaterialTheme.colorScheme
            ShapeStat(pct(overall.accuracy), "Accuracy", MaterialShapes.Sunny.toShape(), cs.primaryContainer, cs.onPrimaryContainer)
            ShapeStat("${overall.attempted}", "Answered", MaterialShapes.Flower.toShape(), cs.secondaryContainer, cs.onSecondaryContainer)
            ShapeStat("${history.size}", "Tests", MaterialShapes.Cookie6Sided.toShape(), cs.tertiaryContainer, cs.onTertiaryContainer)
        }
    }
    breakdownSection("By subject", Analytics.breakdown(stats, Dimension.SUBJECT))
    breakdownSection("Theory vs numerical", Analytics.breakdown(stats, Dimension.SUBJECT_KIND))
    breakdownSection("By difficulty", Analytics.breakdown(stats, Dimension.DIFFICULTY))
    val weak = Analytics.weakTopics(stats)
    item { SectionTitle("Weak topics") }
    if (weak.isEmpty()) item { BodyMuted("No weak topics yet. A topic needs at least 3 answered questions with accuracy under 60%.") }
    items(weak, key = { "w-${it.label}" }) { BreakdownRow(it) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.breakdownSection(
    title: String,
    rows: List<com.invictus.abhyas.domain.analytics.Breakdown>,
) {
    if (rows.isEmpty()) return
    item { SectionTitle(title) }
    items(rows, key = { "$title-${it.label}" }) { BreakdownRow(it) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyTab(
    history: List<AttemptEntity>,
    onOpen: (Long) -> Unit,
    onDelete: (AttemptEntity) -> Unit,
) {
    if (history.isEmpty()) { item { BodyMuted("No completed tests yet.") }; return }
    items(history, key = { it.id }) { a ->
        ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(a.id) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleMediumEmphasized)
                    BodyMuted(fmtDate(a.startedAt))
                    val answered = a.correctCount + a.wrongCount
                    BodyMuted(
                        "${fmtMarks(a.score)}/${fmtMarks(a.maxScore)} - " +
                            "${if (answered == 0) "0%" else pct(a.correctCount.toDouble() / answered)} accuracy - " +
                            "${a.correctCount} right, ${a.wrongCount} wrong, ${a.unattemptedCount} skipped",
                    )
                }
                IconButton(onClick = { onDelete(a) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete result") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mistakesTab(
    vm: ProgressViewModel,
    mistakes: List<com.invictus.abhyas.data.local.MistakeRow>,
    onPractice: () -> Unit,
) {
    if (mistakes.isEmpty()) {
        item { BodyMuted("No unresolved mistakes. Wrong answers from tests are collected here; answer one correctly later to clear it.") }
        return
    }
    item {
        Button(onClick = onPractice, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text("Retry mistakes (up to 30)")
        }
    }
    items(mistakes, key = { it.question.id }) { m ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MathText(m.question.statement, maxLines = 4)
                BodyMuted("${m.question.subject}${m.question.chapter?.let { " - $it" } ?: ""} - wrong ${m.wrongCount}x")
                TextButton(onClick = { vm.resolve(m.question.id) }, shapes = ButtonDefaults.shapes()) { Text("Mark resolved") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.savedTab(
    vm: ProgressViewModel,
    saved: List<com.invictus.abhyas.data.local.QuestionEntity>,
    onPractice: () -> Unit,
) {
    if (saved.isEmpty()) {
        item { BodyMuted("No bookmarks yet. Tap the bookmark icon during a test or in the result review.") }
        return
    }
    item {
        Button(onClick = onPractice, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text("Practice bookmarked (up to 30)")
        }
    }
    items(saved, key = { it.id }) { q ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MathText(q.statement, maxLines = 4)
                BodyMuted("${q.subject}${q.chapter?.let { " - $it" } ?: ""}")
                TextButton(onClick = { vm.removeBookmark(q.id) }, shapes = ButtonDefaults.shapes()) { Text("Remove bookmark") }
            }
        }
    }
}
