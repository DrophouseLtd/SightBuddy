package com.example.sightbuddy.features.vision

import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectCommandResolverTest {

    private val noopTransport = OpenAiTransport(apiKeyProvider = { "" })

    @Test
    fun `Polish object commands resolve offline without confusing animal mouse`() = runBlocking {
        val old = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pl"))
        try {
            val resolver = ObjectCommandResolver(transport = noopTransport)
            mapOf("kubek" to "cup", "znajdź kubek" to "cup", "szukam butelki" to "bottle",
                "znajdź osobę" to "person", "szukam widelca" to "fork",
                "mysz komputerowa" to "mouse").forEach { (input, expected) ->
                val result = resolver.resolve(input, llmEnabled = false)
                assertTrue("$input: $result", result is ObjectCommandResolver.ResolveResult.Activate)
                assertEquals(input, expected, (result as ObjectCommandResolver.ResolveResult.Activate).item)
                assertEquals(ObjectCommandResolver.Source.LOCAL, result.source)
            }
            listOf("żywa mysz polna", "mysz zwierzę", "dzika mysz leśna", "smok", "znajdź kubek i butelkę", "kubek i bottle").forEach { input ->
                assertTrue(input, resolver.resolve(input, llmEnabled = false) !is ObjectCommandResolver.ResolveResult.Activate)
            }
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test
    fun `all Polish display names resolve to their detector identifiers`() = runBlocking {
        val old = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pl"))
        try {
            val resolver = ObjectCommandResolver(transport = noopTransport)
            COCO_OBJECTS.forEach { id ->
                val name = CocoFinnish.displayName(id)
                val result = resolver.resolve(name, llmEnabled = false)
                assertTrue("$name: $result", result is ObjectCommandResolver.ResolveResult.Activate)
                assertEquals(name, id, (result as ObjectCommandResolver.ResolveResult.Activate).item)
            }
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

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
