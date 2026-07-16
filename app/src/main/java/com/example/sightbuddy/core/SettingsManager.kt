package com.example.sightbuddy.core

import android.content.Context
import android.content.SharedPreferences
import com.example.sightbuddy.features.vision.COCO_OBJECTS
import com.example.sightbuddy.features.vision.defaultHiddenCocoObjects
import com.example.sightbuddy.features.vision.filterVisibleCocoObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralised, observable app settings backed by SharedPreferences.
 * Every toggle is exposed as a StateFlow so Compose recomposes automatically.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sight_buddy_settings", Context.MODE_PRIVATE)

    // --- Feature settings ---
    private val _textPreviewEnabled = MutableStateFlow(prefs.getBoolean(KEY_TEXT_PREVIEW, true))
    val textPreviewEnabled = _textPreviewEnabled.asStateFlow()

    private val _scanLightEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_LIGHT, true))
    val scanLightEnabled = _scanLightEnabled.asStateFlow()

    private val _scanColourEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_COLOUR, true))
    val scanColourEnabled = _scanColourEnabled.asStateFlow()

    private val _llmChatEnabled = MutableStateFlow(prefs.getBoolean(KEY_LLM_CHAT, true))
    val llmChatEnabled = _llmChatEnabled.asStateFlow()

    // --- User preferences ---
    private val _highContrastEnabled = MutableStateFlow(prefs.getBoolean(KEY_HIGH_CONTRAST, false))
    val highContrastEnabled = _highContrastEnabled.asStateFlow()

    // Only relevant when high contrast is enabled. OFF = dark, ON = white.
    private val _whiteModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_WHITE_MODE, false))
    val whiteModeEnabled = _whiteModeEnabled.asStateFlow()

    private val _useButtonNav = MutableStateFlow(prefs.getBoolean(KEY_BUTTON_NAV, true))
    val useButtonNav = _useButtonNav.asStateFlow()

    // OFF (default) = tap to start / tap to stop recording; ON = hold while speaking.
    private val _holdToSpeak = MutableStateFlow(prefs.getBoolean(KEY_HOLD_TO_SPEAK, false))
    val holdToSpeak = _holdToSpeak.asStateFlow()

    /** TTS speech rate multiplier; one of [TTS_RATE_OPTIONS]. */
    private val _ttsSpeechRate = MutableStateFlow(
        prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f).let { stored ->
            TTS_RATE_OPTIONS.minByOrNull { kotlin.math.abs(it - stored) } ?: 1.0f
        },
    )
    val ttsSpeechRate = _ttsSpeechRate.asStateFlow()

    private val _featureActivationAnnouncementsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_FEATURE_ACTIVATION_ANNOUNCEMENTS, true),
    )
    val featureActivationAnnouncementsEnabled = _featureActivationAnnouncementsEnabled.asStateFlow()

    /** COCO labels hidden from the Find-objects picker (UI only). */
    private val _hiddenCocoObjects = MutableStateFlow(loadHiddenCocoObjects())
    val hiddenCocoObjects = _hiddenCocoObjects.asStateFlow()

    // --- Mutators ---

    fun setTextPreview(enabled: Boolean) {
        _textPreviewEnabled.value = enabled
        prefs.edit().putBoolean(KEY_TEXT_PREVIEW, enabled).apply()
    }

    fun setScanLight(enabled: Boolean) {
        _scanLightEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SCAN_LIGHT, enabled).apply()
    }

    fun setScanColour(enabled: Boolean) {
        _scanColourEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SCAN_COLOUR, enabled).apply()
    }

    fun setLlmChat(enabled: Boolean) {
        _llmChatEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LLM_CHAT, enabled).apply()
    }

    fun setHighContrast(enabled: Boolean) {
        _highContrastEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun setWhiteMode(enabled: Boolean) {
        _whiteModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_WHITE_MODE, enabled).apply()
    }

    fun setButtonNav(enabled: Boolean) {
        _useButtonNav.value = enabled
        prefs.edit().putBoolean(KEY_BUTTON_NAV, enabled).apply()
    }

    fun setHoldToSpeak(enabled: Boolean) {
        _holdToSpeak.value = enabled
        prefs.edit().putBoolean(KEY_HOLD_TO_SPEAK, enabled).apply()
    }

    fun setTtsSpeechRate(rate: Float) {
        val valid = TTS_RATE_OPTIONS.minByOrNull { kotlin.math.abs(it - rate) } ?: 1.0f
        _ttsSpeechRate.value = valid
        prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, valid).apply()
    }

    /** Advance to the next rate option (wraps around). Returns the new rate. */
    fun cycleTtsSpeechRate(): Float {
        val idx = TTS_RATE_OPTIONS.indexOf(_ttsSpeechRate.value)
        val next = TTS_RATE_OPTIONS[(idx + 1) % TTS_RATE_OPTIONS.size]
        setTtsSpeechRate(next)
        return next
    }

    fun setFeatureActivationAnnouncements(enabled: Boolean) {
        _featureActivationAnnouncementsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_FEATURE_ACTIVATION_ANNOUNCEMENTS, enabled).apply()
    }

    fun visibleCocoObjects(): List<String> = filterVisibleCocoObjects(_hiddenCocoObjects.value)

    fun isCocoObjectVisible(item: String): Boolean = item !in _hiddenCocoObjects.value

    fun setCocoObjectVisible(item: String, visible: Boolean) {
        if (item !in COCO_OBJECTS) return
        val updated = _hiddenCocoObjects.value.toMutableSet()
        if (visible) {
            updated.remove(item)
        } else {
            updated.add(item)
        }
        persistHiddenCocoObjects(updated)
    }

    private fun loadHiddenCocoObjects(): Set<String> {
        val stored = prefs.getStringSet(KEY_COCO_HIDDEN, null)
        if (stored != null) return stored.toSet()
        val defaults = defaultHiddenCocoObjects()
        persistHiddenCocoObjects(defaults, writePrefsOnly = true)
        return defaults
    }

    private fun persistHiddenCocoObjects(hidden: Set<String>, writePrefsOnly: Boolean = false) {
        if (!writePrefsOnly) {
            _hiddenCocoObjects.value = hidden
        }
        prefs.edit().putStringSet(KEY_COCO_HIDDEN, HashSet(hidden)).apply()
    }

    companion object {
        private const val KEY_TEXT_PREVIEW = "text_preview_enabled"
        private const val KEY_SCAN_LIGHT = "scan_light_enabled"
        private const val KEY_SCAN_COLOUR = "scan_colour_enabled"
        private const val KEY_LLM_CHAT = "llm_chat_enabled"
        private const val KEY_HIGH_CONTRAST = "high_contrast_enabled"
        private const val KEY_WHITE_MODE = "white_mode_enabled"
        private const val KEY_BUTTON_NAV = "button_nav_enabled"
        private const val KEY_HOLD_TO_SPEAK = "hold_to_speak_enabled"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"

        val TTS_RATE_OPTIONS = listOf(1.0f, 1.5f, 2.0f, 3.0f)

        fun ttsRateLabel(rate: Float): String = when (rate) {
            1.0f -> "Normal"
            1.5f -> "Fast"
            2.0f -> "Very fast"
            3.0f -> "Ultra fast"
            else -> "${rate}x"
        }
        private const val KEY_FEATURE_ACTIVATION_ANNOUNCEMENTS = "feature_activation_announcements_enabled"
        private const val KEY_COCO_HIDDEN = "coco_hidden_objects"
    }
}
