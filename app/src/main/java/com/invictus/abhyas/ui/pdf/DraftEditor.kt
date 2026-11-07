@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.abhyas.data.local.PdfDraftEntity
import com.invictus.abhyas.data.pdf.DraftEvaluator
import com.invictus.abhyas.data.pdf.PdfJobConfig
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.QuestionPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.domain.pack.PayloadCodec
import com.invictus.abhyas.domain.pdf.OptionParser
import com.invictus.abhyas.ui.common.QuestionImage
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.SectionTitle

/** Editable copy of a draft. Option correctness is index based so blank options can be dropped safely. */
private class EditForm(val base: PdfDraftEntity) {
    var type by mutableStateOf(QuestionType.valueOf(base.type))
    var statement by mutableStateOf(base.statement)
    var explanation by mutableStateOf(base.explanation.orEmpty())
    var chapter by mutableStateOf(base.chapter.orEmpty())
    var topic by mutableStateOf(base.topic.orEmpty())
    var subject by mutableStateOf(base.subject.orEmpty())
    var difficulty by mutableStateOf(base.difficulty)
    val options = mutableStateListOf<String>()
    val correct = mutableStateListOf<Int>()
    var number by mutableStateOf("")
    var tolerance by mutableStateOf("0")
    var integerOnly by mutableStateOf(false)
    var tf by mutableStateOf<Boolean?>(null)
    var assertion by mutableStateOf("")
    var reason by mutableStateOf("")
    var arCorrect by mutableStateOf<Int?>(null)
    var blanks by mutableStateOf("")
    private var matchPayload: MatchPayload? = null

    init {
        val p = runCatching { PayloadCodec.decodeString(type, base.payloadJson) }.getOrNull()
        when (p) {
            is SingleChoicePayload -> { p.options.forEach { options += it.text }; p.correctId?.let { id -> p.options.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { n -> correct.add(n) } } }
            is MultiChoicePayload -> { p.options.forEach { options += it.text }; p.correctIds.forEach { id -> p.options.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { n -> correct.add(n) } } }
            is NumericalPayload -> { number = p.answer?.let { if (it == Math.floor(it) && it < 1e12) it.toLong().toString() else it.toString() }.orEmpty(); tolerance = p.tolerance.toString(); integerOnly = p.integerOnly }
            is TrueFalsePayload -> tf = p.answer
            is AssertionReasonPayload -> { assertion = p.assertion; reason = p.reason; arCorrect = p.correctOption }
            is MatchPayload -> matchPayload = p
            is FillBlankPayload -> blanks = p.acceptedAnswers.joinToString(" / ")
            null -> Unit
        }
        if (options.isEmpty() && (type == QuestionType.SINGLE_MCQ || type == QuestionType.MULTI_MCQ || type == QuestionType.IMAGE)) repeat(4) { options += "" }
    }

    fun changeType(t: QuestionType) {
        type = t
        val needsOptions = t == QuestionType.SINGLE_MCQ || t == QuestionType.MULTI_MCQ || t == QuestionType.IMAGE
        if (needsOptions && options.size < 2) { options.clear(); repeat(4) { options += "" } }
        if (t == QuestionType.SINGLE_MCQ || t == QuestionType.IMAGE) while (correct.size > 1) correct.removeAt(correct.lastIndex)
    }

    fun payload(): QuestionPayload = when (type) {
        QuestionType.SINGLE_MCQ, QuestionType.IMAGE -> {
            val (opts, ids) = optionsWithIds()
            SingleChoicePayload(opts, ids.firstOrNull())
        }
        QuestionType.MULTI_MCQ -> {
            val (opts, ids) = optionsWithIds()
            MultiChoicePayload(opts, ids)
        }
        QuestionType.NUMERICAL -> NumericalPayload(number.trim().toDoubleOrNull(), tolerance.trim().toDoubleOrNull() ?: 0.0, integerOnly)
        QuestionType.TRUE_FALSE -> TrueFalsePayload(tf)
        QuestionType.ASSERTION_REASON -> AssertionReasonPayload(assertion.trim(), reason.trim(), arCorrect)
        QuestionType.MATCH -> matchPayload ?: MatchPayload(left = emptyList(), right = emptyList(), options = emptyList())
        QuestionType.FILL_BLANK -> FillBlankPayload(blanks.split('/', ';').map { it.trim() }.filter { it.isNotEmpty() })
    }

