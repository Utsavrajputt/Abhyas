@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.theme.AbhyasShapes
import com.invictus.abhyas.ui.theme.AppTheme
import com.invictus.abhyas.ui.theme.LocalThemeTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Theme colour (xmd's picker), dark mode and AMOLED. */
@Composable
fun AppearanceSettingsScreen(app: AbhyasApp, onBack: () -> Unit) {
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val isDark = settings.darkMode ?: isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val themeTransition = LocalThemeTransitionState.current

    SettingsScaffold(title = "Appearance", onBack = onBack) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            ThemePicker(
                currentTheme = settings.appTheme,
                isDarkMode = isDark,
                isAmoled = settings.amoled,
                onThemeSelected = { theme, origin ->
                    if (theme != settings.appTheme && themeTransition?.isAnimating != true) {
                        themeTransition?.startTransition(origin)
                        scope.launch {
                            delay(50)
                            store.setAppTheme(theme)
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsSectionCard {
                    SwitchSettingRow(
                        title = "Dark mode",
                        subtitle = "Use the dark colour scheme",
                        checked = isDark,
                        onCheckedChange = { scope.launch { store.setDarkMode(it) } },
                    )
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "AMOLED black",
                        subtitle = "Pure black backgrounds in dark mode",
                        checked = settings.amoled,
                        enabled = isDark,
                        onCheckedChange = { scope.launch { store.setAmoled(it) } },
                    )
                }
            }
        }
    }
}

@Composable
fun ThemePicker(
    currentTheme: AppTheme,
    isDarkMode: Boolean,
    isAmoled: Boolean,
    onThemeSelected: (AppTheme, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = AppTheme.entries.indexOf(currentTheme)
        if (index >= 0) {
            listState.animateScrollToItem(maxOf(0, index - 1))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tap a theme to apply it immediately.",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AppTheme.entries, key = { it.name }) { theme ->
                ThemePreviewCard(
                    theme = theme,
                    isSelected = theme == currentTheme,
                    isDarkMode = isDarkMode,
                    isAmoled = isAmoled,
                    onClick = { position -> onThemeSelected(theme, position) },
                )
            }
        }
    }
}

@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    isDarkMode: Boolean,
    isAmoled: Boolean,
    onClick: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    val colorScheme = if (isDarkMode) {
        if (isAmoled) theme.getAmoledColorScheme() else theme.getDarkColorScheme()
    } else {
        theme.getLightColorScheme()
    }
    val selectionColor = MaterialTheme.colorScheme.primary
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val borderColor = if (isSelected) selectionColor else if (isAmoled && isDarkMode) Color(0xFF242424) else Color.Transparent
    val elevation = if (isSelected) 8.dp else 2.dp

    Column(
        modifier = modifier
            .width(100.dp)
            .onGloballyPositioned { cardOrigin = it.boundsInWindow().topLeft }
            .pointerInput(Unit) {
                detectTapGestures { localPosition ->
                    onClick(cardOrigin + localPosition)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 140.dp)
                .shadow(
                    elevation = elevation,
                    shape = AbhyasShapes.S,
                    ambientColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                    spotColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                )
                .clip(AbhyasShapes.S)
                .background(colorScheme.surface)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = AbhyasShapes.S,
                ),
        ) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(if (isSelected) 3.dp else 1.dp)
                    .clip(RoundedCornerShape(if (isSelected) 9.dp else 11.dp))
                    .background(colorScheme.background)
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.primary),
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colorScheme.tertiary),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.surfaceVariant),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorScheme.secondary),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = theme.title,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

