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
import com.invictus.abhyas.ui.theme.AbhyasShapes
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Subjects of an exam as (label, subject names used in question banks). Biology also covers Botany/Zoology. */
private fun Exam.subjectGroups(): List<Pair<String, List<String>>> = when (this) {
    Exam.NEET -> listOf(
        "Physics" to listOf("Physics"),
        "Chemistry" to listOf("Chemistry"),
        "Biology" to listOf("Biology", "Botany", "Zoology"),
    )
    Exam.JEE_MAIN -> listOf(
        "Physics" to listOf("Physics"),
        "Chemistry" to listOf("Chemistry"),
        "Mathematics" to listOf("Mathematics", "Maths"),
    )
}

/**
 * Home: today's three-question practice (one random question per subject of the exam chosen in
 * Settings; the same three all day), quick practice, resume. When there are no questions it points
 * to the downloads and the question-bank import.
 */
@Composable
fun HomeScreen(
    app: AbhyasApp,
    onOpenSession: (Long) -> Unit,
    onImportBank: () -> Unit,
    onOpenPyq: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val exam = settings.exam
    val inProgress by app.attemptRepository.observeInProgress().collectAsState(initial = null)
    val total: Int? by remember(exam) { app.questionRepository.observeCount(QuestionFilter(exam = exam)) }
        .collectAsState<Int, Int?>(initial = null)
    var today by remember { mutableStateOf<List<Question>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    // One random question per subject, stable for the day (changes at midnight or when questions change).
    LaunchedEffect(exam, total) {
        val day = System.currentTimeMillis() / 86_400_000L
        val picked = ArrayList<String>()
        exam.subjectGroups().forEach { (label, names) ->
            val ids = names.flatMap { app.questionRepository.matchingIds(QuestionFilter(exam = exam, subject = it)) }
                .distinct()
                .sorted()
            if (ids.isNotEmpty()) {
                picked += ids[Random(day * 31L + label.hashCode()).nextInt(ids.size)]
            }
        }
        today = if (picked.isEmpty()) emptyList() else app.questionRepository.load(picked)
    }

    ExpressiveScreen("Abhyas", "Learn. Practice. Improve.") {
        if (today.isNotEmpty()) {
            TodayCard(
                questions = today,
                onStart = {
                    scope.launch {
                        when (val r = app.attemptRepository.startFromQuestions("Today's practice", PracticeMode.QUICK.name, today)) {
                            is StartResult.Started -> onOpenSession(r.attemptId)
                            is StartResult.Failed -> message = r.message
                        }
                    }
                },
            )
        }

        if ((total ?: 0) > 0) {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        when (val r = app.attemptRepository.startPractice(
                            PracticeConfig(PracticeMode.QUICK, QuestionFilter(exam = exam), 20),
                        )) {
                            is StartResult.Started -> onOpenSession(r.attemptId)
                            is StartResult.Failed -> message = r.message
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Play, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Quick practice - 20 questions")
            }
        }

        message?.let { BodyMuted(it) }
        inProgress?.let { a ->
            Button(
                onClick = { onOpenSession(a.id) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Resume: ${a.title}") }
        }

        if (total == 0) {
            SectionTitle("Get questions to practise")
            BodyMuted("No ${exam.name.replace('_', ' ')} questions yet. Open Downloads for books and previous-year papers, or import a question bank.")
            Button(
                onClick = onOpenPyq,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Package, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Downloads")
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
private fun TodayCard(questions: List<Question>, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbhyasShapes.L)
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Today's practice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer,
                )
                Text(
                    "${questions.size} questions, one from each subject",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            questions.forEach { q ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(cs.primary),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            q.subject,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onPrimaryContainer,
                        )
                        Text(
                            q.statement.replace(Regex("\\s+"), " ").trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Button(
                onClick = onStart,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Play, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Start today's practice")
            }
        }
    }
}
