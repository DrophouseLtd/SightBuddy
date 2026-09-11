package com.example.sightbuddy.features.vision

import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectCommandResolverTest {

    private val noopTransport = OpenAiTransport(apiKeyProvider = { "" })

    @Test
    fun resolvesItemWordEmbeddedInLongerCommandLocally() = runBlocking {
        val resolver = ObjectCommandResolver(transport = noopTransport)

        val result = resolver.resolve("Where is my coffeCUP", llmEnabled = false)

        assertTrue(result is ObjectCommandResolver.ResolveResult.Activate)
        result as ObjectCommandResolver.ResolveResult.Activate
        assertEquals("cup", result.item)
        assertEquals(ObjectCommandResolver.Source.LOCAL, result.source)
    }

    @Test
    fun returnsItemListMessageWhenLocalAndAiCannotResolve() = runBlocking {
        val resolver = ObjectCommandResolver(transport = noopTransport)

        val result = resolver.resolve("dragon", llmEnabled = false)

        assertTrue(result is ObjectCommandResolver.ResolveResult.Unavailable)
        result as ObjectCommandResolver.ResolveResult.Unavailable
        assertEquals("I'm sorry I couldn't find \"dragon\" from the item list.", result.message)
    }
}
