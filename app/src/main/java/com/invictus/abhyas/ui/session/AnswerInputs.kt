@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.domain.engine.OptionShuffler
import com.invictus.abhyas.domain.engine.Scorer
import com.invictus.abhyas.domain.model.AssertionReasonOptions
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.ui.math.MathText

/** Renders the answer area for any question type. Selecting again clears a single choice. */
@Composable
fun AnswerInput(q: Question, response: Response?, onResponse: (Response?) -> Unit) {
    when (val p = q.payload) {
        is SingleChoicePayload -> ChoiceList(p.options, (response as? Response.Choice)?.ids.orEmpty(), false, onResponse)
        is MultiChoicePayload -> {
            Text("Select all that apply", style = MaterialTheme.typography.labelLarge)
            ChoiceList(p.options, (response as? Response.Choice)?.ids.orEmpty(), true, onResponse)
        }
        is AssertionReasonPayload -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledText("Assertion (A)", p.assertion)
                LabeledText("Reason (R)", p.reason)
            }
            ChoiceList(AssertionReasonOptions.ALL, (response as? Response.Choice)?.ids.orEmpty(), false, onResponse)
        }
        is MatchPayload -> {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MatchColumn(p.leftTitle, p.left, Modifier.weight(1f))
                MatchColumn(p.rightTitle, p.right, Modifier.weight(1f))
            }
            ChoiceList(p.options, (response as? Response.Choice)?.ids.orEmpty(), false, onResponse)
        }
        is TrueFalsePayload -> {
            val picked = (response as? Response.Bool)?.value
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(true to "True", false to "False").forEach { (value, label) ->
                    ToggleButton(
                        checked = picked == value,
                        onCheckedChange = { onResponse(if (picked == value) null else Response.Bool(value)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                }
            }
        }
        is NumericalPayload -> NumericalField(q.id, response, p.integerOnly, onResponse)
        is FillBlankPayload -> {
            var text by rememberSaveable(q.id) { mutableStateOf((response as? Response.Text)?.value.orEmpty()) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onResponse(if (it.isBlank()) null else Response.Text(it)) },
                label = { Text("Your answer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceList(options: List<Option>, selected: Set<String>, multi: Boolean, onResponse: (Response?) -> Unit) {
    val labels = OptionShuffler.labels(options)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val checked = opt.id in selected
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    val next = when {
                        multi -> if (checked) selected - opt.id else selected + opt.id
                        checked -> emptySet()
                        else -> setOf(opt.id)
                    }
                    onResponse(if (next.isEmpty()) null else Response.Choice(next))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(labels[opt.id].orEmpty(), style = MaterialTheme.typography.titleMediumEmphasized)
                    MathText(opt.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun NumericalField(qid: String, response: Response?, integerOnly: Boolean, onResponse: (Response?) -> Unit) {
    var text by rememberSaveable(qid) {
        mutableStateOf((response as? Response.Number)?.value?.let { formatNumber(it) }.orEmpty())
    }
    val invalid = text.isNotBlank() && Scorer.parseNumber(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onResponse(if (it.isBlank()) null else Scorer.parseNumber(it))
        },
        label = { Text(if (integerOnly) "Integer answer" else "Numerical answer") },
        isError = invalid,
        supportingText = { if (invalid) Text("Enter a number") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatNumber(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

@Composable
private fun LabeledText(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        MathText(text)
    }
}

@Composable
private fun MatchColumn(title: String, items: List<Option>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                Text(item.id.uppercase() + ".", style = MaterialTheme.typography.labelLarge)
                MathText(item.text, modifier = Modifier.weight(1f))
            }
        }
    }
}
