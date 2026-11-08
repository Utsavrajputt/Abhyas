package com.invictus.abhyas.data.ai

import com.invictus.abhyas.domain.ai.AiError
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Request/response shapes of the Gemini `generateContent` REST call. Pure, so it is unit-tested. */
object GeminiCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_TOKENS = 4096

    fun requestBody(prompt: AiPrompt): String = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { add(buildJsonObject { put("text", prompt.system) }) }
        }
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", prompt.user) }) }
                },
            )
        }
        putJsonObject("generationConfig") {
            put("temperature", 0.4)
            put("maxOutputTokens", MAX_TOKENS)
            if (prompt.json) put("responseMimeType", "application/json")
        }
    }.toString()

    fun parse(status: Int, body: String): AiResult {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        }
        if (status !in 200..299) return failure(status, root)

        val blockReason = ((root?.get("promptFeedback") as? JsonObject)?.get("blockReason") as? JsonPrimitive)?.contentOrNull
        if (blockReason != null) return AiResult.Failure(AiError.BLOCKED, "The request was blocked by the model's safety filter.")

        val candidate = ((root?.get("candidates") as? JsonArray)?.firstOrNull() as? JsonObject)
            ?: return AiResult.Failure(AiError.BAD_RESPONSE, "The model returned no answer.")
        val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        val text = parts.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.joinToString("").trim()
        if (text.isNotEmpty()) return AiResult.Text(text)

        val finish = (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull
        return if (finish == "SAFETY" || finish == "RECITATION" || finish == "BLOCKLIST") {
            AiResult.Failure(AiError.BLOCKED, "The answer was blocked by the model's safety filter.")
        } else {
            AiResult.Failure(AiError.BAD_RESPONSE, "The model returned an empty answer.")
        }
    }

    private fun failure(status: Int, root: JsonObject?): AiResult.Failure {
        val message = (((root?.get("error") as? JsonObject)?.get("message")) as? JsonPrimitive)?.contentOrNull.orEmpty()
        return when {
            status == 401 || status == 403 -> AiResult.Failure(AiError.INVALID_KEY, "The API key was rejected. Check it in Settings.")
            status == 400 && message.contains("api key", ignoreCase = true) ->
                AiResult.Failure(AiError.INVALID_KEY, "The API key is not valid. Check it in Settings.")
            status == 429 -> AiResult.Failure(AiError.RATE_LIMITED, "Rate limit or quota reached. Try again in a minute.")
            status == 404 -> AiResult.Failure(AiError.UNKNOWN, "Model not found. Check the model id in Settings.")
            status >= 500 -> AiResult.Failure(AiError.SERVER, "Gemini is having trouble right now. Try again later.")
            else -> AiResult.Failure(AiError.UNKNOWN, "Request failed ($status)" + if (message.isNotBlank()) ": ${message.take(160)}" else "")
        }
    }
}
