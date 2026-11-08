@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.domain.ai.AiDefaults
import com.invictus.abhyas.domain.ai.AiResult
import com.invictus.abhyas.domain.ai.ApiKeyFormat
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Key, model and connection test. Shown only while the AI switch is on. */
@Composable
fun AiSettingsSection(app: AbhyasApp, model: String) {
    val scope = rememberCoroutineScope()
    val hasKey by app.apiKeyStore.present.collectAsState()
    // Deliberately plain `remember`, not `rememberSaveable`: the key must never reach saved instance state.
    var keyInput by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var modelText by remember(model) { mutableStateOf(model) }
    val modelOk = AiDefaults.isValidModel(modelText)

    BodyMuted(
        "Uses your own Gemini API key (free keys: aistudio.google.com). Passages you send are processed by Google. " +
            "The key is stored encrypted on this device and is not included in backups.",
    )

    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it.filter { c -> !c.isWhitespace() } },
        label = { Text(if (hasKey) "Replace API key" else "Gemini API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = { Text(if (hasKey) "A key is saved. It is never shown again." else "No key saved yet.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                val k = keyInput
                keyInput = ""
                testResult = null
                scope.launch { withContext(Dispatchers.IO) { app.apiKeyStore.set(k) } }
            },
            enabled = ApiKeyFormat.isPlausible(keyInput),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) { Text("Save key") }
        if (hasKey) {
            OutlinedButton(
                onClick = { testResult = null; app.apiKeyStore.clear() },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) { Text("Remove key") }
        }
    }

    Text("Model", style = MaterialTheme.typography.titleSmall)
    val presets = AiDefaults.PRESETS
    ConnectedToggleGroup(
        items = presets.map { it.first },
        selected = presets.indexOfFirst { it.second == model },
    ) { i -> scope.launch { app.settingsStore.setAiModel(presets[i].second) } }
    OutlinedTextField(
        value = modelText,
        onValueChange = {
            modelText = it.trim()
            if (AiDefaults.isValidModel(modelText)) scope.launch { app.settingsStore.setAiModel(modelText) }
        },
        label = { Text("Model id") },
        singleLine = true,
        isError = !modelOk,
        supportingText = { if (!modelOk) Text("Letters, digits, dot, dash and underscore only") },
        modifier = Modifier.fillMaxWidth(),
    )

    FilledTonalButton(
        onClick = {
            testing = true
            testResult = null
            scope.launch {
                testResult = when (val r = app.aiGateway.test()) {
                    is AiResult.Text -> "Connected."
                    is AiResult.Failure -> r.message
                }
                testing = false
            }
        },
        enabled = hasKey && modelOk && !testing,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Test connection") }
    if (testing) LoadingIndicator()
    testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
}
