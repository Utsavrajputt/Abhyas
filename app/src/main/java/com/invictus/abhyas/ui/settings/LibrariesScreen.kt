package com.invictus.abhyas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.ui.theme.AbhyasShapes

private val LIBRARIES = listOf(
    "Jetpack Compose & Material 3 Expressive" to "User interface",
    "Material Symbols (Compose icons)" to "Navigation, top bar and settings icons",
    "Navigation Compose" to "Screen navigation",
    "Room" to "Local question, attempt and book storage",
    "Paging 3" to "Fast browsing of large question banks",
    "DataStore" to "Settings storage",
    "WorkManager" to "Background PDF extraction and book downloads",
    "kotlinx.serialization" to "Question-bank package parsing",
    "Kotlin coroutines" to "Asynchronous work",
    "PdfBox-Android" to "Text extraction from digital PDFs",
    "ML Kit Text Recognition" to "Offline OCR for scanned PDFs",
    "KaTeX" to "Offline maths rendering",
)

/** The open-source projects Abhyas is built on (same layout as xmd's Libraries screen). */
@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Libraries", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Abhyas is built on the shoulders of these open-source projects",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            LIBRARIES.forEach { (name, desc) ->
                Surface(
                    shape = AbhyasShapes.M,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
