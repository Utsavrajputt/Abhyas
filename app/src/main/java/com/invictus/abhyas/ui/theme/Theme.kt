@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.abhyas.R

private val HeadingFont = FontFamily(
    Font(R.font.space_grotesk_semibold, weight = FontWeight.SemiBold),
)

/** Single source of truth for corner radii (same scale as xmd); also feeds [MaterialTheme.shapes]. */
object AbhyasShapes {
    val Xs = RoundedCornerShape(10.dp)
    val S = RoundedCornerShape(14.dp)
    val M = RoundedCornerShape(20.dp)
    val L = RoundedCornerShape(28.dp)
    val Xl = RoundedCornerShape(36.dp)
}

private val ExpressiveShapes = Shapes(
    extraSmall = AbhyasShapes.Xs,
    small = AbhyasShapes.S,
    medium = AbhyasShapes.M,
    large = AbhyasShapes.L,
    extraLarge = AbhyasShapes.Xl,
)

private val DefaultTypography = Typography()

private fun TextStyle.abhyas(fontFamily: FontFamily = this.fontFamily ?: FontFamily.Default): TextStyle =
    copy(fontFamily = fontFamily, letterSpacing = 0.sp)

private val ExpressiveTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.abhyas(HeadingFont),
    displayMedium = DefaultTypography.displayMedium.abhyas(HeadingFont),
    displaySmall = DefaultTypography.displaySmall.abhyas(HeadingFont),
    headlineLarge = DefaultTypography.headlineLarge.abhyas(HeadingFont),
    headlineMedium = DefaultTypography.headlineMedium.abhyas(HeadingFont),
    headlineSmall = DefaultTypography.headlineSmall.abhyas(HeadingFont),
    titleLarge = DefaultTypography.titleLarge.abhyas(HeadingFont),
    titleMedium = DefaultTypography.titleMedium.abhyas(HeadingFont),
    titleSmall = DefaultTypography.titleSmall.abhyas(HeadingFont),
    bodyLarge = DefaultTypography.bodyLarge.abhyas(),
    bodyMedium = DefaultTypography.bodyMedium.abhyas(),
    bodySmall = DefaultTypography.bodySmall.abhyas(),
    labelLarge = DefaultTypography.labelLarge.abhyas(),
    labelMedium = DefaultTypography.labelMedium.abhyas(),
    labelSmall = DefaultTypography.labelSmall.abhyas(),
)

/** Resolves the active [ColorScheme] for [theme], dark mode and AMOLED flags. */
fun resolveColorScheme(theme: AppTheme, isDark: Boolean, isAmoled: Boolean): ColorScheme = when {
    isDark && isAmoled -> theme.getAmoledColorScheme()
    isDark -> theme.getDarkColorScheme()
    else -> theme.getLightColorScheme()
}

/**
 * Material 3 Expressive theme: expressive motion scheme, the selected [AppTheme]
 * palette (light / dark / AMOLED), xmd typography and shapes, and the circular
 * reveal overlay used when the theme changes.
 */
@Composable
fun AbhyasTheme(
    theme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
    transitionState: ThemeTransitionState = rememberThemeTransitionState(),
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(theme, isDark, isAmoled) { resolveColorScheme(theme, isDark, isAmoled) }

    CompositionLocalProvider(LocalThemeTransitionState provides transitionState) {
        ThemeTransitionOverlay(state = transitionState) {
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
                typography = ExpressiveTypography,
                shapes = ExpressiveShapes,
                content = content,
            )
        }
    }
}
