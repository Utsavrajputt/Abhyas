@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.AbhyasShapes

/** Accent colours shared by the paper cards and the filter chips. */
internal object PaperColors {
    val Physics = Color(0xFF4C8DFF)
    val Chemistry = Color(0xFFFFA726)
    val Biology = Color(0xFF4CAF50)
    val Maths = Color(0xFFAB47BC)
    val Neutral = Color(0xFF90A4AE)
    val Pyq = Color(0xFF7E57C2)
    val Theory = Color(0xFF26A69A)
    val Done = Color(0xFF4CAF50)

    fun subject(group: String): Color = when (group) {
        "Physics" -> Physics
        "Chemistry" -> Chemistry
        "Biology" -> Biology
        "Maths" -> Maths
        else -> Neutral
    }
}

/**
 * One paper: subject dot + type, name and facts on the left; PDF export and the main button on the right.
 * The button is Resume for an unfinished paper, Start for a new one and Retake for a submitted one.
 */
@Composable
internal fun PaperCard(row: PaperRow, onStart: () -> Unit, onResume: (Long) -> Unit, onSavePdf: () -> Unit) {
    val container = if (row.inProgressAttemptId != null) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(shape = AbhyasShapes.L, color = container) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = PaperColors.subject(row.subjectGroup), modifier = Modifier.size(8.dp)) {}
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = listOfNotNull(
                            row.subjectGroup,
                            row.type.takeIf { it != PaperRows.TYPE_OTHER },
                            when {
                                row.inProgressAttemptId != null -> "In progress"
                                row.completed -> "Completed"
                                else -> null
                            },
                        ).joinToString(" - "),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            row.inProgressAttemptId != null -> MaterialTheme.colorScheme.onSecondaryContainer
                            row.completed -> PaperColors.Done
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (row.completed) {
                        Spacer(Modifier.width(4.dp))
                        Icon(AppIcons.Check, contentDescription = null, tint = PaperColors.Done, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull("${row.bank.questionCount} questions", row.year?.toString(), row.coaching).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSavePdf) { Icon(AppIcons.Pdf, contentDescription = "Save as PDF") }
            val resumeId = row.inProgressAttemptId
            if (resumeId != null) {
                Button(
                    onClick = { onResume(resumeId) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text("Resume") }
            } else {
                FilledTonalButton(
                    onClick = onStart,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text(if (row.completed) "Retake" else "Start") }
            }
        }
    }
}

/** Outlined pill with a coloured dot, like the workflow-run filters: selected = tinted fill and a stronger border. */
@Composable
internal fun DotChip(label: String, dot: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = shape,
        color = if (selected) cs.primary.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = dot, modifier = Modifier.size(9.dp)) {}
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
        }
    }
}

/** Result of "Save as PDF" (or a start error) shown over the list. */
@Composable
internal fun ImportDoneDialog(state: ImportUi, onDismiss: () -> Unit) {
    val done = state as? ImportUi.Done ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(done.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { done.lines.forEach { Text(it) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
