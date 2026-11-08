@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.query.QuestionFilter
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.AbhyasShapes
import kotlinx.coroutines.launch

/**
 * Home, built around the app-wide exam: pick NEET or JEE Main, see what you have for it,
 * start a quick or per-subject practice, resume, or go get previous-year papers.
 */
@Composable
fun HomeScreen(
    app: AbhyasApp,
    onOpenSession: (Long) -> Unit,
    onImportBank: () -> Unit,
    onOpenPyq: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val exam = settings.exam
    val inProgress by app.attemptRepository.observeInProgress().collectAsState(initial = null)
    val subjects by app.database.questionDao().observeSubjects().collectAsState(initial = emptyList())
    val count: Int? by remember(exam) { app.questionRepository.observeCount(QuestionFilter(exam = exam)) }
        .collectAsState<Int, Int?>(initial = null)
    var message by remember { mutableStateOf<String?>(null) }

    fun start(subject: String?) {
        scope.launch {
            when (val r = app.attemptRepository.startPractice(
                PracticeConfig(PracticeMode.QUICK, QuestionFilter(exam = exam, subject = subject), 20),
            )) {
                is StartResult.Started -> onOpenSession(r.attemptId)
                is StartResult.Failed -> message = r.message
            }
        }
    }

    ExpressiveScreen("Abhyas", "Learn. Practice. Improve.") {
        ExamPicker(selected = exam, onSelect = { scope.launch { store.setExam(it) } })

        ExamHero(exam = exam, count = count)

        if ((count ?: 0) > 0) {
            SectionTitle("Practice by subject")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subjects.forEach { subject ->
                    SubjectChip(app = app, exam = exam, subject = subject, onStart = { start(subject) })
                }
            }

            Button(
                onClick = { start(null) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Play, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Quick practice - ${exam.info().title}", style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }

        message?.let { BodyMuted(it) }
        inProgress?.let { a ->
            FilledTonalButton(
                onClick = { onOpenSession(a.id) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Resume: ${a.title}") }
        }

        if (count == 0) {
            SectionTitle("Get questions for ${exam.info().title}")
            BodyMuted("No ${exam.info().title} questions yet. Download previous-year papers or import a question bank.")
            Button(
                onClick = onOpenPyq,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Package, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Previous year papers")
            }
            FilledTonalButton(
                onClick = onImportBank,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import a question bank") }
        }
    }
}

@Composable
private fun ExamHero(exam: Exam, count: Int?) {
    val info = exam.info()
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbhyasShapes.L)
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Your exam",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer,
                )
                Text(text = info.subjects, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
                Text(
                    text = if (count == null) "Loading questions..." else if (count == 0) "No questions yet" else "Ready to practise",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${count ?: 0}",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = cs.onPrimary,
                    )
                    Text("questions", style = MaterialTheme.typography.labelSmall, color = cs.onPrimary)
                }
            }
        }
    }
}

/** A subject with its question count for [exam]; hidden when this exam has none in that subject. */
@Composable
private fun SubjectChip(app: AbhyasApp, exam: Exam, subject: String, onStart: () -> Unit) {
    val n: Int by remember(exam, subject) {
        app.questionRepository.observeCount(QuestionFilter(exam = exam, subject = subject))
    }.collectAsState(initial = 0)
    if (n > 0) {
        AssistChip(onClick = onStart, label = { Text("$subject · $n") })
    }
}
