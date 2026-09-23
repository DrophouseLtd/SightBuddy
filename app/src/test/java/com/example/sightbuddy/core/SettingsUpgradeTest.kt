package com.example.sightbuddy.core

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An update must keep every setting a user chose. Each test starts
 * [SettingsManager] on the preferences an earlier version left behind, exactly
 * as that version wrote them (same keys, same types), and checks what the new
 * version reads from them.
 *
 * Renaming a key, changing its type or changing a default all show up here.
 * A deliberate change of default belongs in [untouchedSettingsFollowTheDefaults].
 */
class SettingsUpgradeTest {

    /** Everything 2.2.0 (versionCode 13) can store, each set away from its 2.2.0 default. */
    private fun changedIn220(): Map<String, Any> = mapOf(
        "text_preview_enabled" to false,
        "text_chat_enabled" to false,
        "discover_enabled" to false,
        "find_enabled" to false,
        "scan_light_enabled" to false,
        "scan_colour_enabled" to false,
        "image_chat_enabled" to false,
        "llm_chat_enabled" to false,
        "high_contrast_enabled" to true,
        "white_mode_enabled" to true,
        "loop_carousel" to true,
        "cloud_stt" to true,
        "cloud_stt_asked" to true,
        "pitch_feedback" to true,
        "hold_to_speak_enabled" to true,
        "auto_capture_enabled" to false,
        "crash_reporting_enabled" to false,
        "llm_model" to SettingsManager.MODEL_SMART,
        "tts_speech_rate" to 1.5f,
        "stt_download_choice" to SettingsManager.STT_CHOICE_NEVER,
        "coco_hidden_objects" to setOf("toaster", "kite", "zebra"),
        // Retired since 2.2.0; nothing reads them any more.
        "button_nav_enabled" to false,
        "feature_activation_announcements_enabled" to false,
    )

    @Test
    fun everyChoiceMadeIn220Survives() {
        val settings = SettingsManager(contextWith(FakePrefs(changedIn220())))

        assertFalse(settings.textPreviewEnabled.value)
        assertFalse(settings.textChatEnabled.value)
        assertFalse(settings.discoverEnabled.value)
        assertFalse(settings.findEnabled.value)
        assertFalse(settings.scanLightEnabled.value)
        assertFalse(settings.scanColourEnabled.value)
        assertFalse(settings.imageChatEnabled.value)
        assertFalse(settings.llmChatEnabled.value)
        assertTrue(settings.highContrastEnabled.value)
        assertTrue(settings.whiteModeEnabled.value)
        assertTrue(settings.loopCarousel.value)
        assertTrue(settings.cloudStt.value)
        assertTrue(settings.cloudSttAsked.value)
        assertTrue(settings.pitchFeedback.value)
        assertTrue(settings.holdToSpeak.value)
        assertFalse(settings.autoCaptureEnabled.value)
        assertFalse(settings.crashReportingEnabled.value)
        assertEquals(SettingsManager.MODEL_SMART, settings.llmModel.value)
        assertEquals(1.5f, settings.ttsSpeechRate.value)
        assertEquals(SettingsManager.STT_CHOICE_NEVER, settings.sttDownloadChoice.value)
        assertEquals(setOf("toaster", "kite", "zebra"), settings.hiddenCocoObjects.value)
    }

    @Test
    fun retiredKeysAreRemovedAndNothingElse() {
        val prefs = FakePrefs(changedIn220())
        SettingsManager(contextWith(prefs))

        assertFalse(prefs.contains("button_nav_enabled"))
        assertFalse(prefs.contains("feature_activation_announcements_enabled"))
        val kept = changedIn220().keys - setOf("button_nav_enabled", "feature_activation_announcements_enabled")
        kept.forEach { assertEquals("$it was changed", changedIn220()[it], prefs.all[it]) }
    }

    /**
     * A 2.2.0 user who never touched a setting has no stored value, so they get
     * the new version's default. Each line here is a decision about existing
     * users, not only new ones.
     */
    @Test
    fun untouchedSettingsFollowTheDefaults() {
        val settings = SettingsManager(contextWith(FakePrefs(emptyMap())))

        // Unchanged since 2.2.0.
        assertTrue(settings.textChatEnabled.value)
        assertTrue(settings.discoverEnabled.value)
        assertTrue(settings.findEnabled.value)
        assertTrue(settings.scanLightEnabled.value)
        assertTrue(settings.scanColourEnabled.value)
        assertTrue(settings.imageChatEnabled.value)
        assertFalse(settings.highContrastEnabled.value)
        assertFalse(settings.cloudStt.value)
        assertTrue(settings.crashReportingEnabled.value)
        assertEquals(SettingsManager.MODEL_BALANCED, settings.llmModel.value)
        assertEquals(1.0f, settings.ttsSpeechRate.value)
        assertEquals("", settings.sttDownloadChoice.value)

        // Changed on purpose in 2.3.0.
        assertTrue(settings.loopCarousel.value)

        // New in 2.3.0.
        assertTrue(settings.useApi.value)
        assertTrue(settings.useLocalChat.value)
        assertEquals("whisper", settings.sttChoice.value)
        assertTrue(settings.hapticFeedback.value)
        assertTrue(settings.cameraPreview.value)
        assertFalse(settings.liveText.value)
        assertTrue(settings.textScanGuidance.value)
        assertFalse(settings.startFeaturesMuted.value)
    }

    private fun contextWith(prefs: SharedPreferences): Context = object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    /** An in-memory SharedPreferences: reads and writes land at once. */
    private class FakePrefs(initial: Map<String, Any>) : SharedPreferences {
        private val values = initial.toMutableMap()

        override fun getAll(): Map<String, *> = values.toMap()
        override fun contains(key: String?) = key in values
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?) = also { changes[key] = value }
            override fun putStringSet(key: String, value: MutableSet<String>?) = also { changes[key] = value?.toSet() }
            override fun putInt(key: String, value: Int) = also { changes[key] = value }
            override fun putLong(key: String, value: Long) = also { changes[key] = value }
            override fun putFloat(key: String, value: Float) = also { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean) = also { changes[key] = value }
            override fun remove(key: String) = also { changes[key] = null }
            override fun clear() = also { clear = true }
            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) values.clear()
                changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            }
        }
    }
}
