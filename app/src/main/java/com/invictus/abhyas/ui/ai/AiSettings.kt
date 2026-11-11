@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.invictus.abhyas.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import com.invictus.abhyas.domain.ai.ModelsResult
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
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }

    fun loadModels() {
        if (!hasKey) return
        loadingModels = true
        modelsError = null
        scope.launch {
            when (val r = app.aiGateway.models()) {
                is ModelsResult.Models -> { models = r.ids; if (r.ids.isEmpty()) modelsError = "No models found for this key." }
                is ModelsResult.Failure -> modelsError = r.message
            }
            loadingModels = false
        }
    }
    LaunchedEffect(hasKey) { if (hasKey) loadModels() else models = emptyList() }

    // The saved id stays selectable even when it is missing from the list (it may be why requests fail).
    val options = remember(models, model) { if (model.isNotBlank() && model !in models) listOf(model) + models else models }
    if (!manual && options.isNotEmpty()) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = model,
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                supportingText = {
                    val known = model in models
                    Text(if (models.isNotEmpty() && !known) "Not in your key's model list: requests may fail with \"model not found\"." else "${models.size} models available for your key")
                },
                isError = models.isNotEmpty() && model !in models,
                trailingIcon = { Icon(if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent layer on top: a read-only text field swallows taps, so the click is taken here.
            Box(Modifier.matchParentSize().clickable { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(id) },
                        onClick = { expanded = false; testResult = null; scope.launch { app.settingsStore.setAiModel(id) } },
                    )
                }
            }
        }
    } else {
        OutlinedTextField(
            value = modelText,
            onValueChange = {
                modelText = it.trim()
                if (AiDefaults.isValidModel(modelText)) scope.launch { app.settingsStore.setAiModel(modelText) }
            },
            label = { Text("Model id") },
            singleLine = true,
            isError = !modelOk,
            supportingText = { Text(if (!modelOk) "Letters, digits, dot, dash and underscore only" else modelsError ?: if (hasKey) "Loading models..." else "Save a key to load the model list.") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { loadModels() }, enabled = hasKey && !loadingModels, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
            Text(if (loadingModels) "Loading..." else "Refresh models")
        }
        if (options.isNotEmpty()) {
            TextButton(onClick = { manual = !manual }) { Text(if (manual) "Use list" else "Type id") }
        }
    }
    modelsError?.takeIf { options.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

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
