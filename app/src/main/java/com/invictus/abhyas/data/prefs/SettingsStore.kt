package com.invictus.abhyas.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.invictus.abhyas.data.pyq.IndexLink
import com.invictus.abhyas.data.pyq.PaperLink
import com.invictus.abhyas.data.pyq.PaperLinkCodec
import com.invictus.abhyas.data.pyq.PaperMeta
import com.invictus.abhyas.data.pyq.SourceCodec
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class UserSettings(
    /** The exam the whole app is focused on (Home, Library, PYQ store, practice defaults). */
    val exam: Exam = Exam.NEET,
    /** HTTPS URL of the hosted PYQ index (index.json). Empty = not set. */
    val pyqIndexUrl: String = "",
    /** Selected colour theme (Aurora by default). */
    val appTheme: AppTheme = AppTheme.Default,
    /** null = follow the system until the user picks light/dark explicitly. */
    val darkMode: Boolean? = null,
    /** Pure-black surfaces, only applied while dark mode is on. */
    val amoled: Boolean = false,
    /** AI is optional and OFF by default; the app never needs it. */
    val aiEnabled: Boolean = false,
    /** Gemini model id used for AI features. */
    val aiModel: String = "gemini-2.5-flash",
    /** HTTPS URL of a hosted NCERT book index (optional; empty = not set). */
    val ncertIndexUrl: String = "",
)

class SettingsStore(private val context: Context) {
    private object Keys {
        /** Legacy key (SYSTEM / LIGHT / DARK), read once as a fallback for [dark]. */
        val legacyThemeMode = stringPreferencesKey("theme_mode")
        val appTheme = stringPreferencesKey("app_theme")
        val dark = booleanPreferencesKey("dark_mode")
        val amoled = booleanPreferencesKey("amoled_mode")
        val ai = booleanPreferencesKey("ai_enabled")
        val aiModel = stringPreferencesKey("ai_model")
        val ncertIndex = stringPreferencesKey("ncert_index_url")
        val exam = stringPreferencesKey("exam")
        val pyqIndex = stringPreferencesKey("pyq_index_url")
        val paperLinks = stringPreferencesKey("paper_links")
        val indexLinks = stringPreferencesKey("index_links")
        val paperMeta = stringPreferencesKey("paper_meta")
        val completedPapers = stringSetPreferencesKey("completed_papers")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { p ->
        UserSettings(
            appTheme = AppTheme.fromKey(p[Keys.appTheme]),
            darkMode = p[Keys.dark] ?: when (p[Keys.legacyThemeMode]) {
                "DARK" -> true
                "LIGHT" -> false
                else -> null
            },
            amoled = p[Keys.amoled] ?: false,
            aiEnabled = p[Keys.ai] ?: false,
            aiModel = p[Keys.aiModel]?.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash",
            ncertIndexUrl = p[Keys.ncertIndex] ?: "",
            exam = if (p[Keys.exam] == Exam.JEE_MAIN.name) Exam.JEE_MAIN else Exam.NEET,
            pyqIndexUrl = p[Keys.pyqIndex] ?: "",
        )
    }

    /** Links the user added to the Downloads screen (papers / question-bank zips). */
    val paperLinks: Flow<List<PaperLink>> = context.settingsDataStore.data.map { PaperLinkCodec.decode(it[Keys.paperLinks]) }

    suspend fun setPaperLinks(list: List<PaperLink>) {
        context.settingsDataStore.edit { it[Keys.paperLinks] = PaperLinkCodec.encode(list) }
    }

    /** "Other links": extra index.json sources added in Settings (name filled from the index itself). */
    val indexLinks: Flow<List<IndexLink>> = context.settingsDataStore.data.map { SourceCodec.decodeLinks(it[Keys.indexLinks]) }

    suspend fun setIndexLinks(list: List<IndexLink>) {
        context.settingsDataStore.edit { it[Keys.indexLinks] = SourceCodec.encodeLinks(list) }
    }

    /** Coaching / subject / kind of installed papers, keyed by bank id (taken from the index they came from). */
    val paperMeta: Flow<Map<String, PaperMeta>> = context.settingsDataStore.data.map { SourceCodec.decodeMeta(it[Keys.paperMeta]) }

    suspend fun putPaperMeta(bankId: String, meta: PaperMeta) {
        context.settingsDataStore.edit {
            val current = SourceCodec.decodeMeta(it[Keys.paperMeta]).toMutableMap()
            current[bankId] = meta
            it[Keys.paperMeta] = SourceCodec.encodeMeta(current)
        }
    }

    /** Bank ids of papers the student marked as completed. */
    val completedPapers: Flow<Set<String>> = context.settingsDataStore.data.map { it[Keys.completedPapers] ?: emptySet() }

    suspend fun setPaperCompleted(bankId: String, done: Boolean) {
        context.settingsDataStore.edit {
            val current = it[Keys.completedPapers] ?: emptySet()
            it[Keys.completedPapers] = if (done) current + bankId else current - bankId
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[Keys.appTheme] = theme.storageKey }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.settingsDataStore.edit { it[Keys.dark] = dark }
    }

    suspend fun setAmoled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.amoled] = enabled }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ai] = enabled }
    }

    suspend fun setAiModel(model: String) {
        context.settingsDataStore.edit { it[Keys.aiModel] = model.trim() }
    }

    suspend fun setNcertIndexUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.ncertIndex] = url.trim() }
    }

    suspend fun setExam(exam: Exam) {
        context.settingsDataStore.edit { it[Keys.exam] = exam.name }
    }

    suspend fun setPyqIndexUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.pyqIndex] = url.trim() }
    }
}
