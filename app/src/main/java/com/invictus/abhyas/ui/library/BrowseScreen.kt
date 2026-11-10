@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.invictus.abhyas.ui.components.AbhyasTopBar
import com.invictus.abhyas.ui.components.BackNavigationIcon
import com.invictus.abhyas.ui.components.LocalBackAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.LaunchedEffect
import com.invictus.abhyas.ui.components.InfoPill
import com.invictus.abhyas.ui.components.SearchPill
import com.invictus.abhyas.ui.theme.AbhyasShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.query.KindFilter
import com.invictus.abhyas.ui.math.MathText
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup

@Composable
fun BrowseScreen(app: AbhyasApp) {
    val vm: BrowseViewModel = viewModel(factory = appViewModelFactory(app))
    val count by vm.count.collectAsState()
    val paging = vm.questions.collectAsLazyPagingItems()


    val back = LocalBackAction.current
    // Questions are searched across every exam; the NEET / JEE switch is gone from this screen.
    LaunchedEffect(Unit) { vm.update { it.copy(exam = null) } }
    Column(Modifier.fillMaxSize()) {
        AbhyasTopBar(
            title = { Text("Questions") },
            navigationIcon = { back?.let { BackNavigationIcon(onBack = it) } },
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        FilterControls(vm, showSearch = true, showExam = false)

        Text(
            if (count == 1) "1 question" else "$count questions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.fillMaxSize()) {
            when {
                paging.loadState.refresh is LoadState.Loading && paging.itemCount == 0 ->
                    LoadingIndicator(Modifier.align(Alignment.Center).size(72.dp))
                paging.itemCount == 0 ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No questions found", style = MaterialTheme.typography.titleLargeEmphasized)
                        BodyMuted("Try clearing a filter or importing a bank.")
                    }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = paging.itemCount, key = paging.itemKey { it.id }) { index ->
                        paging[index]?.let { QuestionRow(it) }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun QuestionRow(q: Question) {
    Surface(shape = AbhyasShapes.M, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MathText(q.statement, maxLines = 4)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull(
                    q.subject,
                    q.chapter,
                    q.difficulty?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() },
                    q.type.label(),
                    q.year?.toString(),
                    if (q.answerVerified) null else "Answer unverified",
                ).forEach { InfoPill(it) }
            }
        }
    }
}

@Composable
internal fun FilterDropdown(label: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Any") }, onClick = { onSelect(null); open = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); open = false })
            }
        }
    }
}

private fun QuestionType.label(): String = when (this) {
    QuestionType.SINGLE_MCQ -> "Single MCQ"
    QuestionType.MULTI_MCQ -> "Multi MCQ"
    QuestionType.NUMERICAL -> "Numerical"
    QuestionType.TRUE_FALSE -> "True/False"
    QuestionType.ASSERTION_REASON -> "Assertion-Reason"
    QuestionType.MATCH -> "Match"
    QuestionType.FILL_BLANK -> "Fill blank"
    QuestionType.IMAGE -> "Image"
}

/** Search + exam/kind toggles + dropdown filters bound to a [BrowseViewModel]; shared by Browse and practice setup. */
@Composable
internal fun FilterControls(vm: BrowseViewModel, showSearch: Boolean, showExam: Boolean = true) {
    val filter by vm.filter.collectAsState()
    val subjects by vm.subjects.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val topics by vm.topics.collectAsState()
    val years by vm.years.collectAsState()
    val exams = listOf<Exam?>(null, Exam.NEET, Exam.JEE_MAIN)
    val kinds = KindFilter.entries

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showSearch) SearchPill(
                value = filter.query,
                onValueChange = { q -> vm.update { it.copy(query = q) } },
                placeholder = "Search questions, chapters, topics",
            )

            if (showExam) ConnectedToggleGroup(listOf("All", "NEET", "JEE Main"), exams.indexOf(filter.exam)) { i ->
                vm.update { it.copy(exam = exams[i]) }
            }
            ConnectedToggleGroup(listOf("All", "Theory", "Numerical"), kinds.indexOf(filter.kind)) { i ->
                vm.update { it.copy(kind = kinds[i]) }
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterDropdown("Class", listOf("11", "12"), filter.classLevel?.toString()) { v ->
                    vm.update { it.copy(classLevel = v?.toInt()) }
                }
                FilterDropdown("Subject", subjects, filter.subject) { v ->
                    vm.update { it.copy(subject = v, chapter = null, topic = null) }
                }
                FilterDropdown("Chapter", chapters, filter.chapter) { v ->
                    vm.update { it.copy(chapter = v, topic = null) }
                }
                FilterDropdown("Topic", topics, filter.topic) { v -> vm.update { it.copy(topic = v) } }
                FilterDropdown(
                    "Type",
                    QuestionType.entries.map { it.label() },
                    filter.type?.label(),
                ) { v -> vm.update { f -> f.copy(type = QuestionType.entries.firstOrNull { it.label() == v }) } }
                FilterDropdown(
                    "Difficulty",
                    Difficulty.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercaseChar() } },
                    filter.difficulty?.name?.lowercase()?.replaceFirstChar { c -> c.uppercaseChar() },
                ) { v -> vm.update { f -> f.copy(difficulty = Difficulty.entries.firstOrNull { it.name.equals(v, true) }) } }
                FilterDropdown("Year", years.map { it.toString() }, filter.year?.toString()) { v ->
                    vm.update { it.copy(year = v?.toInt()) }
                }
            }
    }
}
