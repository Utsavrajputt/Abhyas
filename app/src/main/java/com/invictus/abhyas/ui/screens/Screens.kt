@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.invictus.abhyas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.prefs.SettingsStore
import com.invictus.abhyas.data.prefs.ThemeMode
import com.invictus.abhyas.data.prefs.UserSettings
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Shared expressive building blocks
// ---------------------------------------------------------------------------------------------

/** Screen frame: simple title + subtitle header, scrollable content (+ optional FAB). */
@Composable
private fun ExpressiveScreen(
    title: String,
    subtitle: String,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        // The root scaffold already pads for the bottom navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized)

@Composable
private fun BodyMuted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Connected expressive toggle-button group (single selection). */
@Composable
private fun ConnectedToggleGroup(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        items.forEachIndexed { i, label ->
            val shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selected == i,
                onCheckedChange = { onSelect(i) },
                shapes = shapes,
                modifier = Modifier.weight(1f),
            ) { Text(label) }
        }
    }
}

/** A stat tile drawn inside a MaterialShapes container. */
@Composable
private fun ShapeStat(
    value: String,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier.size(104.dp).clip(shape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMediumEmphasized, color = onContainer)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(database: AbhyasDatabase) {
    val total: Int? by database.questionDao().observeCount().collectAsState<Int, Int?>(initial = null)
    var exam by remember { mutableStateOf(0) }
    val exams = listOf("NEET", "JEE Main")
    val hasQuestions = (total ?: 0) > 0

    ExpressiveScreen("Abhyas", "Learn. Practice. Improve.") {
        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally).size(168.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (total == null) {
                LoadingIndicator(modifier = Modifier.size(96.dp))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${total ?: 0}",
                            style = MaterialTheme.typography.displayMediumEmphasized,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "questions",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        SectionTitle("Exam")
        ConnectedToggleGroup(exams, exam) { exam = it }

        Button(
            onClick = { /* Quick Practice arrives in Milestone 1 */ },
            enabled = hasQuestions,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Quick practice - ${exams[exam]}", style = MaterialTheme.typography.titleMediumEmphasized)
        }

        if (total == 0) {
            FilledTonalButton(
                onClick = { /* Bank import arrives in Milestone 1 */ },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import a question bank") }
            BodyMuted("No question banks imported yet. Import a bank or a PDF to start practising.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryScreen(database: AbhyasDatabase) {
    val total: Int? by database.questionDao().observeCount().collectAsState<Int, Int?>(initial = null)
    var filter by remember { mutableStateOf(0) }

    ExpressiveScreen("Library", "NEET, JEE Main, NCERT") {
        ConnectedToggleGroup(listOf("All", "NEET", "JEE Main", "NCERT"), filter) { filter = it }

        when (total) {
            null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(72.dp))
            }
            0 -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(MaterialShapes.Clover4Leaf.toShape())
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                )
                Text("Nothing here yet", style = MaterialTheme.typography.titleLargeEmphasized)
                BodyMuted("Installed question banks and NCERT textbooks will appear here.")
            }
            else -> BodyMuted("$total questions available offline.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------------------------

@Composable
fun CreateScreen() {
    var expanded by remember { mutableStateOf(false) }
    ExpressiveScreen(
        title = "Create",
        subtitle = "Custom practice, PDF Studio",
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = expanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = expanded,
                        onCheckedChange = { expanded = it },
                    ) {
                        Icon(
                            if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
                            contentDescription = if (expanded) "Close menu" else "Create",
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { expanded = false },
                    icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    text = { Text("Custom practice") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { expanded = false },
                    icon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                    text = { Text("Import PDF") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { expanded = false },
                    icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                    text = { Text("Import question bank") },
                )
            }
        },
    ) {
        SectionTitle("What you can create")
        BodyMuted("Custom practice sessions, PDF imports and question-bank imports. Use the + button to start.")
        BodyMuted("These actions are wired up in Milestones 1 and 2.")
    }
}

// ---------------------------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------------------------

@Composable
fun ProgressScreen() {
    val cs = MaterialTheme.colorScheme
    ExpressiveScreen("Progress", "Results and analytics") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ShapeStat("-", "Accuracy", MaterialShapes.Sunny.toShape(), cs.primaryContainer, cs.onPrimaryContainer)
            ShapeStat("-", "Attempted", MaterialShapes.Flower.toShape(), cs.secondaryContainer, cs.onSecondaryContainer)
            ShapeStat("-", "Streak", MaterialShapes.Cookie6Sided.toShape(), cs.tertiaryContainer, cs.onTertiaryContainer)
        }
        SectionTitle("No attempts yet")
        BodyMuted("Your scores, weak topics and the mistake notebook will appear here after your first test.")
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(store: SettingsStore) {
    val settings by store.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()
    val modes = ThemeMode.entries

    ExpressiveScreen("Settings", "Theme, exam templates, AI") {
        SectionTitle("Theme")
        ConnectedToggleGroup(
            items = listOf("System", "Light", "Dark"),
            selected = modes.indexOf(settings.themeMode),
        ) { scope.launch { store.setThemeMode(modes[it]) } }

        SwitchRow(
            title = "Dynamic colour",
            description = "Match your wallpaper (Android 12+)",
            checked = settings.dynamicColor,
        ) { scope.launch { store.setDynamicColor(it) } }

        SectionTitle("AI (optional)")
        SwitchRow(
            title = "Enable AI features",
            description = "Off by default. Practice, scoring and reading never need AI. AI content is always shown as unverified.",
            checked = settings.aiEnabled,
        ) { scope.launch { store.setAiEnabled(it) } }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
