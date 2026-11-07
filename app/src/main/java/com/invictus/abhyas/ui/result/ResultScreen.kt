@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.repo.ReviewItem
import com.invictus.abhyas.domain.analytics.Analytics
import com.invictus.abhyas.domain.analytics.AnswerFormatter
import com.invictus.abhyas.domain.analytics.Dimension
import com.invictus.abhyas.domain.model.Verdict
import com.invictus.abhyas.ui.library.FilterDropdown
import com.invictus.abhyas.ui.math.MathText
import com.invictus.abhyas.ui.progress.BreakdownRow
import com.invictus.abhyas.ui.progress.fmtDate
import com.invictus.abhyas.ui.progress.fmtDuration
import com.invictus.abhyas.ui.progress.fmtMarks
import com.invictus.abhyas.ui.progress.pct
import com.invictus.abhyas.ui.progress.resultViewModelFactory
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.SectionTitle

private val REVIEW_FILTERS = listOf("All", "Wrong", "Skipped", "Correct")

@Composable
fun ResultScreen(app: AbhyasApp, attemptId: Long, onDone: () -> Unit) {
    val vm: com.invictus.abhyas.ui.progress.ResultViewModel =
        viewModel(key = "result$attemptId", factory = resultViewModelFactory(app, attemptId))
    val ui by vm.ui.collectAsState()
    val bookmarked by vm.bookmarkedIds.collectAsState()
    val a = ui.attempt

    var dim by remember { mutableStateOf(Dimension.SUBJECT) }
    var reviewFilter by remember { mutableStateOf(0) }

    if (ui.loading || a == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (ui.loading) LoadingIndicator(Modifier.size(72.dp)) else BodyMuted("Result not found.")
        }
        return
    }

    val overall = Analytics.overall(ui.stats)
    val shown = when (reviewFilter) {
        1 -> ui.review.filter { it.verdict == Verdict.WRONG }
        2 -> ui.review.filter { it.verdict == Verdict.UNATTEMPTED }
        3 -> ui.review.filter { it.verdict == Verdict.CORRECT }
        else -> ui.review
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Result", style = MaterialTheme.typography.headlineMedium)
                BodyMuted("${a.title} - ${fmtDate(a.finishedAt ?: a.startedAt)}")
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(180.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtMarks(a.score), style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("of ${fmtMarks(a.maxScore)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tile("${a.correctCount}", "Correct")
                    Tile("${a.wrongCount}", "Wrong")
                    Tile("${a.unattemptedCount}", "Skipped")
                    Tile(pct(overall.accuracy), "Accuracy")
                }
                BodyMuted(
                    "Avg ${fmtDuration(overall.avgTimeMs)} per question" +
                        if (a.unscoredCount > 0) " - ${a.unscoredCount} unscored (no verified answer)" else "",
                )
                Button(onClick = onDone, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Breakdown")
                FilterDropdown("Group by", Dimension.entries.map { it.label }, dim.label) { v ->
                    dim = Dimension.entries.firstOrNull { it.label == v } ?: Dimension.SUBJECT
                }
            }
        }
        val rows = Analytics.breakdown(ui.stats, dim)
        if (rows.isEmpty()) item { BodyMuted("Nothing to show.") }
        items(rows, key = { "b-${dim.name}-${it.label}" }) { BreakdownRow(it) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Review")
                ConnectedToggleGroup(REVIEW_FILTERS, reviewFilter) { reviewFilter = it }
            }
        }
        if (shown.isEmpty()) item { BodyMuted("No questions in this filter.") }
        items(shown, key = { "r-${it.position}" }) { r ->
            ReviewCard(r, r.question.id in bookmarked) { vm.toggleBookmark(r.question.id) }
        }
        item {
            Button(onClick = onDone, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun Tile(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewCard(r: ReviewItem, bookmarked: Boolean, onBookmark: () -> Unit) {
    val q = r.question
    val verdictText = when (r.verdict) {
        Verdict.CORRECT -> "Correct"
        Verdict.WRONG -> "Wrong"
        Verdict.UNATTEMPTED -> "Not attempted"
        Verdict.UNSCORED -> "Unscored (no verified answer)"
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Q${r.position + 1} - $verdictText (${if (r.marks > 0) "+" else ""}${fmtMarks(r.marks)}) - ${fmtDuration(r.timeSpentMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = when (r.verdict) {
                        Verdict.CORRECT -> MaterialTheme.colorScheme.primary
                        Verdict.WRONG -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onBookmark) {
                    Icon(
                        if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                    )
                }
            }
            MathText(q.statement)
            Text("Your answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MathText(AnswerFormatter.given(q, r.response))
            Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MathText(AnswerFormatter.correct(q) ?: "Not available (answer key missing)")
            q.explanation?.takeIf { it.isNotBlank() }?.let {
                Text("Explanation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MathText(it)
            }
        }
    }
}
