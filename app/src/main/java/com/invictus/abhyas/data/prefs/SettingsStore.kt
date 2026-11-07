package com.invictus.abhyas.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** AI is optional and OFF by default; the app never needs it. */
    val aiEnabled: Boolean = false,
    /** HTTPS URL of a hosted NCERT book index (optional; empty = not set). */
    val ncertIndexUrl: String = "",
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val ai = booleanPreferencesKey("ai_enabled")
        val ncertIndex = stringPreferencesKey("ncert_index_url")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { p ->
        UserSettings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: true,
            aiEnabled = p[Keys.ai] ?: false,
            ncertIndexUrl = p[Keys.ncertIndex] ?: "",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamic] = enabled }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ai] = enabled }
    }

    suspend fun setNcertIndexUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.ncertIndex] = url.trim() }
    }
}
