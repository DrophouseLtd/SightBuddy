package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.stt.SttModelSet.LEGACY_EN
import com.example.sightbuddy.core.stt.SttModelSet.MULTILINGUAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the running app does when the language changes, and how the three
 * recognisers rank against each other for a language the installed model does
 * or does not serve.
 */
class SttEnginePlanTest {

    private fun plan(modern: Boolean, legacy: Boolean, tag: String) =
        WhisperPlan.choose(
            ready = { it == MULTILINGUAL && modern || it == LEGACY_EN && legacy },
            appLanguageTag = tag,
        )

    @Test
    fun aLanguageChangeNeedsTheModelReopenedInTheNewLanguage() {
        // sherpa-onnx takes the language when the recogniser is built, so a
        // loaded engine cannot simply be asked to switch: it is replaced.
        val english = plan(modern = true, legacy = false, tag = "en")!!
        val polish = plan(modern = true, legacy = false, tag = "pl")!!
        assertTrue(polish.requiresReload(english))
        assertTrue(english.requiresReload(polish))
        // The same language on the same model: nothing to do.
        assertFalse(polish.requiresReload(plan(modern = true, legacy = false, tag = "pl")))
        // Nothing loaded yet is a load, not a reload.
        assertTrue(polish.requiresReload(null))
    }

    @Test
    fun aLanguageTheLegacyModelCannotServeDropsTheEngine() {
        // Phone has base.en only, user switches to Polish: the plan is null, so
        // the service unloads the engine rather than transcribing Polish as English.
        assertNull(plan(modern = false, legacy = true, tag = "pl"))
        // And back to English finds it again.
        assertEquals(LEGACY_EN, plan(modern = false, legacy = true, tag = "en")!!.set)
    }

    @Test
    fun theMultilingualModelIsUsedForEveryLanguageEvenWithLegacyInstalled() {
        for (tag in listOf("en", "pl", "fi", "de")) {
            assertEquals(tag, MULTILINGUAL, plan(modern = true, legacy = true, tag = tag)!!.set)
        }
    }

    @Test
    fun cloudStillOutranksTheModelAndThePlatform() {
        // Cloud on: paid for deliberately, so it runs whatever the model can do.
        val multilingualPolish = plan(modern = true, legacy = false, tag = "pl")!!
        val speech = SpeechAvailability(
            modelServesLanguage = true,
            modelDownloaded = multilingualPolish.set == MULTILINGUAL,
            cloudEnabled = true,
        )
        assertEquals(SpeechEngine.CLOUD, speech.engine)
        // With cloud off, the same phone records on Whisper and the app owns it.
        assertTrue(SpeechAvailability(true, true, cloudEnabled = false).appOwnsRecording)

        // Legacy only, Polish, cloud off: the platform recogniser, as before.
        val legacyPolish = SpeechAvailability(modelServesLanguage = false, modelDownloaded = true)
        assertEquals(SpeechEngine.PLATFORM, legacyPolish.engine)
        assertFalse(legacyPolish.appOwnsRecording)
        // Cloud on again: still the cloud, even without a model for the language.
        assertEquals(SpeechEngine.CLOUD, SpeechAvailability(false, true, cloudEnabled = true).engine)
    }

    @Test
    fun theModelThatServesTheLanguageDecidesTheChoiceInSettings() {
        // Multilingual ready in Polish: the choice stays Whisper, not Android.
        assertEquals(
            SttChoice.WHISPER,
            SttChoice.effective(SttChoice.WHISPER, whisperReady = true, gemmaInstalled = false, english = false, whisperServesLanguage = true),
        )
        // Legacy ready in Polish: never Whisper, and Gemma is English-only.
        assertEquals(
            SttChoice.ANDROID,
            SttChoice.effective(SttChoice.WHISPER, whisperReady = true, gemmaInstalled = true, english = false, whisperServesLanguage = false),
        )
    }
}
