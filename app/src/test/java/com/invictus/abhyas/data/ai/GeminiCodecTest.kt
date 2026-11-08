package com.invictus.abhyas.data.ai

import com.invictus.abhyas.domain.ai.AiError
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCodecTest {

    private fun error(status: Int, body: String): AiError = (GeminiCodec.parse(status, body) as AiResult.Failure).error

    @Test
    fun requestHasSystemUserAndOptionalJsonMode() {
        val plain = GeminiCodec.requestBody(AiPrompt("SYS TEST", "USER TEST"))
        assertTrue(plain.contains("\"systemInstruction\""))
        assertTrue(plain.contains("SYS TEST"))
        assertTrue(plain.contains("USER TEST"))
        assertTrue(plain.contains("\"maxOutputTokens\""))
        assertFalse(plain.contains("responseMimeType"))

        val json = GeminiCodec.requestBody(AiPrompt("s", "u", json = true))
        assertTrue(json.contains("\"responseMimeType\":\"application/json\""))
    }

    @Test
    fun successJoinsAllTextParts() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"Hello "},{"text":"world"}]},"finishReason":"STOP"}]}"""
        assertEquals(AiResult.Text("Hello world"), GeminiCodec.parse(200, body))
    }

    @Test
    fun promptBlockAndSafetyFinishAreBlocked() {
        assertEquals(AiError.BLOCKED, error(200, """{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(AiError.BLOCKED, error(200, """{"candidates":[{"content":{"parts":[]},"finishReason":"SAFETY"}]}"""))
    }

    @Test
    fun emptyOrBrokenAnswersAreBadResponses() {
        assertEquals(AiError.BAD_RESPONSE, error(200, """{"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}]}"""))
        assertEquals(AiError.BAD_RESPONSE, error(200, """{"candidates":[]}"""))
        assertEquals(AiError.BAD_RESPONSE, error(200, "oops"))
    }

    @Test
    fun httpStatusesMapToUsefulErrors() {
        assertEquals(AiError.INVALID_KEY, error(400, """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""))
        assertEquals(AiError.INVALID_KEY, error(401, "{}"))
        assertEquals(AiError.INVALID_KEY, error(403, "{}"))
        assertEquals(AiError.RATE_LIMITED, error(429, "{}"))
        assertEquals(AiError.UNKNOWN, error(404, "{}"))
        assertEquals(AiError.SERVER, error(503, "not json"))
        assertEquals(AiError.UNKNOWN, error(400, """{"error":{"message":"bad field"}}"""))
    }

    @Test
    fun errorMessagesStaySmall() {
        val long = "x".repeat(2000)
        val r = GeminiCodec.parse(418, """{"error":{"message":"$long"}}""") as AiResult.Failure
        assertTrue(r.message.length < 260)
    }
}
