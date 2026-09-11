package com.example.sightbuddy.core

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.example.sightbuddy.R
import com.example.sightbuddy.features.vision.COCO_OBJECTS
import com.example.sightbuddy.features.vision.defaultHiddenCocoObjects
import com.example.sightbuddy.features.vision.filterVisibleCocoObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A selectable OpenAI model shown in Settings (bring-your-own-key). */
data class LlmModelOption(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
)

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

    // Per-feature carousel visibility. These five are always-available (local)
    // features; the guarded setters keep at least one of them enabled so the
    // carousel is never empty. Image chat is separate (key-gated).
    private val _textChatEnabled = MutableStateFlow(prefs.getBoolean(KEY_TEXT_CHAT, true))
    val textChatEnabled = _textChatEnabled.asStateFlow()

    private val _discoverEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCOVER, true))
    val discoverEnabled = _discoverEnabled.asStateFlow()

    private val _findEnabled = MutableStateFlow(prefs.getBoolean(KEY_FIND, true))
    val findEnabled = _findEnabled.asStateFlow()

    private val _scanLightEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_LIGHT, true))
    val scanLightEnabled = _scanLightEnabled.asStateFlow()

    private val _scanColourEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_COLOUR, true))
    val scanColourEnabled = _scanColourEnabled.asStateFlow()

    // Image chat visibility (only shown when an API key is also set).
    private val _imageChatEnabled = MutableStateFlow(prefs.getBoolean(KEY_IMAGE_CHAT, true))
    val imageChatEnabled = _imageChatEnabled.asStateFlow()

    // Unused since BYOK (LLM availability now derives from ApiKeyStore); kept
    // so legacy callers of llmChatEnabled/setLlmChat still compile.
    private val _llmChatEnabled = MutableStateFlow(false)
    val llmChatEnabled = _llmChatEnabled.asStateFlow()

    // --- User preferences ---
    private val _highContrastEnabled = MutableStateFlow(prefs.getBoolean(KEY_HIGH_CONTRAST, false))
    val highContrastEnabled = _highContrastEnabled.asStateFlow()

    // Only relevant when high contrast is enabled. OFF = dark, ON = white.
    private val _whiteModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_WHITE_MODE, false))
    val whiteModeEnabled = _whiteModeEnabled.asStateFlow()

    private val _useButtonNav = MutableStateFlow(prefs.getBoolean(KEY_BUTTON_NAV, true))
    val useButtonNav = _useButtonNav.asStateFlow()

    /** Off by default: the ends of the carousel give a useful sense of place. */
    private val _loopCarousel = MutableStateFlow(prefs.getBoolean(KEY_LOOP_CAROUSEL, false))
    val loopCarousel = _loopCarousel.asStateFlow()

    /** Off by default: it costs the user money, so it is always their choice. */
    private val _cloudStt = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_STT, false))
    val cloudStt = _cloudStt.asStateFlow()

    private val _cloudSttAsked = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_STT_ASKED, false))
    val cloudSttAsked = _cloudSttAsked.asStateFlow()

    /** Off by default: testing found the spoken direction cues easier to act on. */
    private val _pitchFeedback = MutableStateFlow(prefs.getBoolean(KEY_PITCH_FEEDBACK, false))
    val pitchFeedback = _pitchFeedback.asStateFlow()

    // OFF (default) = tap to start / tap to stop recording; ON = hold while speaking.
    private val _holdToSpeak = MutableStateFlow(prefs.getBoolean(KEY_HOLD_TO_SPEAK, false))
    val holdToSpeak = _holdToSpeak.asStateFlow()

    // ON (default) = Ask with no capture snaps a picture automatically on release.
    // OFF = the user must press Capture first, then Ask for follow-ups.
    private val _autoCaptureEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CAPTURE, true))
    val autoCaptureEnabled = _autoCaptureEnabled.asStateFlow()

    // Anonymous crash diagnostics (Firebase Crashlytics). On by default; users
    // can opt out in Settings → Privacy.
    private val _crashReportingEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_CRASH_REPORTING, true))
    val crashReportingEnabled = _crashReportingEnabled.asStateFlow()

    fun setCrashReporting(enabled: Boolean) {
        _crashReportingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CRASH_REPORTING, enabled).apply()
    }

    /** OpenAI model used for Image/Text chat (BYOK — the user pays, so they choose). */
    private val _llmModel = MutableStateFlow(
        prefs.getString(KEY_LLM_MODEL, MODEL_BALANCED)
            ?.takeIf { id -> LLM_MODEL_OPTIONS.any { it.id == id } }
            ?: MODEL_BALANCED,
    )
    val llmModel = _llmModel.asStateFlow()

    fun setLlmModel(id: String) {
        if (LLM_MODEL_OPTIONS.none { it.id == id }) return
        _llmModel.value = id
        prefs.edit().putString(KEY_LLM_MODEL, id).apply()
    }

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

    /** "": undecided (ask each launch), STT_CHOICE_LATER: ask again, STT_CHOICE_NEVER: don't ask. */
    private val _sttDownloadChoice = MutableStateFlow(prefs.getString(KEY_STT_CHOICE, "") ?: "")
    val sttDownloadChoice = _sttDownloadChoice.asStateFlow()

    fun setSttDownloadChoice(choice: String) {
        _sttDownloadChoice.value = choice
        prefs.edit().putString(KEY_STT_CHOICE, choice).apply()
    }

    /** COCO labels hidden from the Find-objects picker (UI only). */
    private val _hiddenCocoObjects = MutableStateFlow(loadHiddenCocoObjects())
    val hiddenCocoObjects = _hiddenCocoObjects.asStateFlow()

    // --- Mutators ---

    fun setTextPreview(enabled: Boolean) {
        _textPreviewEnabled.value = enabled
        prefs.edit().putBoolean(KEY_TEXT_PREVIEW, enabled).apply()
    }

    /** Number of enabled always-available (local) carousel features. */
    fun localFeatureEnabledCount(): Int = listOf(
        _textChatEnabled, _discoverEnabled, _findEnabled, _scanLightEnabled, _scanColourEnabled,
    ).count { it.value }

    /**
     * Toggle a local feature. Disabling the last enabled one is refused so the
     * carousel is never empty. Returns true if applied, false if blocked.
     */
    private fun setLocalFeature(flow: MutableStateFlow<Boolean>, key: String, enabled: Boolean): Boolean {
        if (!enabled && flow.value && localFeatureEnabledCount() <= 1) return false
        flow.value = enabled
        prefs.edit().putBoolean(key, enabled).apply()
        return true
    }

    fun setTextChat(enabled: Boolean): Boolean = setLocalFeature(_textChatEnabled, KEY_TEXT_CHAT, enabled)
    fun setDiscover(enabled: Boolean): Boolean = setLocalFeature(_discoverEnabled, KEY_DISCOVER, enabled)
    fun setFind(enabled: Boolean): Boolean = setLocalFeature(_findEnabled, KEY_FIND, enabled)
    fun setScanLight(enabled: Boolean): Boolean = setLocalFeature(_scanLightEnabled, KEY_SCAN_LIGHT, enabled)
    fun setScanColour(enabled: Boolean): Boolean = setLocalFeature(_scanColourEnabled, KEY_SCAN_COLOUR, enabled)

    /** Image chat is key-gated, so it has no minimum guard. */
    fun setImageChat(enabled: Boolean) {
        _imageChatEnabled.value = enabled
        prefs.edit().putBoolean(KEY_IMAGE_CHAT, enabled).apply()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setLlmChat(enabled: Boolean) {
        // No-op: cloud chat no longer exists.
    }

    fun setHighContrast(enabled: Boolean) {
        _highContrastEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun setWhiteMode(enabled: Boolean) {
        _whiteModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_WHITE_MODE, enabled).apply()
    }

    fun setCloudStt(enabled: Boolean) {
        _cloudStt.value = enabled
        prefs.edit().putBoolean(KEY_CLOUD_STT, enabled).apply()
    }

    /** Remembers that the offer was made, so it is not pushed a second time. */
    fun markCloudSttAsked() {
        _cloudSttAsked.value = true
        prefs.edit().putBoolean(KEY_CLOUD_STT_ASKED, true).apply()
    }

    fun setPitchFeedback(enabled: Boolean) {
        _pitchFeedback.value = enabled
        prefs.edit().putBoolean(KEY_PITCH_FEEDBACK, enabled).apply()
    }

    fun setLoopCarousel(enabled: Boolean) {
        _loopCarousel.value = enabled
        prefs.edit().putBoolean(KEY_LOOP_CAROUSEL, enabled).apply()
    }

    fun setButtonNav(enabled: Boolean) {
        _useButtonNav.value = enabled
        prefs.edit().putBoolean(KEY_BUTTON_NAV, enabled).apply()
    }

    fun setHoldToSpeak(enabled: Boolean) {
        _holdToSpeak.value = enabled
        prefs.edit().putBoolean(KEY_HOLD_TO_SPEAK, enabled).apply()
    }

    fun setAutoCapture(enabled: Boolean) {
        _autoCaptureEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_CAPTURE, enabled).apply()
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

    /** Step one rate option up or down (clamped at the ends). Returns the new rate. */
    fun stepTtsSpeechRate(up: Boolean): Float {
        val idx = TTS_RATE_OPTIONS.indexOf(_ttsSpeechRate.value)
        val next = TTS_RATE_OPTIONS[(idx + if (up) 1 else -1).coerceIn(0, TTS_RATE_OPTIONS.size - 1)]
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
        private const val KEY_TEXT_CHAT = "text_chat_enabled"
        private const val KEY_DISCOVER = "discover_enabled"
        private const val KEY_FIND = "find_enabled"
        private const val KEY_IMAGE_CHAT = "image_chat_enabled"
        private const val KEY_LLM_CHAT = "llm_chat_enabled"
        private const val KEY_HIGH_CONTRAST = "high_contrast_enabled"
        private const val KEY_WHITE_MODE = "white_mode_enabled"
        private const val KEY_BUTTON_NAV = "button_nav_enabled"
        private const val KEY_HOLD_TO_SPEAK = "hold_to_speak_enabled"
        private const val KEY_AUTO_CAPTURE = "auto_capture_enabled"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_CRASH_REPORTING = "crash_reporting_enabled"

        const val MODEL_FAST = "gpt-4o-mini"
        const val MODEL_BALANCED = "gpt-4o"
        const val MODEL_SMART = "gpt-4.1"

        /** Selectable models, cheapest first. Only one is active at a time. */
        val LLM_MODEL_OPTIONS = listOf(
            LlmModelOption(MODEL_FAST, R.string.model_fast_label, R.string.model_fast_desc),
            LlmModelOption(MODEL_BALANCED, R.string.model_balanced_label, R.string.model_balanced_desc),
            LlmModelOption(MODEL_SMART, R.string.model_smart_label, R.string.model_smart_desc),
        )
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_LOOP_CAROUSEL = "loop_carousel"
        private const val KEY_PITCH_FEEDBACK = "pitch_feedback"
        private const val KEY_CLOUD_STT = "cloud_stt"
        private const val KEY_CLOUD_STT_ASKED = "cloud_stt_asked"
        private const val KEY_STT_CHOICE = "stt_download_choice"

        const val STT_CHOICE_LATER = "later"
        const val STT_CHOICE_NEVER = "never"

        val TTS_RATE_OPTIONS = listOf(1.0f, 1.5f, 2.0f, 3.0f)

        /** Localised speech-rate name, for anything the user reads or hears. */
        fun ttsRateLabel(context: Context, rate: Float): String = when (rate) {
            1.0f -> context.getString(R.string.rate_normal)
            1.5f -> context.getString(R.string.rate_fast)
            2.0f -> context.getString(R.string.rate_very_fast)
            3.0f -> context.getString(R.string.rate_ultra_fast)
            else -> "${rate}x"
        }

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
