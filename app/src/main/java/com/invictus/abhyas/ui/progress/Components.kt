@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

internal val ProgGood = Color(0xFF3FB97A)
internal val ProgWarn = Color(0xFFE8A93A)
internal val ProgBad = Color(0xFFEF6B6B)
internal val ProgInfo = Color(0xFF5B9DF5)
internal val ProgViolet = Color(0xFFA78BFA)
internal val ProgTeal = Color(0xFF2DD4BF)
internal val ProgPink = Color(0xFFF472B6)

/** Distinct accents handed out row by row so every subject / topic reads as its own colour. */
internal val ProgPalette = listOf(ProgInfo, ProgViolet, ProgTeal, ProgPink, ProgWarn, ProgGood)

internal fun accuracyColor(v: Double): Color = when {
    v >= 0.75 -> ProgGood
    v >= 0.5 -> ProgWarn
    else -> ProgBad
}

@Composable
internal fun BreakdownRow(b: Breakdown) = BreakdownCard(b, accuracyColor(b.accuracy))

/** One subject / topic: name, accuracy chip, a bar in the row's own colour and one line of numbers. */
@Composable
internal fun BreakdownCard(b: Breakdown, accent: Color) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Text(b.label, style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f).padding(start = 8.dp))
                val ac = accuracyColor(b.accuracy)
                Surface(shape = RoundedCornerShape(50), color = ac.copy(alpha = 0.18f)) {
                    Text(pct(b.accuracy), style = MaterialTheme.typography.labelLarge, color = ac, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
            LinearProgressIndicator(
                progress = { b.accuracy.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = accent.copy(alpha = 0.18f),
            )
            Text(
                "${b.correct}/${b.attempted} correct - ${fmtMarks(b.score)} marks - avg ${fmtDuration(b.avgTimeMs)}" +
                    (if (b.unattempted > 0) " - ${b.unattempted} skipped" else "") +
                    (if (b.unscored > 0) " - ${b.unscored} unscored" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Big coloured number tile for the overview strip. */
@Composable
internal fun SummaryTile(modifier: Modifier, value: String, label: String, color: Color) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = color.copy(alpha = 0.18f)) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Section heading with a coloured marker. */
@Composable
internal fun ColorSectionTitle(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized)
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
