@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.invictus.abhyas.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.repo.SessionItem
import com.invictus.abhyas.domain.attempt.PaletteStatus
import com.invictus.abhyas.domain.attempt.paletteStatus
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.hasAnswer
import com.invictus.abhyas.ui.common.QuestionImage
import com.invictus.abhyas.ui.math.MathText
import com.invictus.abhyas.ui.screens.BodyMuted

@Composable
fun SessionScreen(app: AbhyasApp, attemptId: Long, onFinished: () -> Unit, onExit: () -> Unit) {
    val vm: SessionViewModel = viewModel(key = "session$attemptId", factory = sessionViewModelFactory(app, attemptId))
    val ui by vm.ui.collectAsState()
    val remaining by vm.remainingMs.collectAsState()
    val bookmarked by vm.bookmarkedIds.collectAsState()

    var showPalette by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resume() }
    LaunchedEffect(ui.finished) { if (ui.finished) onFinished() }
    BackHandler(enabled = !ui.finished) { confirmExit = true }

    val item = ui.current
    when {
        ui.error != null -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(ui.error.orEmpty(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onExit) { Text("Back") }
        }
        ui.loading || item == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(72.dp))
        }
        else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header: progress, timer, bookmark, palette.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Question ${ui.index + 1} of ${ui.items.size}",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        ui.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                remaining?.let { TimerBadge(it) }
                IconButton(onClick = vm::toggleBookmark) {
                    val on = item.question.id in bookmarked
                    Icon(
                        if (on) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (on) "Remove bookmark" else "Bookmark",
                    )
                }
                IconButton(onClick = { showPalette = true }) {
                    Icon(Icons.Outlined.GridView, contentDescription = "Question palette")
                }
            }

            // The question comes first; nothing competes with it.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if ("ai-generated" in item.question.tags) {
                    Text("AI-written - unverified, not scored", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
                MathText(item.question.statement, style = MaterialTheme.typography.titleMedium)
                QuestionImage(item.question.imagePath)
                if (item.question.type == QuestionType.IMAGE && item.question.imagePath.isNullOrBlank()) {
                    BodyMuted("Image-based question (image not available).")
                }
                AnswerInput(item.question, item.response, vm::answer)
                Text(
                    "+${trim(item.question.marks)} / -${trim(item.question.negativeMarks)}" +
                        if (!item.question.answerVerified || !item.question.payload.hasAnswer()) " - answer unverified, not scored" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Navigation bar.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = vm::previous, enabled = ui.index > 0, shapes = ButtonDefaults.shapes()) {
                    Text("Prev")
                }
                TextButton(onClick = vm::toggleMarked, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                    Text(if (item.marked) "  Unmark" else "  Mark")
                }
                TextButton(
                    onClick = { vm.answer(null) },
                    enabled = item.response != null,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Clear") }
                Box(Modifier.weight(1f))
                if (ui.index < ui.items.lastIndex) {
                    Button(onClick = vm::next, shapes = ButtonDefaults.shapes()) { Text("Next") }
                } else {
                    Button(
                        onClick = { confirmSubmit = true },
                        enabled = !ui.submitting,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Submit") }
                }
            }
        }
    }

    if (showPalette) {
        ModalBottomSheet(onDismissRequest = { showPalette = false }) {
            Palette(ui.items, ui.index, onPick = { vm.goTo(it); showPalette = false }, onSubmit = {
                showPalette = false
                confirmSubmit = true
            })
        }
    }

    if (confirmSubmit) {
        val answered = ui.items.count { it.response != null }
        val marked = ui.items.count { it.marked }
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("Submit test?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Answered: $answered of ${ui.items.size}")
                    Text("Not answered: ${ui.items.size - answered}")
                    if (marked > 0) Text("Marked for review: $marked")
                    Text("You cannot change answers after submitting.")
                }
            },
            confirmButton = { TextButton(onClick = { confirmSubmit = false; vm.submit() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Keep working") } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave this test?") },
            text = {
                Text(
                    "Your answers are saved and you can resume from Home. " +
                        "A timed test keeps running while you are away.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.pause(); onExit() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
        )
    }
}

private fun trim(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else v.toString()

@Composable
private fun TimerBadge(ms: Long) {
    val totalSec = (ms / 1000).toInt()
    val low = totalSec < 60
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    Box(
        Modifier
            .clip(MaterialShapes.Pill.toShape())
            .background(if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Time left: " + (if (h > 0) "$h hours, " else "") + "$m minutes, $s seconds" },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun statusText(s: PaletteStatus): String = when (s) {
    PaletteStatus.NOT_VISITED -> "not visited"
    PaletteStatus.NOT_ANSWERED -> "not answered"
    PaletteStatus.ANSWERED -> "answered"
    PaletteStatus.MARKED -> "marked for review"
    PaletteStatus.ANSWERED_MARKED -> "answered and marked for review"
}

@Composable
private fun Palette(items: List<SessionItem>, current: Int, onPick: (Int) -> Unit, onSubmit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    @Composable
    fun look(s: PaletteStatus): Triple<Shape, Color, Color> = when (s) {
        PaletteStatus.NOT_VISITED -> Triple(MaterialShapes.Square.toShape(), cs.surfaceVariant, cs.onSurfaceVariant)
        PaletteStatus.NOT_ANSWERED -> Triple(MaterialShapes.Circle.toShape(), cs.errorContainer, cs.onErrorContainer)
        PaletteStatus.ANSWERED -> Triple(MaterialShapes.Cookie6Sided.toShape(), cs.primaryContainer, cs.onPrimaryContainer)
        PaletteStatus.MARKED -> Triple(MaterialShapes.Diamond.toShape(), cs.tertiaryContainer, cs.onTertiaryContainer)
        PaletteStatus.ANSWERED_MARKED -> Triple(MaterialShapes.Flower.toShape(), cs.tertiaryContainer, cs.onTertiaryContainer)
    }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Question palette", style = MaterialTheme.typography.titleMediumEmphasized)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(52.dp),
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items) { i, item ->
                val status = paletteStatus(item.visited, item.response != null, item.marked)
                val (shape, bg, fg) = look(status)
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(shape)
                        .background(bg)
                        .then(if (i == current) Modifier.border(2.dp, cs.primary, shape) else Modifier)
                        .semantics {
                            // TalkBack reads the state, not just the number; colour and shape are never the only cue.
                            contentDescription = "Question ${i + 1}, ${statusText(status)}" + if (i == current) ", current" else ""
                            selected = i == current
                        }
                        .clickable(onClickLabel = "Go to question ${i + 1}", role = Role.Button) { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = fg, style = MaterialTheme.typography.labelLarge) }
            }
        }
        // Legend: meaning is never carried by colour alone.
        Text(
            "Square: not visited - Circle: not answered - Cookie: answered - Diamond: marked - Flower: answered + marked",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )
        Button(onClick = onSubmit, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text("Submit test")
        }
    }
}
