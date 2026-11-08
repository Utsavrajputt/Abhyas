package com.invictus.abhyas.data.ai

import com.invictus.abhyas.domain.ai.AiDefaults
import com.invictus.abhyas.domain.ai.AiError
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiProvider
import com.invictus.abhyas.domain.ai.AiResult
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
    }
}
