package com.invictus.abhyas.data.ai

import com.invictus.abhyas.domain.ai.AiDefaults
import com.invictus.abhyas.domain.ai.AiError
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiProvider
import com.invictus.abhyas.domain.ai.AiResult
import com.invictus.abhyas.domain.ai.ModelsResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Google Gemini over plain HTTPS (REST `generateContent`). The key travels in the `x-goog-api-key`
 * header, never in the URL, so it cannot end up in logs or crash reports.
 */
class GeminiProvider(private val timeoutMs: Int = 40_000) : AiProvider {

    override suspend fun generate(prompt: AiPrompt, model: String, apiKey: String): AiResult = withContext(Dispatchers.IO) {
        call(prompt, model, apiKey)
    }

    override suspend fun listModels(apiKey: String): ModelsResult = withContext(Dispatchers.IO) {
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("x-goog-api-key", apiKey)
            val status = conn.responseCode
            val stream = if (status < 400) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBounded(MAX_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            when {
                status == 400 || status == 401 || status == 403 -> ModelsResult.Failure("The API key was not accepted.")
                status >= 400 -> ModelsResult.Failure("Could not load models (error $status).")
                else -> ModelsResult.Models(parseModels(body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ModelsResult.Failure("No connection. Could not load models.")
        } catch (e: Exception) {
            ModelsResult.Failure("Could not read the model list.")
        } finally {
            conn.disconnect()
        }
    }

    private fun call(prompt: AiPrompt, model: String, apiKey: String): AiResult {
        if (!AiDefaults.isValidModel(model)) return AiResult.Failure(AiError.UNKNOWN, "The model id has characters that are not allowed.")
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(GeminiCodec.requestBody(prompt).toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status < 400) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBounded(MAX_BYTES) }?.toString(Charsets.UTF_8).orEmpty()
            GeminiCodec.parse(status, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            AiResult.Failure(AiError.TIMEOUT, "The request timed out. Check your connection and try again.")
        } catch (e: UnknownHostException) {
            AiResult.Failure(AiError.OFFLINE, "No internet connection.")
        } catch (e: ConnectException) {
            AiResult.Failure(AiError.OFFLINE, "Could not reach Gemini. Check your connection.")
        } catch (e: IOException) {
            AiResult.Failure(AiError.OFFLINE, "Network error. Check your connection and try again.")
        } finally {
            conn.disconnect()
        }
    }

    private fun InputStream.readBounded(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            if (out.size() + n > max) throw IOException("Response too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 1_000_000

        /** Text models that support generateContent, newest-looking first (e.g. "gemini-2.5-flash"). */
        fun parseModels(body: String): List<String> {
            val arr = Json.parseToJsonElement(body).jsonObject["models"] as? JsonArray ?: return emptyList()
            return arr.map { it.jsonObject }
                .filter { m -> m["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
                .map { it["name"]!!.jsonPrimitive.content.removePrefix("models/") }
                .filter { it.startsWith("gemini") && AiDefaults.isValidModel(it) }
                .filterNot { id -> listOf("embedding", "tts", "image", "live", "audio", "robotics", "computer-use").any { it in id } }
                .distinct()
                .sortedDescending()
        }
    }
}
