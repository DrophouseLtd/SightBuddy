package com.example.sightbuddy.features.vision

import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PolishObjectCommandsTest {
    @Test fun inflectedRequestsResolveLocally() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"))
            val resolver = ObjectCommandResolver(OpenAiTransport(apiKeyProvider = { "" }))
            val cases = listOf(
                "szukam osoby" to "person", "znajdź osobę" to "person",
                "szukam komórki" to "cell phone", "szukam filiżanki" to "cup",
                "szukam widelca" to "fork", "znajdź butelkę" to "bottle",
                "poszukaj kluczy" to null, "szukam parasola" to "umbrella",
                "znajdź psa" to "dog", "szukam samochodu" to "car",
                "pokaż mi łyżkę" to "spoon", "gdzie jest książka" to "book",
                "znajdź krzesło" to "chair", "szukam telefonu" to "cell phone",
                "znajdź kota lub psa" to null, "szukam kluczy i portfela" to null,
                "mysz zwierzę" to null, "znajdź mysz polną" to null,
                "szukam myszy zwierzęcia" to null,
                "mysz domowa" to null, "mysz" to null, "szukam myszki" to null,
                "mysz komputerowa" to "mouse", "znajdź myszkę komputerową" to "mouse",
                "szukam myszy do komputera" to "mouse"
            )
            for ((text, expected) in cases) {
                assertEquals(text, expected, resolver.findLocalMatch(text)?.item)
                val resolved = runBlocking { resolver.resolve(text, llmEnabled = false) }
                if (expected == null) assertFalse(text, resolved is ObjectCommandResolver.ResolveResult.Activate)
                else {
                    assertTrue(text, resolved is ObjectCommandResolver.ResolveResult.Activate)
                    assertEquals(text, expected, (resolved as ObjectCommandResolver.ResolveResult.Activate).item)
                    assertEquals(ObjectCommandResolver.Source.LOCAL, resolved.source)
                }
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun englishAndFinnishControls() {
        val previous = Locale.getDefault()
        try {
            val resolver = ObjectCommandResolver(OpenAiTransport(apiKeyProvider = { "" }))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("cup", resolver.findLocalMatch("find the cup")?.item)
            Locale.setDefault(Locale.forLanguageTag("fi"))
            assertEquals("cup", resolver.findLocalMatch("etsi muki")?.item)
        } finally { Locale.setDefault(previous) }
    }
}
