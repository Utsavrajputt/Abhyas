@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.query.QuestionFilter
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.data.local.AttemptEntity
import com.invictus.abhyas.domain.attempt.DailyPractice
import com.invictus.abhyas.domain.query.KindFilter
import java.time.LocalDate
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.invictus.abhyas.ui.theme.AbhyasShapes
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Home: the daily set on top (random questions, new every day, with a streak), then quick practice
 * and the unfinished test. With no questions it points to the downloads and the bank import.
 */
@Composable
fun HomeScreen(
    app: AbhyasApp,
    onOpenSession: (Long) -> Unit,
    onImportBank: () -> Unit,
    onOpenPyq: () -> Unit,
    onOpenResult: (Long) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val exam = settings.exam
    val inProgress by app.attemptRepository.observeInProgress().collectAsState(initial = null)
    val history by app.database.attemptDao().observeHistory().collectAsState(initial = emptyList())
    val total: Int? by remember(exam) { app.questionRepository.observeCount(QuestionFilter(exam = exam)) }
        .collectAsState<Int, Int?>(initial = null)
    var daily by remember { mutableStateOf<List<Question>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    // The set depends only on the date, the exam and the settings, so it is stable all day.
    LaunchedEffect(exam, total, settings.dailyNumerical, settings.dailyCount, settings.dailyEnabled) {
        if (!settings.dailyEnabled) { daily = emptyList(); return@LaunchedEffect }
        val kind = if (settings.dailyNumerical) KindFilter.ALL else KindFilter.THEORY
        val ids = app.questionRepository.matchingIds(QuestionFilter(exam = exam, kind = kind))
        daily = app.questionRepository.load(DailyPractice.pick(ids, today, settings.dailyCount))
    }

    val doneDays = remember(history) { history.mapNotNull { DailyPractice.dayOf(it.title) }.toSet() }
    val todaysDone = remember(history) { history.firstOrNull { DailyPractice.dayOf(it.title) == today } }
    val todaysOpen = inProgress?.takeIf { DailyPractice.dayOf(it.title) == today }
    val streak = DailyPractice.streak(doneDays, today)

    ExpressiveScreen("Abhyas", "Learn. Practice. Improve.") {
        if (settings.dailyEnabled && daily.isNotEmpty()) {
            DailyCard(
                count = daily.size,
                numerical = settings.dailyNumerical,
                streak = streak,
                week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (it in doneDays) },
                done = todaysDone,
                inProgress = todaysOpen,
                onStart = {
                    scope.launch {
                        when (val r = app.attemptRepository.startFromQuestions(DailyPractice.title(today), PracticeMode.QUICK.name, daily)) {
                            is StartResult.Started -> onOpenSession(r.attemptId)
                            is StartResult.Failed -> message = r.message
                        }
                    }
                },
                onResume = { todaysOpen?.let { onOpenSession(it.id) } },
                onResult = { todaysDone?.let { onOpenResult(it.id) } },
            )
        }

        message?.let { BodyMuted(it) }
        inProgress?.takeIf { DailyPractice.dayOf(it.title) != today }?.let { a -> ContinueCard(app, a.id, a.title, a.currentIndex, onOpenSession) }

        if ((total ?: 0) > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickTile(
                    title = "Quick 20",
                    subtitle = "Random mix",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch {
                        when (val r = app.attemptRepository.startPractice(PracticeConfig(PracticeMode.QUICK, QuestionFilter(exam = exam), 20))) {
                            is StartResult.Started -> onOpenSession(r.attemptId)
                            is StartResult.Failed -> message = r.message
                        }
                    }
                }
                QuickTile(
                    title = "Theory 10",
                    subtitle = "Short round",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch {
                        when (val r = app.attemptRepository.startPractice(PracticeConfig(PracticeMode.QUICK, QuestionFilter(exam = exam, kind = KindFilter.THEORY), 10))) {
                            is StartResult.Started -> onOpenSession(r.attemptId)
                            is StartResult.Failed -> message = r.message
                        }
                    }
                }
            }
        }

        if (total == 0) {
            SectionTitle("Get questions to practise")
            BodyMuted("No ${exam.name.replace('_', ' ')} questions yet. Open Packages for books and previous-year papers, or import a question bank.")
            Button(onClick = onOpenPyq, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Package, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Packages")
            }
            FilledTonalButton(onClick = onImportBank, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Import a question bank") }
        }
    }
}

@Composable
private fun DailyCard(
    count: Int,
    numerical: Boolean,
    streak: Int,
    week: List<Pair<LocalDate, Boolean>>,
    done: AttemptEntity?,
    inProgress: AttemptEntity?,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onResult: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbhyasShapes.L)
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Daily $count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                    Text(
                        if (numerical) "Theory + numerical, new every day" else "Theory questions, new every day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = cs.primary) {
                    Text(
                        "\uD83D\uDD25 $streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            // Last seven days: filled = the daily set was finished that day.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { (day, ok) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (ok) cs.primary else cs.onPrimaryContainer.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) { if (ok) Text("\u2713", color = cs.onPrimary, style = MaterialTheme.typography.labelMedium) }
                        Text(
                            day.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimaryContainer.copy(alpha = if (day == week.last().first) 1f else 0.7f),
                            fontWeight = if (day == week.last().first) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            when {
                done != null -> {
                    Text(
                        "Done today: ${done.correctCount}/${done.correctCount + done.wrongCount + done.unattemptedCount} correct. Come back tomorrow to keep the streak.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer,
                    )
                    OutlinedButton(onClick = onResult, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("View result") }
                }
                inProgress != null -> Button(onClick = onResume, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Play, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Resume today's $count")
                }
                else -> Button(onClick = onStart, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Play, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Start today's $count")
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier, shape = AbhyasShapes.L, color = container) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(AppIcons.Play, contentDescription = null, tint = content)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
        }
    }
}

/** Unfinished test: where you stopped, a progress bar and a Resume button. */
@Composable
private fun ContinueCard(app: AbhyasApp, attemptId: Long, title: String, currentIndex: Int, onOpenSession: (Long) -> Unit) {
    val total by androidx.compose.runtime.produceState(initialValue = 0, attemptId) {
        value = app.database.attemptDao().getQuestions(attemptId).size
    }
    androidx.compose.material3.Surface(
        shape = AbhyasShapes.L,
        color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Continue where you left off", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (total > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { ((currentIndex + 1).coerceAtMost(total)).toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Question ${(currentIndex + 1).coerceAtMost(total)} of $total",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = { onOpenSession(attemptId) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Play, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Resume")
            }
        }
    }
}
