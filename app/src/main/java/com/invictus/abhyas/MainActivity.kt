package com.invictus.abhyas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.invictus.abhyas.data.prefs.ThemeMode
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.AbhyasRoot
import com.invictus.abhyas.ui.theme.AbhyasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AbhyasApp
        setContent {
            val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AbhyasTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                AbhyasRoot(database = app.database, settingsStore = app.settingsStore)
            }
        }
    }
}
