@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.invictus.abhyas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.ui.components.AbhyasTopBar
import com.invictus.abhyas.ui.components.BackNavigationIcon
import com.invictus.abhyas.ui.components.LocalBackAction
import com.invictus.abhyas.ui.components.MainTopBar
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.query.QuestionFilter
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Shared expressive building blocks
// ---------------------------------------------------------------------------------------------

/**
 * Screen frame: xmd-style top bar (title; dark/light toggle + Settings gear on the main
 * tabs, a back arrow on sub-screens), the subtitle, then scrollable content (+ optional FAB).
 */
@Composable
internal fun ExpressiveScreen(
    title: String,
    subtitle: String,
    floatingActionButton: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val back = LocalBackAction.current
    Scaffold(
        topBar = {
            if (back != null) {
                AbhyasTopBar(
                    title = { Text(title) },
                    navigationIcon = { BackNavigationIcon(onBack = back) },
                    actions = topBarActions,
                )
            } else {
                MainTopBar(title = title, extraActions = topBarActions)
            }
        },
        // The root scaffold already pads for the bottom navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
internal fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized)

@Composable
internal fun BodyMuted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Connected expressive toggle-button group (single selection). */
@Composable
internal fun ConnectedToggleGroup(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        items.forEachIndexed { i, label ->
            val shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selected == i,
                onCheckedChange = { onSelect(i) },
                shapes = shapes,
                modifier = Modifier.weight(1f),
            ) { Text(label) }
        }
    }
}

/** A stat tile drawn inside a MaterialShapes container. */
@Composable
internal fun ShapeStat(
    value: String,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier.size(104.dp).clip(shape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMediumEmphasized, color = onContainer)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