    private fun optionsWithIds(): Pair<List<Option>, List<String>> {
        val keep = options.indices.filter { options[it].isNotBlank() }
        val opts = keep.mapIndexed { n, i -> Option(OptionParser.idOf(n + 1), options[i].trim()) }
        val ids = correct.sorted().mapNotNull { c -> keep.indexOf(c).takeIf { it >= 0 }?.let { OptionParser.idOf(it + 1) } }
        return opts to ids
    }

    fun toEntity(): PdfDraftEntity = base.copy(
        type = type.name,
        statement = statement.trim(),
        payloadJson = PayloadCodec.encode(payload()).toString(),
        explanation = explanation.trim().ifBlank { null },
        chapter = chapter.trim().ifBlank { null },
        topic = topic.trim().ifBlank { null },
        subject = subject.trim().ifBlank { null },
        difficulty = difficulty,
    )
}

@Composable
fun DraftEditor(
    draft: PdfDraftEntity,
    config: PdfJobConfig,
    chapters: List<String>,
    onDismiss: () -> Unit,
    onSave: (PdfDraftEntity) -> Unit,
    onAttach: (Long, (String?, String?) -> Unit) -> Unit,
    onRemoveFigure: (Long) -> Unit,
) {
    val form = remember(draft.id) { EditForm(draft) }
    var typeMenu by remember { mutableStateOf(false) }
    var figureBusy by remember { mutableStateOf(false) }
    var figureError by remember { mutableStateOf<String?>(null) }
    // Figure changes are saved straight away by the repository, so show the live value.
    var imagePath by remember(draft.id, draft.imagePath) { mutableStateOf(draft.imagePath) }
    val errors = DraftEvaluator.evaluate(form.toEntity().copy(imagePath = imagePath), config, "").errors

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
                    Text("Question ${draft.number}  (page ${draft.page})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onSave(form.toEntity().copy(imagePath = imagePath)) }, shapes = ButtonDefaults.shapes()) { Text("Save") }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (errors.isNotEmpty()) errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    draft.issues.lineSequence().filter { it.isNotBlank() }.forEach { BodyMuted("Note: $it") }

                    Box {
                        OutlinedButton(onClick = { typeMenu = true }, shapes = ButtonDefaults.shapes()) { Text("Type: ${form.type.name.replace('_', ' ')}") }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            QuestionType.entries.filter { it != QuestionType.MATCH || form.type == QuestionType.MATCH }.forEach { t ->
                                DropdownMenuItem(text = { Text(t.name.replace('_', ' ')) }, onClick = { form.changeType(t); typeMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(form.statement, { form.statement = it }, label = { Text("Statement (LaTeX ok)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

                    when (form.type) {
                        QuestionType.SINGLE_MCQ, QuestionType.IMAGE, QuestionType.MULTI_MCQ -> {
                            val multi = form.type == QuestionType.MULTI_MCQ
                            SectionTitle(if (multi) "Options (tick all correct)" else "Options (select the correct one)")
                            form.options.forEachIndexed { i, text ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (multi) Checkbox(i in form.correct, { on -> if (on) form.correct.add(i) else form.correct.remove(i) })
                                    else RadioButton(i in form.correct, { form.correct.clear(); form.correct.add(i) })
                                    OutlinedTextField(text, { form.options[i] = it }, label = { Text("Option ${OptionParser.idOf(i + 1).uppercase()}") }, modifier = Modifier.weight(1f))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (form.options.size < 5) TextButton(onClick = { form.options += "" }, shapes = ButtonDefaults.shapes()) { Text("Add option") }
                                if (form.options.size > 2) TextButton(onClick = { form.correct.remove(form.options.lastIndex); form.options.removeAt(form.options.lastIndex) }, shapes = ButtonDefaults.shapes()) { Text("Remove last") }
                                if (form.correct.isNotEmpty()) TextButton(onClick = { form.correct.clear() }, shapes = ButtonDefaults.shapes()) { Text("Clear answer") }
                            }
                        }
                        QuestionType.NUMERICAL -> {
                            OutlinedTextField(form.number, { form.number = it }, label = { Text("Answer (empty = no answer)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(form.tolerance, { form.tolerance = it }, label = { Text("Tolerance (absolute)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(form.integerOnly, { form.integerOnly = it }); Text("Integer answer only") }
                        }
                        QuestionType.TRUE_FALSE -> ConnectedToggleGroup(listOf("True", "False", "No answer"), when (form.tf) { true -> 0; false -> 1; null -> 2 }) { form.tf = when (it) { 0 -> true; 1 -> false; else -> null } }
                        QuestionType.ASSERTION_REASON -> {
                            OutlinedTextField(form.assertion, { form.assertion = it }, label = { Text("Assertion (A)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            OutlinedTextField(form.reason, { form.reason = it }, label = { Text("Reason (R)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            SectionTitle("Correct option (standard order)")
                            ConnectedToggleGroup(listOf("1", "2", "3", "4", "None"), form.arCorrect?.minus(1) ?: 4) { form.arCorrect = if (it == 4) null else it + 1 }
                            BodyMuted("1 both true, R explains A. 2 both true, R does not explain A. 3 A true, R false. 4 A false, R true.")
                        }
                        QuestionType.FILL_BLANK -> OutlinedTextField(form.blanks, { form.blanks = it }, label = { Text("Accepted answers, separated by /") }, modifier = Modifier.fillMaxWidth())
                        QuestionType.MATCH -> BodyMuted("Match-the-following lists are kept as imported; only the statement can be edited here.")
                    }

                    OutlinedTextField(form.explanation, { form.explanation = it }, label = { Text("Explanation (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                    SectionTitle("Classification")
                    OutlinedTextField(form.subject, { form.subject = it }, label = { Text("Subject (default: ${config.subject})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.chapter, { form.chapter = it }, label = { Text("Chapter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    draft.suggestedChapter?.takeIf { it.isNotBlank() && it != form.chapter }?.let { s ->
                        FilledTonalButton(onClick = { form.chapter = s }, shapes = ButtonDefaults.shapes()) { Text("Use suggested: $s") }
                    }
                    chapters.filter { form.chapter.isNotBlank() && it != form.chapter && it.contains(form.chapter, true) }.take(3).forEach { c ->
                        TextButton(onClick = { form.chapter = c }, shapes = ButtonDefaults.shapes()) { Text(c) }
                    }
                    OutlinedTextField(form.topic, { form.topic = it }, label = { Text("Topic") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val diffs = listOf(null, "EASY", "MEDIUM", "HARD")
                    ConnectedToggleGroup(listOf("None", "Easy", "Medium", "Hard"), diffs.indexOf(form.difficulty).coerceAtLeast(0)) { form.difficulty = diffs[it] }

                    SectionTitle("Figure")
                    QuestionImage(imagePath)
                    figureError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                figureBusy = true; figureError = null
                                onAttach(draft.id) { path, err ->
                                    figureBusy = false
                                    figureError = err
                                    if (path != null) imagePath = path
                                }
                            },
                            enabled = !figureBusy,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(if (figureBusy) "Working..." else if (imagePath == null) "Attach figure from page" else "Re-crop figure") }
                        if (imagePath != null) TextButton(onClick = { onRemoveFigure(draft.id); imagePath = null }, shapes = ButtonDefaults.shapes()) { Text("Remove") }
                    }
                    if (draft.hasFigureHint && imagePath == null) BodyMuted("This question may refer to a figure.")
                }
            }
        }
    }
}
