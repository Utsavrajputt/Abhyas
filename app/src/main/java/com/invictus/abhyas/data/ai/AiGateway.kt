package com.invictus.abhyas.data.ai

import com.invictus.abhyas.data.prefs.SettingsStore
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.domain.ai.AiDefaults
import com.invictus.abhyas.domain.ai.AiError
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiPrompts
import com.invictus.abhyas.domain.ai.AiProvider
import com.invictus.abhyas.domain.ai.AiResult
import com.invictus.abhyas.domain.ai.ModelsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The only door to the model. Checks the switch and the key, serialises calls (one at a time keeps a
 * student's quota safe), enforces an overall timeout and makes sure the key never appears in a message.
 */
class AiGateway(
    private val settings: Flow<UserSettings>,
    private val keyPresent: Flow<Boolean>,
    private val readKey: () -> String?,
    private val provider: AiProvider,
) {
    constructor(settings: SettingsStore, keys: ApiKeyStore, provider: AiProvider = GeminiProvider()) :
        this(settings.settings, keys.present, { keys.get() }, provider)

    private val lock = Mutex()

    /** True when AI is switched on and a key is saved: the UI shows AI actions only then. */
    val ready: Flow<Boolean> = combine(settings.map { it.aiEnabled }, keyPresent) { on, has -> on && has }
        .distinctUntilChanged()

    suspend fun run(prompt: AiPrompt): AiResult {
        val s = settings.first()
        if (!s.aiEnabled) return AiResult.Failure(AiError.DISABLED, "AI is turned off in Settings.")
        val key = withContext(Dispatchers.IO) { readKey() }
            ?: return AiResult.Failure(AiError.NO_KEY, "Add your Gemini API key in Settings.")
        val model = s.aiModel.trim().ifBlank { AiDefaults.MODEL }
        val result = lock.withLock {
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) { provider.generate(prompt, model, key) }
        } ?: AiResult.Failure(AiError.TIMEOUT, "The request took too long. Try again.")
        return scrub(result, key)
    }

    /** Lists models the saved key can use. Not serialised with [run]: it is one cheap GET. */
    suspend fun models(): ModelsResult {
        val key = withContext(Dispatchers.IO) { readKey() } ?: return ModelsResult.Failure("Add your Gemini API key first.")
        return provider.listModels(key)
    }

    suspend fun test(): AiResult = run(AiPrompts.ping())

    private fun scrub(r: AiResult, key: String): AiResult =
        if (r is AiResult.Failure && key.isNotEmpty()) r.copy(message = r.message.replace(key, "***")) else r

    private companion object {
        const val TOTAL_TIMEOUT_MS = 60_000L
    }
}
