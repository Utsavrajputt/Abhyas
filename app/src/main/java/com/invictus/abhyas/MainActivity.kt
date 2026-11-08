package com.invictus.abhyas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.AbhyasRoot
import com.invictus.abhyas.ui.theme.AbhyasTheme
import com.invictus.abhyas.ui.theme.resolveColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AbhyasApp
        setContent {
            val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
            val dark = settings.darkMode ?: isSystemInDarkTheme()

            // Keep the window background and system-bar icons in step with the chosen theme.
            val background = resolveColorScheme(settings.appTheme, dark, settings.amoled).background.toArgb()
            LaunchedEffect(dark, background) {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
            }

            AbhyasTheme(theme = settings.appTheme, isDark = dark, isAmoled = settings.amoled) {
                AbhyasRoot(app)
            }
        }
    }
}
