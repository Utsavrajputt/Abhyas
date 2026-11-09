@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.invictus.abhyas.ui.icons.AppIcons
import kotlin.math.abs

/** One tab of the floating pill. [activeWidth] is its width while selected (icon + label). */
class PillTab(val label: String, val icon: ImageVector, val activeWidth: Dp)

/**
 * xmd-style bottom navigation: a floating pill of tabs (the selected one expands to
 * show its label) with a separate round "+" button next to it. [selectedIndex] = null
 * means the current screen is not one of the tabs (e.g. a Create flow).
 */
@Composable
fun FloatingNavigation(
    tabs: List<PillTab>,
    selectedIndex: Int?,
    onTabSelected: (Int) -> Unit,
    onCustomPractice: () -> Unit,
    onNcertPractice: () -> Unit,
    onImportPdf: () -> Unit,
    onImportBank: () -> Unit,
    onNcertConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillNavigationBar(tabs = tabs, selectedIndex = selectedIndex, onTabSelected = onTabSelected)
            CreateMenuButton(
                onCustomPractice = onCustomPractice,
                onNcertPractice = onNcertPractice,
                onImportPdf = onImportPdf,
                onImportBank = onImportBank,
                onNcertConnect = onNcertConnect,
            )
        }
    }
}

@Composable
private fun PillNavigationBar(
    tabs: List<PillTab>,
    selectedIndex: Int?,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val position by animateFloatAsState(
        targetValue = (selectedIndex ?: 0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "pillPosition",
    )
    val selectionAlpha by animateFloatAsState(
        targetValue = if (selectedIndex == null) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "pillSelection",
    )

    val inactiveTabWidth = 44.dp
    val spacing = 4.dp
    val startPadding = 6.dp

    val tabFractions = tabs.indices.map { index ->
        (1f - abs(position - index)).coerceIn(0f, 1f) * selectionAlpha
    }
    val tabWidths = tabs.mapIndexed { index, tab -> lerp(inactiveTabWidth, tab.activeWidth, tabFractions[index]) }
    val tabOffsets = run {
        var acc = startPadding
        tabWidths.map { w ->
            val left = acc
            acc += w + spacing
            left
        }
    }

    val pageFloor = position.toInt().coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val pageCeil = (pageFloor + 1).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val fraction = (position - pageFloor).coerceIn(0f, 1f)
    val indicatorLeft = lerp(tabOffsets[pageFloor], tabOffsets[pageCeil], fraction)
    val indicatorWidth = lerp(tabWidths[pageFloor], tabWidths[pageCeil], fraction)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = startPadding, vertical = 6.dp),
        ) {
            // Sliding background pill indicator (hidden while no tab is selected).
            Box(
                modifier = Modifier
                    .offset(x = indicatorLeft - startPadding)
                    .width(indicatorWidth)
                    .height(44.dp)
                    .graphicsLayer { alpha = selectionAlpha }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val tabFraction = tabFractions[index]
                    val contentColor = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        tabFraction,
                    )
                    Box(
                        modifier = Modifier
                            .width(tabWidths[index])
                            .height(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp),
                            )
                            if (tabFraction > 0.05f) {
                                Spacer(modifier = Modifier.width(lerp(0.dp, 6.dp, tabFraction)))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = ((tabFraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Round + button; tapping it opens the create options right here instead of a separate screen. */
@Composable
private fun CreateMenuButton(
    onCustomPractice: () -> Unit,
    onNcertPractice: () -> Unit,
    onImportPdf: () -> Unit,
    onImportBank: () -> Unit,
    onNcertConnect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = true
            },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .size(56.dp)
                .shadow(8.dp, CircleShape),
        ) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = "Create",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CreateMenuItem("Custom practice", AppIcons.Tune) { expanded = false; onCustomPractice() }
            CreateMenuItem("Practice from NCERT", AppIcons.MenuBook) { expanded = false; onNcertPractice() }
            CreateMenuItem("NCERT Connect", AppIcons.LibraryBooks) { expanded = false; onNcertConnect() }
            CreateMenuItem("Import PDF", AppIcons.Pdf) { expanded = false; onImportPdf() }
            CreateMenuItem("Import question bank", AppIcons.FileOpen) { expanded = false; onImportBank() }
        }
    }
}

@Composable
private fun CreateMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick,
    )
}
