package com.example.sightbuddy.core

import com.example.sightbuddy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The feedback email can carry the app version, the phone and which
 * settings are on, but only when the user allows it, with a switch that is
 * off by default.
 */
class FeedbackEmailTest {

    /** The English strings the body is made of, as in values/strings.xml. */
    private val english = mapOf(
        R.string.feedback_write_above to "Please write above this line.",
        R.string.settings_version to "Version %1\$s (%2\$d)",
        R.string.feedback_detail_phone to "Phone",
        R.string.feedback_detail_screen_reader to "Screen reader",
        R.string.settings_language to "App language",
        R.string.feedback_detail_features to "Features shown",
        R.string.settings_camera_preview to "Camera preview",
        R.string.settings_live_text to "Live text",
        R.string.settings_sound_effects to "Sound effects",
        R.string.settings_vibration to "Vibration",
        R.string.bar_pitch to "Pitch",
        R.string.settings_speech_rate to "Speech rate",
        R.string.settings_stt_engine to "Speech recognition",
        R.string.settings_use_local_chat to "Use local chat AI",
        R.string.feedback_detail_api_key to "OpenAI API key saved",
        R.string.feedback_value_on to "on",
        R.string.feedback_value_off to "off",
        R.string.feedback_value_yes to "yes",
        R.string.feedback_value_no to "no",
    )
    private val say: (Int, Array<out Any>) -> String = { id, args -> english.getValue(id).format(*args) }

    private val details = FeedbackDetails(
        appVersion = "2.4.0",
        appBuild = 15,
        androidVersion = "15",
        androidSdk = 35,
        phone = "Example Phone 1",
        screenReaderOn = true,
        appLanguage = "English",
        featuresShown = listOf("Image chat", "Text chat"),
        cameraPreview = true,
        liveText = false,
        soundEffects = true,
        vibration = false,
        pitchFeedback = false,
        speechRate = "Fast",
        speechRecognition = "Whisper (recommended)",
        onDeviceChat = false,
        apiKeySaved = true,
    )

    @Test
    fun notAllowedTheEmailCarriesNothingAboutThePhoneOrTheApp() {
        var gathered = false
        val body = FeedbackEmail.body(allowed = false, details = { gathered = true; details }, say = say)

        assertEquals("", body)
        assertFalse("The details were gathered although not allowed", gathered)
    }

    @Test
    fun allowedTheEmailCarriesTheDetailsBelowALineToWriteAbove() {
        val body = FeedbackEmail.body(allowed = true, details = { details }, say = say)

        assertEquals(
            listOf(
                // Room for the message, where the cursor starts.
                "",
                "",
                "Please write above this line.",
                "Version 2.4.0 (15)",
                "Android 15 (API 35)",
                "Phone: Example Phone 1",
                "Screen reader: on",
                "App language: English",
                "Features shown: Image chat, Text chat",
                "Camera preview: on",
                "Live text: off",
                "Sound effects: on",
                "Vibration: off",
                "Pitch: off",
                "Speech rate: Fast",
                "Speech recognition: Whisper (recommended)",
                "Use local chat AI: off",
                "OpenAI API key saved: yes",
            ),
            body.lines(),
        )
    }
}
