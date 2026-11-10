package com.invictus.abhyas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.ui.icons.AppIcons

/** Rounded search field: icon, hint, clear button. Replaces the outlined text field that clipped its label. */
@Composable
fun SearchPill(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Search, contentDescription = null, tint = cs.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(AppIcons.Close, contentDescription = "Clear") }
            }
        }
    }
}

/** Small rounded fact label ("Class 11", "184 pages"). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (highlight) cs.primaryContainer else cs.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) cs.onPrimaryContainer else cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Pill-shaped tab switcher (single choice). */
@Composable
fun PillTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp)) {
            items.forEachIndexed { i, label ->
                val on = i == selected
                val bg by animateColorAsState(if (on) cs.primaryContainer else Color.Transparent, label = "tab")
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(bg).clickable { onSelect(i) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
