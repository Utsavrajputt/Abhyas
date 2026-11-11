package com.invictus.abhyas.domain.ai

/** What the student asked the AI to do. Every feature is optional; the app never needs any of them. */
enum class AiFeature(val label: String) {
    EXPLAIN("Explain"),
    SIMPLIFY("Simplify"),
    NOTES("Notes"),
    ASK("Ask"),
    GENERATE("Generate questions"),
}

/** Provider-neutral prompt. [json] asks the provider for a JSON-only reply. */
data class AiPrompt(
    val system: String,
    val user: String,
    val json: Boolean = false,
)

enum class AiError {
    DISABLED,
    NO_KEY,
    INVALID_KEY,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    BLOCKED,
    BAD_RESPONSE,
    SERVER,
    UNKNOWN,
}

sealed interface AiResult {
    data class Text(val text: String) : AiResult
    data class Failure(val error: AiError, val message: String) : AiResult
}

/** One model backend. Implementations must never put the API key in a URL, log line or error message. */
interface AiProvider {
    suspend fun generate(prompt: AiPrompt, model: String, apiKey: String): AiResult

    /** Model ids this key can use for text generation. Providers that cannot list models keep the default. */
    suspend fun listModels(apiKey: String): ModelsResult = ModelsResult.Failure("This provider cannot list models.")
}

sealed interface ModelsResult {
    data class Models(val ids: List<String>) : ModelsResult
    data class Failure(val message: String) : ModelsResult
}

object AiDefaults {
    const val MODEL = "gemini-2.5-flash"

    /** Quick picks shown in Settings; any other id can be typed in. */
    val PRESETS: List<Pair<String, String>> = listOf(
        "Flash" to "gemini-2.5-flash",
        "Flash-Lite" to "gemini-2.5-flash-lite",
        "Pro" to "gemini-2.5-pro",
    )

    private val MODEL_ID = Regex("^[A-Za-z0-9._-]{1,64}$")

    /** Model ids end up in a URL path, so only a safe alphabet is accepted. */
    fun isValidModel(id: String): Boolean = MODEL_ID.matches(id)
}

object ApiKeyFormat {
    /** Light sanity check only (no whitespace, plausible length); the server is the real judge. */
    fun isPlausible(raw: String): Boolean {
        val k = raw.trim()
        return k.length in 20..200 && k.none { it.isWhitespace() }
    }
}
