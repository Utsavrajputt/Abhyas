@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.invictus.abhyas.ui.ncert

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.SheetValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiPrompts
import com.invictus.abhyas.domain.ai.AiResult
import com.invictus.abhyas.ui.components.InfoPill
import kotlinx.coroutines.launch

private data class ChatMsg(val fromUser: Boolean, val text: String, val error: Boolean = false)

/**
 * The reader's AI: opens straight from the top bar, quick actions on top (notes, explain, ...), the
 * conversation in the middle and a text box at the bottom. Everything is answered from this page's text.
 */
@Composable
fun ReaderAiSheet(app: AbhyasApp, pageLabel: String, pageText: suspend () -> String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var context by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { context = pageText() }
    LaunchedEffect(messages.size, loading) {
        val last = messages.size + (if (loading) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    fun send(label: String, build: (String) -> AiPrompt) {
        if (loading) return
        messages += ChatMsg(true, label)
        val text = context.orEmpty()
        if (text.isBlank()) {
            messages += ChatMsg(false, "There is no readable text on this page (scanned pages have no text to send).", error = true)
            return
        }
        loading = true
        scope.launch {
            when (val r = app.aiGateway.run(build(text))) {
                is AiResult.Text -> messages += ChatMsg(false, r.text)
                is AiResult.Failure -> messages += ChatMsg(false, r.message, error = true)
            }
            loading = false
        }
    }

    fun ask() {
        val q = input.trim()
        if (q.isEmpty()) return
        input = ""
        send(q) { AiPrompts.ask(q, it) }
    }

    // Opens at half height; drag the handle up for full height. The keyboard also expands it so the box stays visible.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val full = sheetState.targetValue == SheetValue.Expanded
    val contentH by animateDpAsState(if (full) screenH * 0.9f - 56.dp else screenH * 0.5f - 56.dp, label = "aiSheetHeight")
    val imeOpen = WindowInsets.isImeVisible
    LaunchedEffect(imeOpen) { if (imeOpen) sheetState.expand() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().height(contentH).navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Ask AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                InfoPill("Page $pageLabel")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { send("Notes for this page") { AiPrompts.notes(it) } }, label = { Text("Notes") })
                AssistChip(onClick = { send("Explain this page") { AiPrompts.explain(it) } }, label = { Text("Explain") })
                AssistChip(onClick = { send("Simplify this page") { AiPrompts.simplify(it) } }, label = { Text("Simplify") })
                AssistChip(onClick = { send("Summarise this page") { AiPrompts.ask("Summarise this page in 5 short bullet points.", it) } }, label = { Text("Summary") })
                AssistChip(onClick = { send("Quiz me on this page") { AiPrompts.ask("Ask me 3 short questions on this page, then give the answers at the end.", it) } }, label = { Text("Quiz me") })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !loading) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ask anything about this page", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Answers come from the page text only and can be wrong, so check the book.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LazyColumn(Modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages.size) { i -> Bubble(messages[i]) }
                    if (loading) item { LoadingBubble() }
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask about this page") },
                    shape = CircleShape,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(onClick = ::ask, enabled = input.isNotBlank() && !loading, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = when {
                m.fromUser -> cs.primaryContainer
                m.error -> cs.errorContainer
                else -> cs.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        m.fromUser -> cs.onPrimaryContainer
                        m.error -> cs.onErrorContainer
                        else -> cs.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingBubble() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) { LoadingIndicator(Modifier.size(32.dp)) }
    }
}
