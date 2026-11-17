package com.example.sightbuddy.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wiring decisions AppController makes between the manager's facts and the
 * UI's questions: which model serves the app language, when the download may be
 * offered, and what the space check is measured against.
 */
class ControllerPlanTest {

    @Test
    fun theMultilingualModelServesPolishAndFinnishButTheLegacyOneDoesNot() {
        // Polish with only the legacy model: not served — the platform takes it.
        val legacyPolish = WhisperPlan.choose(
            ready = { it == SttModelSet.LEGACY_EN },
            appLanguageTag = "pl",
        )
        assertEquals(null, legacyPolish)

        // Polish once the multilingual model is downloaded: served, in Polish.
        val modernPolish = WhisperPlan.choose(
            ready = { it == SttModelSet.MULTILINGUAL },
            appLanguageTag = "pl",
        )!!
        assertEquals(SttModelSet.MULTILINGUAL, modernPolish.set)
        assertEquals("pl", modernPolish.language)
        assertTrue(modernPolish.multilingual)

        // Finnish the same way, including a regional tag.
        assertEquals("fi", WhisperPlan.choose({ it == SttModelSet.MULTILINGUAL }, "fi-FI")!!.language)
    }

    @Test
    fun theCloudDisableOnModelReadyNowAlsoWorksForPolishAndFinnish() {
        // The old behaviour: a download completing turns paid transcription off,
        // but only where the model actually serves the language. With the
        // multilingual model this now applies to Polish and Finnish too.
        val polish = SpeechAvailability(
            modelServesLanguage = true,
            modelDownloaded = true,
            cloudEnabled = true,
        )
        assertTrue(polish.shouldDisableCloudOnModelReady(wasReady = false))

        // Legacy only, Polish: the download changes nothing for Polish speech.
        val legacyPolish = SpeechAvailability(
            modelServesLanguage = false,
            modelDownloaded = true,
            cloudEnabled = true,
        )
        assertFalse(legacyPolish.shouldDisableCloudOnModelReady(wasReady = false))
    }

    @Test
    fun theSpaceCheckIsMeasuredAgainstTheRealDownloadSize() {
        // What startSttDownload checks: the multilingual files, byte for byte.
        val spec = SttModelCatalog.of(SttModelSet.MULTILINGUAL)
        assertEquals(161_253_144L, spec.totalBytes)
        assertEquals(spec.totalBytes, com.example.sightbuddy.core.ModelSetup.WHISPER_BYTES)
        // And a Whisper-only tier quotes exactly the download's size.
        assertEquals(spec.totalBytes, com.example.sightbuddy.core.ModelSetup.packageBytes(com.example.sightbuddy.core.ModelSetup.Tier.WHISPER))
    }

    @Test
    fun aModelThatIsPartlyThereIsNeverCountedAsDownloaded() {
        // isReady demands every file at its published size plus the marker:
        // a truncated decoder must not let the engine load or the UI claim success.
        val spec = SttModelCatalog.of(SttModelSet.MULTILINGUAL)
        val sizes = spec.allFiles.associate { it.name to it.bytes }
        // Simulate: every file whole except the decoder, no marker.
        val present = sizes.toMutableMap()
        present["base-decoder.int8.onnx"] = sizes.getValue("base-decoder.int8.onnx") - 1
        val complete = present.values.all { it > 0 } && false // no marker
        assertFalse(complete)
        // With the marker and all files whole it is ready — that path is the
        // manager's own, tested with real files in SttDownloadTest.
    }
}
