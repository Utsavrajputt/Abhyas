@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.invictus.abhyas.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.LocalThemeTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Title style shared by every screen header (same emphasized headline as xmd). */
@Composable
fun abhyasTopBarTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineSmallEmphasized.copy(fontWeight = FontWeight.ExtraBold)

/** Flat bar colours shared by every screen header. */
@Composable
fun abhyasTopBarColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = containerColor,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/** The one header used by every screen: a flat Material 3 top app bar with the shared emphasized title. */
@Composable
fun AbhyasTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = abhyasTopBarColors(),
) {
    TopAppBar(
        title = { ProvideTextStyle(abhyasTopBarTitleStyle()) { title() } },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
    )
}

/** Back arrow used as the navigation icon of sub-screens. */
@Composable
fun BackNavigationIcon(onBack: () -> Unit) {
    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
        Icon(
            imageVector = AppIcons.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * What the shell exposes to every screen header: where the Settings gear goes and
 * how the dark/light toggle behaves. null = header shows no shell actions (sub-screens).
 */
class ShellActions(
    val isDark: Boolean,
    val onOpenSettings: () -> Unit,
    val onToggleDark: () -> Unit,
)

val LocalShellActions = compositionLocalOf<ShellActions?> { null }

/**
 * Header of the main screens: title (tap it to flip dark/light, like xmd) with the
 * dark/light toggle and the Settings gear as actions. The toggle runs the same
 * circular reveal as the theme picker, starting at the tapped spot.
 */
@Composable
fun MainTopBar(title: String, modifier: Modifier = Modifier) {
    val shell = LocalShellActions.current
    val themeTransition = LocalThemeTransitionState.current
    val scope = rememberCoroutineScope()
    val titleBounds = remember { mutableStateOf(Rect.Zero) }
    val toggleBounds = remember { mutableStateOf(Rect.Zero) }

    fun toggleFrom(origin: Offset) {
        val shellActions = shell ?: return
        if (themeTransition?.isAnimating == true) return
        themeTransition?.startTransition(origin)
        scope.launch {
            delay(50)
            shellActions.onToggleDark()
        }
    }

    AbhyasTopBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                modifier = Modifier
                    .onGloballyPositioned { titleBounds.value = it.boundsInWindow() }
                    .pointerInput(shell) {
                        detectTapGestures(
                            onTap = { local ->
                                toggleFrom(Offset(titleBounds.value.left + local.x, titleBounds.value.top + local.y))
                            },
                        )
                    },
            )
        },
        actions = {
            if (shell != null) {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { toggleFrom(toggleBounds.value.center) },
                    modifier = Modifier.onGloballyPositioned { toggleBounds.value = it.boundsInWindow() },
                ) {
                    Icon(
                        imageVector = if (shell.isDark) AppIcons.LightMode else AppIcons.DarkMode,
                        contentDescription = if (shell.isDark) "Switch to light mode" else "Switch to dark mode",
                    )
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = shell.onOpenSettings) {
                    Icon(imageVector = AppIcons.Settings, contentDescription = "Settings")
                }
            }
        },
    )
}

/** Back action for non-top-level screens; null on the main tabs. */
val LocalBackAction = compositionLocalOf<(() -> Unit)?> { null }
