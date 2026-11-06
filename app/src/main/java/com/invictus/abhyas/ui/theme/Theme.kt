package com.invictus.abhyas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF1F3A8A)
private val IndigoLight = Color(0xFFB9C6FF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0B1B52),
    secondary = Color(0xFF00796B),
    background = Color(0xFFFAFAFC),
    surface = Color(0xFFFAFAFC),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF0B1B52),
    primaryContainer = Color(0xFF2A3F8F),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF4DB6AC),
    background = Color(0xFF121317),
    surface = Color(0xFF121317),
)

@Composable
fun AbhyasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
