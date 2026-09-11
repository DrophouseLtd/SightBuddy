package com.example.sightbuddy.features.vision

import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The Finnish aliases resolve to the English COCO labels the rest of the app
 * runs on, and stay completely inert in any other language.
 */
class CocoFinnishAliasTest {

    private val noopTransport = OpenAiTransport(apiKeyProvider = { "" })
    private val original = Locale.getDefault()

    @Before
    fun useFinnish() {
        Locale.setDefault(Locale.forLanguageTag("fi-FI"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    private fun resolve(input: String) = runBlocking {
        ObjectCommandResolver(transport = noopTransport).resolve(input, llmEnabled = false)
    }

    private fun assertActivates(input: String, expected: String) {
        val result = resolve(input)
        assertTrue("'$input' did not activate, got $result", result is ObjectCommandResolver.ResolveResult.Activate)
        result as ObjectCommandResolver.ResolveResult.Activate
        assertEquals("'$input'", expected, result.item)
        assertEquals(ObjectCommandResolver.Source.LOCAL, result.source)
    }

    @Test
    fun finnishNamesActivateTheEnglishLabel() {
        assertActivates("kuppi", "cup")
        assertActivates("tuoli", "chair")
        assertActivates("kirja", "book")
        assertActivates("polkupyörä", "bicycle")
    }

    @Test
    fun finnishNamesWithUmlautsSurviveNormalization() {
        // These fail if the a-z filter strips ä/ö instead of folding them.
        assertActivates("henkilö", "person")
        assertActivates("kännykkä", "cell phone")
        assertActivates("sänky", "bed")
        assertActivates("pöytä", "dining table")
    }

    @Test
    fun colloquialFinnishSynonymsActivate() {
        assertActivates("muki", "cup")
        assertActivates("läppäri", "laptop")
        assertActivates("vessa", "toilet")
        assertActivates("telkkari", "tv")
        assertActivates("fillari", "bicycle")
    }

    @Test
    fun finnishWordEmbeddedInASentenceActivates() {
        assertActivates("missä on mun läppäri", "laptop")
        assertActivates("etsi kissa", "cat")
    }

    @Test
    fun englishStillWorksWhileFinnishIsActive() {
        assertActivates("cup", "cup")
        assertActivates("where is my coffee mug", "cup")
    }

    @Test
    fun spokenNamesAreFinnishWhileFinnishIsActive() {
        assertEquals("kuppi", CocoFinnish.displayName("cup"))
        assertEquals("kannettava tietokone", CocoFinnish.displayName("laptop"))
        // Unknown labels pass through untouched.
        assertEquals("wombat", CocoFinnish.displayName("wombat"))
    }

    @Test
    fun aliasesAreInertInEnglish() {
        Locale.setDefault(Locale.UK)

        assertTrue(CocoFinnish.aliases().isEmpty())
        assertFalse(CocoFinnish.isActive())
        assertEquals("cup", CocoFinnish.displayName("cup"))

        val result = resolve("muki")
        assertFalse(
            "Finnish alias leaked into the English build: $result",
            result is ObjectCommandResolver.ResolveResult.Activate
        )
    }

    @Test
    fun everyFinnishNameResolvesBackToItsLabel() {
        // Guards against a typo in the table silently dropping an object.
        val unresolved = COCO_OBJECTS.filter { label ->
            val result = resolve(CocoFinnish.displayName(label))
            result !is ObjectCommandResolver.ResolveResult.Activate || result.item != label
        }
        assertEquals("Finnish names that do not resolve back", emptyList<String>(), unresolved)
    }

    @Test
    fun everyLabelHasAFinnishName() {
        // Frisbee and pizza are spelled the same in Finnish. Any other label
        // showing through untranslated means a missing row in the table.
        val untranslated = COCO_OBJECTS.filter { CocoFinnish.displayName(it) == it }
        assertEquals(listOf("frisbee", "pizza"), untranslated)
    }
}
