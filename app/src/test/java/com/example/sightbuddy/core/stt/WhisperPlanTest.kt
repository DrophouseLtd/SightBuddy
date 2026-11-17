package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.stt.SttModelSet.LEGACY_EN
import com.example.sightbuddy.core.stt.SttModelSet.MULTILINGUAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which model runs and in which language: the modern multilingual base, the old
 * base.en kept on phones that already had it, or nothing (the platform
 * recogniser). Legacy is English only; the multilingual model is told the app's
 * language and falls back to Whisper's own detection for languages it does not
 * list.
 */
class WhisperPlanTest {

    private fun plan(modern: Boolean, legacy: Boolean, tag: String): WhisperPlan? =
        WhisperPlan.choose(
            ready = { set ->
                when (set) {
                    MULTILINGUAL -> modern
                    LEGACY_EN -> legacy
                }
            },
            appLanguageTag = tag,
        )

    @Test
    fun theMultilingualModelIsToldPolishFinnishAndEnglish() {
        assertEquals(WhisperPlan(MULTILINGUAL, "pl"), plan(modern = true, legacy = false, tag = "pl"))
        assertEquals(WhisperPlan(MULTILINGUAL, "fi"), plan(modern = true, legacy = false, tag = "fi"))
        assertEquals(WhisperPlan(MULTILINGUAL, "en"), plan(modern = true, legacy = false, tag = "en"))
    }

    @Test
    fun aRegionTagIsNarrowedToItsLanguage() {
        assertEquals(WhisperPlan(MULTILINGUAL, "pl"), plan(modern = true, legacy = false, tag = "pl-PL"))
        assertEquals(WhisperPlan(LEGACY_EN, "en"), plan(modern = false, legacy = true, tag = "en-GB"))
    }

    @Test
    fun theMultilingualModelReplacesLegacyOnceItIsDownloaded() {
        val both = plan(modern = true, legacy = true, tag = "en")
        assertEquals(MULTILINGUAL, both?.set)
    }

    @Test
    fun theLegacyModelIsEnglishOnly() {
        assertNull(plan(modern = false, legacy = true, tag = "pl"))
        assertNull(plan(modern = false, legacy = true, tag = "fi"))
        assertEquals(WhisperPlan(LEGACY_EN, "en"), plan(modern = false, legacy = true, tag = "en"))
    }

    @Test
    fun aLanguageWhisperDoesNotListAsksTheModelToDetectIt() {
        assertEquals(WhisperPlan(MULTILINGUAL, ""), plan(modern = true, legacy = false, tag = "tlh"))
        // An empty or unknown tag is detection too, not English.
        assertEquals(WhisperPlan(MULTILINGUAL, ""), plan(modern = true, legacy = false, tag = ""))
    }

    @Test
    fun changingTheAppLanguageChangesTheLanguageNotTheModel() {
        val english = plan(modern = true, legacy = true, tag = "en")!!
        val polish = plan(modern = true, legacy = true, tag = "pl")!!
        assertEquals(english.set, polish.set)
        assertEquals("en", english.language)
        assertEquals("pl", polish.language)
        // Dropping back to English keeps the same model, back to "en".
        assertEquals("en", plan(modern = true, legacy = true, tag = "en")!!.language)
    }

    @Test
    fun aPartlyDownloadedOrMissingModelLeavesThePlatformRecogniser() {
        // Not ready means not ready: nothing half-downloaded is ever chosen.
        assertNull(plan(modern = false, legacy = false, tag = "en"))
        assertNull(plan(modern = false, legacy = false, tag = "pl"))
        // Legacy present, this language unsupported: platform, never legacy.
        assertNull(plan(modern = false, legacy = true, tag = "fi"))
    }
}
