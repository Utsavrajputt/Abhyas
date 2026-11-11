@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.domain.ai.AiFeature
import com.invictus.abhyas.domain.ai.AiPrompts
import com.invictus.abhyas.domain.ai.AiResult
import com.invictus.abhyas.ui.screens.BodyMuted
import kotlinx.coroutines.launch

/** What the reader asks the AI sheet to do. */
data class AiRequestUi(
    val feature: AiFeature,
    val context: String,
    val title: String,
    /** A ready-made prompt (used by "Explain with AI" on a reviewed question); overrides the feature's own. */
    val prompt: com.invictus.abhyas.domain.ai.AiPrompt? = null,
)

private sealed interface AiUi {
    data object Idle : AiUi
    data object Loading : AiUi
    data class Done(val text: String) : AiUi
    data class Error(val message: String) : AiUi
}

/**
 * Bottom sheet for Explain / Simplify / Notes / Ask on a piece of textbook text. Everything is sent through
 * [AbhyasApp.aiGateway]; the sheet only ever shows what came back, labelled as unverified.
 */
@Composable
fun AiSheet(app: AbhyasApp, request: AiRequestUi, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember(request) { mutableStateOf<AiUi>(AiUi.Idle) }
    var question by remember(request) { mutableStateOf("") }

    fun start() {
        if (request.prompt == null && request.context.isBlank()) {
            state = AiUi.Error("There is no readable text here (scanned pages have no text to send).")
            return
        }
        val prompt = request.prompt ?: when (request.feature) {
            AiFeature.EXPLAIN -> AiPrompts.explain(request.context)
            AiFeature.SIMPLIFY -> AiPrompts.simplify(request.context)
            AiFeature.NOTES -> AiPrompts.notes(request.context)
            AiFeature.ASK -> AiPrompts.ask(question, request.context)
            AiFeature.GENERATE -> return
        }
        state = AiUi.Loading
        scope.launch {
            state = when (val r = app.aiGateway.run(prompt)) {
                is AiResult.Text -> AiUi.Done(r.text)
                is AiResult.Failure -> AiUi.Error(r.message)
            }
        }
    }

    LaunchedEffect(request) { if (request.feature != AiFeature.ASK) start() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${request.feature.label} - ${request.title}", style = MaterialTheme.typography.titleMediumEmphasized)
            BodyMuted("Written by AI from the passage only. It can be wrong, so check the book.")

            if (request.feature == AiFeature.ASK) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Your question") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Button(
                    onClick = { start() },
                    enabled = question.isNotBlank() && state != AiUi.Loading,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ask") }
            }

            when (val s = state) {
                AiUi.Idle -> Unit
                AiUi.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
                is AiUi.Done -> SelectionContainer { Text(s.text, style = MaterialTheme.typography.bodyLarge) }
                is AiUi.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (request.feature != AiFeature.ASK) {
                        TextButton(onClick = { start() }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                    }
                }
            }
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Close") }
        }
    }
}
