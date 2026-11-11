@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.invictus.abhyas.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material3.OutlinedButton
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.repo.ReviewItem
import com.invictus.abhyas.domain.analytics.Analytics
import com.invictus.abhyas.domain.analytics.AnswerFormatter
import com.invictus.abhyas.domain.analytics.Dimension
import com.invictus.abhyas.domain.model.Verdict
import com.invictus.abhyas.ui.library.FilterDropdown
import com.invictus.abhyas.ui.common.QuestionImage
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

private val GoodColor = Color(0xFF3FB97A)
private val WarnColor = Color(0xFFE8A93A)
private val InfoColor = Color(0xFF5B9DF5)

@Composable
fun ResultScreen(app: AbhyasApp, attemptId: Long, onDone: () -> Unit, onOpenNcert: (String, Int, Int) -> Unit = { _, _, _ -> }) {
    val vm: com.invictus.abhyas.ui.progress.ResultViewModel =
        viewModel(key = "result$attemptId", factory = resultViewModelFactory(app, attemptId))
    val ui by vm.ui.collectAsState()
    val bookmarked by vm.bookmarkedIds.collectAsState()
    val save by vm.save.collectAsState()
    val a = ui.attempt

    var dim by remember { mutableStateOf(Dimension.SUBJECT) }
    var reviewFilter by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf(setOf<Int>()) }
    var showBreakdown by remember { mutableStateOf(false) }

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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Result", style = MaterialTheme.typography.headlineMedium)
                BodyMuted("${a.title} - ${fmtDate(a.finishedAt ?: a.startedAt)}")
            }
        }
        // Hero: score + accuracy side by side, then four coloured tiles.
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(pct(overall.accuracy), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${fmtMarks(a.score)} / ${fmtMarks(a.maxScore)}", style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("marks - avg ${fmtDuration(overall.avgTimeMs)} per question", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (a.unscoredCount > 0) Text("${a.unscoredCount} unscored (no verified answer)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(Modifier.weight(1f), "${a.correctCount}", "Correct", GoodColor)
                StatTile(Modifier.weight(1f), "${a.wrongCount}", "Wrong", MaterialTheme.colorScheme.error)
                StatTile(Modifier.weight(1f), "${a.unattemptedCount}", "Skipped", WarnColor)
                StatTile(Modifier.weight(1f), "${a.correctCount + a.wrongCount + a.unattemptedCount}", "Total", InfoColor)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (save.unsaved > 0) {
                    OutlinedButton(
                        onClick = vm::saveToBank,
                        enabled = !save.busy,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save ${save.unsaved} question${if (save.unsaved == 1) "" else "s"} to my bank") }
                }
                save.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                Button(onClick = onDone, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }

        // Breakdown is tucked away until asked for: the review is what students come for.
        item {
            Row(Modifier.fillMaxWidth().clickable { showBreakdown = !showBreakdown }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Breakdown") }
                Icon(if (showBreakdown) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
        if (showBreakdown) {
            item {
                FilterDropdown("Group by", Dimension.entries.map { it.label }, dim.label) { v ->
                    dim = Dimension.entries.firstOrNull { it.label == v } ?: Dimension.SUBJECT
                }
            }
            val rows = Analytics.breakdown(ui.stats, dim)
            if (rows.isEmpty()) item { BodyMuted("Nothing to show.") }
            items(rows, key = { "b-${dim.name}-${it.label}" }) { BreakdownRow(it) }
        }

        item { SectionTitle("Review") }
        // Question-number map: colour = verdict, tap to jump open that question.
        item {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.review.forEach { r ->
                    val c = verdictColor(r.verdict)
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.22f))
                            .clickable { open = open + r.position; reviewFilter = 0 },
                        contentAlignment = Alignment.Center,
                    ) { Text("${r.position + 1}", style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                REVIEW_FILTERS.forEachIndexed { i, label ->
                    FilterChip(selected = reviewFilter == i, onClick = { reviewFilter = i }, label = { Text(label) })
                }
            }
        }
        if (shown.isEmpty()) item { BodyMuted("No questions in this filter.") }
        items(shown, key = { "r-${it.position}" }) { r ->
            ReviewRow(
                app, r, r.position in open, r.question.id in bookmarked,
                onToggle = { open = if (r.position in open) open - r.position else open + r.position },
                onBookmark = { vm.toggleBookmark(r.question.id) },
                onOpenNcert = onOpenNcert,
            )
        }
        item {
            Button(onClick = onDone, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.CORRECT -> GoodColor
    Verdict.WRONG -> MaterialTheme.colorScheme.error
    Verdict.UNATTEMPTED -> WarnColor
    Verdict.UNSCORED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun StatTile(modifier: Modifier, value: String, label: String, color: Color) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.16f)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One question: a single tidy line while closed, everything (answers, explanation, book, AI) when opened. */
@Composable
private fun ReviewRow(
    app: AbhyasApp, r: ReviewItem, expanded: Boolean, bookmarked: Boolean,
    onToggle: () -> Unit, onBookmark: () -> Unit, onOpenNcert: (String, Int, Int) -> Unit,
) {
    val q = r.question
    val c = verdictColor(r.verdict)
    val verdictText = when (r.verdict) {
        Verdict.CORRECT -> "Correct"
        Verdict.WRONG -> "Wrong"
        Verdict.UNATTEMPTED -> "Skipped"
        Verdict.UNSCORED -> "Unscored"
    }
    val aiReady by app.aiGateway.ready.collectAsState(initial = false)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var aiRequest by remember { mutableStateOf<com.invictus.abhyas.ui.ai.AiRequestUi?>(null) }

    Surface(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(c))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Q${r.position + 1} - $verdictText ${if (r.marks != 0.0) "(${if (r.marks > 0) "+" else ""}${fmtMarks(r.marks)}) " else ""}- ${fmtDuration(r.timeSpentMs)}" +
                                if ("ai-generated" in q.tags) " - AI" else "",
                            style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.SemiBold,
                        )
                        if (!expanded) Text(q.statement, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onBookmark) {
                        Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark")
                    }
                }
                if (expanded) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MathText(q.statement)
                        QuestionImage(q.imagePath)
                        AnswerLine("Your answer", AnswerFormatter.given(q, r.response), c)
                        AnswerLine("Correct answer", AnswerFormatter.correct(q) ?: "Not available (answer key missing)", GoodColor)
                        q.explanation?.takeIf { it.isNotBlank() }?.let {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("Explanation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    MathText(it)
                                }
                            }
                        }
                        com.invictus.abhyas.ui.ncert.NcertRefCard(app, q, onOpenNcert)
                        if (aiReady) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    scope.launch {
                                        // Ground the answer in the book when the reference still resolves.
                                        val passage = q.ncertRef?.let { app.books.resolve(it)?.text }
                                        aiRequest = com.invictus.abhyas.ui.ai.AiRequestUi(
                                            feature = com.invictus.abhyas.domain.ai.AiFeature.EXPLAIN,
                                            context = passage.orEmpty(),
                                            title = "Q${r.position + 1}",
                                            prompt = com.invictus.abhyas.domain.ai.AiPrompts.explainAnswer(
                                                q, AnswerFormatter.given(q, r.response), AnswerFormatter.correct(q), passage,
                                            ),
                                        )
                                    }
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Explain with AI") }
                        }
                    }
                }
            }
        }
    }
    aiRequest?.let { com.invictus.abhyas.ui.ai.AiSheet(app, it, onDismiss = { aiRequest = null }) }
}

@Composable
private fun AnswerLine(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Box(Modifier.weight(1f)) { MathText(value) }
    }
}
