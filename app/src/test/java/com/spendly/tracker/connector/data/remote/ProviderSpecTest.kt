package com.spendly.tracker.connector.data.remote

import com.spendly.tracker.connector.domain.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSpecTest {

    @Test
    fun everyProviderHasTransportDetails() {
        AiProvider.entries.forEach { provider ->
            val spec = provider.spec()
            assertEquals(provider, spec.provider)
            assertTrue("${provider.id} has no base url", spec.baseUrl.startsWith("https://"))
        }
    }

    @Test
    fun freeTierProvidersUseTheOpenAiCompatibleProtocol() {
        val free = AiProvider.entries.filter { it.freeTier }
        assertTrue("expected free providers to exist", free.isNotEmpty())
        free.forEach { provider ->
            assertEquals(
                "${provider.id} should speak chat completions",
                AiProtocol.CHAT_COMPLETIONS,
                provider.spec().protocol
            )
        }
    }

    @Test
    fun paidProvidersKeepTheirNativeProtocols() {
        assertEquals(AiProtocol.RESPONSES, AiProvider.OPENAI.spec().protocol)
        assertEquals(AiProtocol.MESSAGES, AiProvider.ANTHROPIC.spec().protocol)
    }

    @Test
    fun openAiCompatibleBaseUrlsPointAtTheRightHosts() {
        assertTrue(AiProvider.GEMINI.spec().baseUrl.contains("generativelanguage.googleapis.com"))
        assertTrue(AiProvider.GROQ.spec().baseUrl.contains("api.groq.com"))
        assertTrue(AiProvider.OPENROUTER.spec().baseUrl.contains("openrouter.ai"))
    }

    @Test
    fun unknownProviderIdFallsBackToAFreeProvider() {
        assertEquals(AiProvider.GEMINI, AiProvider.fromId("nope"))
        assertEquals(AiProvider.GEMINI, AiProvider.fromId(null))
        assertEquals(AiProvider.GROQ, AiProvider.fromId("groq"))
    }

    @Test
    fun freeProvidersAreListedBeforePaidOnes() {
        val ids = AiProvider.entries.map { it.id }
        val lastFree = ids.indexOfLast { AiProvider.fromId(it).freeTier }
        val firstPaid = ids.indexOfFirst { !AiProvider.fromId(it).freeTier }
        assertTrue("paid providers should come after the free ones", lastFree < firstPaid)
    }
}
