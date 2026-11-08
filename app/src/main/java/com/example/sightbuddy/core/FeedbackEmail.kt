package com.example.sightbuddy.core

import com.example.sightbuddy.R

/**
 * What a feedback email may say about the app and the phone, once the user has
 * allowed it in Settings. Only these: no messages, no chat content, and of the
 * API key only whether one is saved.
 */
data class FeedbackDetails(
    val appVersion: String,
    val appBuild: Int,
    val androidVersion: String,
    val androidSdk: Int,
    val phone: String,
    val screenReaderOn: Boolean,
    val appLanguage: String,
    val featuresShown: List<String>,
    val cameraPreview: Boolean,
    val liveText: Boolean,
    val soundEffects: Boolean,
    val vibration: Boolean,
    val pitchFeedback: Boolean,
    val speechRate: String,
    val speechRecognition: String,
    val onDeviceChat: Boolean,
    val apiKeySaved: Boolean,
)

/** The body of the email Settings' Send feedback opens. */
object FeedbackEmail {

    /**
     * Empty unless [allowed]: then nothing about the phone or the app goes in,
     * and [details] are not even gathered. Allowed: room to write at the top, a
     * line to write above, then the details one per line, in the app's language
     * ([say] reads a string with its arguments). The user sees and can edit all
     * of it before sending.
     */
    fun body(
        allowed: Boolean,
        details: () -> FeedbackDetails,
        say: (Int, Array<out Any>) -> String,
    ): String {
        if (!allowed) return ""
        val d = details()
        fun word(id: Int) = say(id, emptyArray())
        fun line(label: Int, value: String) = word(label) + ": " + value
        fun onOff(on: Boolean) = word(if (on) R.string.feedback_value_on else R.string.feedback_value_off)
        val lines = listOf(
            say(R.string.settings_version, arrayOf<Any>(d.appVersion, d.appBuild)),
            "Android ${d.androidVersion} (API ${d.androidSdk})",
            line(R.string.feedback_detail_phone, d.phone),
            line(R.string.feedback_detail_screen_reader, onOff(d.screenReaderOn)),
            line(R.string.settings_language, d.appLanguage),
            line(R.string.feedback_detail_features, d.featuresShown.joinToString(", ")),
            line(R.string.settings_camera_preview, onOff(d.cameraPreview)),
            line(R.string.settings_live_text, onOff(d.liveText)),
            line(R.string.settings_sound_effects, onOff(d.soundEffects)),
            line(R.string.settings_vibration, onOff(d.vibration)),
            line(R.string.bar_pitch, onOff(d.pitchFeedback)),
            line(R.string.settings_speech_rate, d.speechRate),
            line(R.string.settings_stt_engine, d.speechRecognition),
            line(R.string.settings_use_local_chat, onOff(d.onDeviceChat)),
            line(
                R.string.feedback_detail_api_key,
                word(if (d.apiKeySaved) R.string.feedback_value_yes else R.string.feedback_value_no),
            ),
        )
        return "\n\n" + word(R.string.feedback_write_above) + "\n" + lines.joinToString("\n")
    }
}
