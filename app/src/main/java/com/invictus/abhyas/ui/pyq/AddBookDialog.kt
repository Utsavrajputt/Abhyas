package com.invictus.abhyas.ui.pyq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.SectionTitle

/** Asks for the details of a textbook PDF picked from the phone. */
@Composable
internal fun AddBookDialog(onDismiss: () -> Unit, onConfirm: (title: String, classLevel: Int?, subject: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf(0) }
    var subject by remember { mutableStateOf("Physics") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add textbook") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionTitle("Class")
                ConnectedToggleGroup(listOf("11", "12", "Any"), cls) { cls = it }
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim(), when (cls) { 0 -> 11; 1 -> 12; else -> null }, subject.trim().ifBlank { "General" }) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
