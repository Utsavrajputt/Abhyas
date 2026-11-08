package com.invictus.abhyas.data.ai

import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.domain.ai.AiError
import com.invictus.abhyas.domain.ai.AiPrompt
import com.invictus.abhyas.domain.ai.AiProvider
import com.invictus.abhyas.domain.ai.AiResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance: with AI off (or no key) the provider, and so the network, is never touched. */
class AiGatewayTest {

    private class FakeProvider(var reply: AiResult = AiResult.Text("ok")) : AiProvider {
        var calls = 0
        var lastModel: String? = null
        override suspend fun generate(prompt: AiPrompt, model: String, apiKey: String): AiResult {
            calls++
            lastModel = model
            return reply
        }
    }

    private val prompt = AiPrompt("s", "u")
    private val key = "TESTKEY" + "x".repeat(20)

    private fun gateway(enabled: Boolean, apiKey: String?, provider: AiProvider, model: String = "gemini-2.5-flash") =
        AiGateway(flowOf(UserSettings(aiEnabled = enabled, aiModel = model)), flowOf(apiKey != null), { apiKey }, provider)

    @Test
    fun switchedOffMakesNoCall() = runBlocking {
        val p = FakeProvider()
        val r = gateway(enabled = false, apiKey = key, provider = p).run(prompt)
        assertEquals(AiError.DISABLED, (r as AiResult.Failure).error)
        assertEquals(0, p.calls)
    }

    @Test
    fun noKeyMakesNoCall() = runBlocking {
        val p = FakeProvider()
        val r = gateway(enabled = true, apiKey = null, provider = p).run(prompt)
        assertEquals(AiError.NO_KEY, (r as AiResult.Failure).error)
        assertEquals(0, p.calls)
    }

    @Test
    fun onWithKeyCallsTheProviderOnceWithTheChosenModel() = runBlocking {
        val p = FakeProvider()
        val r = gateway(enabled = true, apiKey = key, provider = p, model = "gemini-2.5-pro").run(prompt)
        assertEquals(AiResult.Text("ok"), r)
        assertEquals(1, p.calls)
        assertEquals("gemini-2.5-pro", p.lastModel)
    }

    @Test
    fun theKeyNeverAppearsInAnErrorMessage() = runBlocking {
        val p = FakeProvider(AiResult.Failure(AiError.UNKNOWN, "rejected $key by server"))
        val r = gateway(enabled = true, apiKey = key, provider = p).run(prompt) as AiResult.Failure
        assertFalse(r.message.contains(key))
        assertTrue(r.message.contains("***"))
    }

    @Test
    fun readyNeedsBothTheSwitchAndAKey() = runBlocking {
        val p = FakeProvider()
        assertTrue(gateway(true, key, p).ready.first())
        assertFalse(gateway(false, key, p).ready.first())
        assertFalse(gateway(true, null, p).ready.first())
    }
}
