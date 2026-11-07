@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.domain.analytics.Breakdown
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BreakdownRow(b: Breakdown) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(b.label, style = MaterialTheme.typography.titleSmallEmphasized)
        LinearProgressIndicator(progress = { b.accuracy.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(
            "${b.correct}/${b.attempted} correct - ${pct(b.accuracy)} - ${fmtMarks(b.score)} marks" +
                " - avg ${fmtDuration(b.avgTimeMs)}" +
                (if (b.unattempted > 0) " - ${b.unattempted} skipped" else "") +
                (if (b.unscored > 0) " - ${b.unscored} unscored" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun pct(v: Double): String = "${Math.round(v * 100)}%"

internal fun fmtMarks(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else "%.2f".format(v)

internal fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m %02ds".format(s % 60)
}

internal fun fmtDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
