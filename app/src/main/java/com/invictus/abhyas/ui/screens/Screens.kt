package com.invictus.abhyas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.data.local.AbhyasDatabase

@Composable
private fun ScreenScaffold(title: String, subtitle: String, content: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
fun HomeScreen(database: AbhyasDatabase) {
    val total by database.questionDao().observeCount().collectAsState(initial = 0)
    ScreenScaffold("Abhyas", "Learn. Practice. Improve.") {
        Text(
            "Questions in local bank: $total",
            style = MaterialTheme.typography.titleMedium,
        )
        if (total == 0) {
            Text(
                "No question banks imported yet. Import a bank or a PDF to start practising.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LibraryScreen() = ScreenScaffold("Library", "NEET, JEE Main and NCERT Connect (coming in later milestones).")

@Composable
fun CreateScreen() = ScreenScaffold("Create", "Custom practice and PDF Studio (coming in later milestones).")

@Composable
fun ProgressScreen() = ScreenScaffold("Progress", "Results, analytics and mistake notebook (coming in later milestones).")

@Composable
fun SettingsScreen() = ScreenScaffold("Settings", "Theme, exam templates and optional AI (coming in later milestones).")
