package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.provider.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderChoiceTest {

    @Test
    fun theChosenProviderIsUsedWhileItIsSetUp() {
        val configured = listOf(ProviderKind.ANTHROPIC, ProviderKind.CHATGPT)
        assertEquals(ProviderKind.CHATGPT, resolveProvider(ProviderKind.CHATGPT, configured))
    }

    @Test
    fun aChosenProviderThatLostItsSignInIsNotReplaced() {
        // Before, the conversation went on with Anthropic's key once the ChatGPT sign-in expired.
        assertNull(resolveProvider(ProviderKind.CHATGPT, listOf(ProviderKind.ANTHROPIC)))
        assertNull(resolveProvider(ProviderKind.CHATGPT, emptyList()))
    }

    @Test
    fun withoutAChoiceTheFirstOneSetUpIsUsed() {
        assertEquals(ProviderKind.OPENAI, resolveProvider(null, listOf(ProviderKind.OPENAI, ProviderKind.CUSTOM)))
        assertNull(resolveProvider(null, emptyList()))
    }
}
