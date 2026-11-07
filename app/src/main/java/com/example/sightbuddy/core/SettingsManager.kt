package com.example.sightbuddy.core

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
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

    init {
        // Retired settings: an older version may have stored these. Unread
        // keys are harmless, but they are cleared so they do not linger.
        val retired = listOf(
            KEY_BUTTON_NAV_RETIRED, KEY_FEATURE_ANNOUNCEMENTS_RETIRED, KEY_USE_LOCAL_STT_RETIRED,
            KEY_HAPTIC_FEEDBACK_RETIRED, KEY_TEXT_SCAN_GUIDANCE_RETIRED, KEY_HOLD_TO_SPEAK_RETIRED,
        )
        if (retired.any { prefs.contains(it) }) {
            prefs.edit().apply { retired.forEach { remove(it) } }.apply()
        }
    }

    // --- Feature settings ---
    private val _textPreviewEnabled = MutableStateFlow(prefs.getBoolean(KEY_TEXT_PREVIEW, true))
    val textPreviewEnabled = _textPreviewEnabled.asStateFlow()

    // Per-feature carousel visibility. The guarded setters keep at least one
    // feature in the carousel: the five always-available (local) ones, and Image
    // chat while it can be shown (see setImageChatAvailable).
    private val _textChatEnabled = MutableStateFlow(prefs.getBoolean(KEY_TEXT_CHAT, true))
    val textChatEnabled = _textChatEnabled.asStateFlow()

    private val _discoverEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCOVER, true))
    val discoverEnabled = _discoverEnabled.asStateFlow()

    private val _findEnabled = MutableStateFlow(prefs.getBoolean(KEY_FIND, true))
    val findEnabled = _findEnabled.asStateFlow()

    // Scan Light and Scan Colour are shown by default, as they always were:
    // hiding them would take them away from existing users who never touched
    // the switch (no stored value means the default applies).
    private val _scanLightEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_LIGHT, true))
    val scanLightEnabled = _scanLightEnabled.asStateFlow()

    private val _scanColourEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_COLOUR, true))
    val scanColourEnabled = _scanColourEnabled.asStateFlow()

    // Image chat visibility (only shown when there is an AI to answer it).
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


    /** On by default: past the last feature, carry on to the first. */
    private val _loopCarousel = MutableStateFlow(prefs.getBoolean(KEY_LOOP_CAROUSEL, true))
    val loopCarousel = _loopCarousel.asStateFlow()

    /** Off by default: it costs the user money, so it is always their choice. */
    private val _cloudStt = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_STT, false))
    val cloudStt = _cloudStt.asStateFlow()

    /**
     * The user's "Use API" switch. Turned on whenever a key is saved. While it is
     * off nothing is sent to OpenAI, whatever key is stored (see OpenAiTransport).
     */
    private val _useApi = MutableStateFlow(prefs.getBoolean(KEY_USE_API, true))
    val useApi = _useApi.asStateFlow()

    /** Speech recognition: an SttChoice key. Whisper by default; see SttChoice.effective. */
    private val _sttChoice = MutableStateFlow(prefs.getString(KEY_STT_ENGINE, null) ?: "whisper")
    val sttChoice = _sttChoice.asStateFlow()

    /** On by default: Gemma answers when the API is off or offline. */
    private val _useLocalChat = MutableStateFlow(prefs.getBoolean(KEY_USE_LOCAL_CHAT, true))
    val useLocalChat = _useLocalChat.asStateFlow()

    private val _cloudSttAsked = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_STT_ASKED, false))
    val cloudSttAsked = _cloudSttAsked.asStateFlow()

    /** Off by default: testing found the spoken direction cues easier to act on. */
    private val _pitchFeedback = MutableStateFlow(prefs.getBoolean(KEY_PITCH_FEEDBACK, false))
    val pitchFeedback = _pitchFeedback.asStateFlow()


    /**
     * Off by default. On, Text chat shows the text in view over the camera, and
     * reads it out, until the chat starts. Off leaves the camera clear, TalkBack
     * with nothing to meet there, and the chat's opening message in its place.
     */
    private val _liveText = MutableStateFlow(prefs.getBoolean(KEY_LIVE_TEXT, false))
    val liveText = _liveText.asStateFlow()

    fun setLiveText(enabled: Boolean) {
        _liveText.value = enabled
        prefs.edit().putBoolean(KEY_LIVE_TEXT, enabled).apply()
    }

    /**
     * On by default: the camera shows behind every feature, the chat faint over
     * it. The Preview button on screen is the same switch.
     */
    private val _cameraPreview = MutableStateFlow(prefs.getBoolean(KEY_CAMERA_PREVIEW, true))
    val cameraPreview = _cameraPreview.asStateFlow()

    fun setCameraPreview(enabled: Boolean) {
        _cameraPreview.value = enabled
        prefs.edit().putBoolean(KEY_CAMERA_PREVIEW, enabled).apply()
    }

    /**
     * On by default: the app's sounds other than speech (the recording cues, the
     * shutter, the waiting tick, Find objects' pitch note). See [FeedbackOutputs].
     */
    private val _soundEffects = MutableStateFlow(prefs.getBoolean(KEY_SOUND_EFFECTS, true))
    val soundEffects = _soundEffects.asStateFlow()

    /** On by default: the recording cues, the shutter and Find objects' pulse are felt as well. */
    private val _vibration = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibration = _vibration.asStateFlow()

    /** Off by default: features with Mute start with it on, to be unmuted when wanted. */
    private val _startFeaturesMuted = MutableStateFlow(prefs.getBoolean(KEY_START_FEATURES_MUTED, false))
    val startFeaturesMuted = _startFeaturesMuted.asStateFlow()


    // ON (default) = Ask with no capture snaps a picture automatically on release.
    // OFF = the user must press Capture first, then Ask for follow-ups.
    private val _autoCaptureEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CAPTURE, true))
    val autoCaptureEnabled = _autoCaptureEnabled.asStateFlow()

    // Text chat, Automatic capture on: Ask before any page finds the page as Find
    // page does (on, the default), or takes everything in view at once (off).
    private val _askFindsPage = MutableStateFlow(prefs.getBoolean(KEY_ASK_FINDS_PAGE, true))
    val askFindsPage = _askFindsPage.asStateFlow()

    /**
     * Off by default: volume up is Capture and volume down is Ask, where those
     * buttons are. The keys also set TalkBack's volume, so it is a choice.
     */
    private val _volumeKeys = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS, false))
    val volumeKeys = _volumeKeys.asStateFlow()

    /**
     * Off by default: Speak with screen reader. On, and with a screen reader
     * running, what the app says goes to the screen reader, in its voice and
     * speed, instead of the app's own voice (issue #15; see SpeechRoute).
     */
    private val _speakWithScreenReader = MutableStateFlow(prefs.getBoolean(KEY_SPEAK_WITH_SCREEN_READER, false))
    val speakWithScreenReader = _speakWithScreenReader.asStateFlow()

    fun setSpeakWithScreenReader(enabled: Boolean) {
        _speakWithScreenReader.value = enabled
        prefs.edit().putBoolean(KEY_SPEAK_WITH_SCREEN_READER, enabled).apply()
    }

    // Anonymous crash diagnostics (Firebase Crashlytics). On by default; users
    // can opt out in Settings → Privacy.
    private val _crashReportingEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_CRASH_REPORTING, true))
    val crashReportingEnabled = _crashReportingEnabled.asStateFlow()

    fun setCrashReporting(enabled: Boolean) {
        _crashReportingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CRASH_REPORTING, enabled).apply()
    }

    /**
     * Off by default: the user's choice to let a feedback email carry the app's
     * version, the phone and the main settings ([FeedbackEmail]). Off, the email
     * carries nothing about either.
     */
    private val _feedbackDetails = MutableStateFlow(prefs.getBoolean(KEY_FEEDBACK_DETAILS, false))
    val feedbackDetails = _feedbackDetails.asStateFlow()

    fun setFeedbackDetails(enabled: Boolean) {
        _feedbackDetails.value = enabled
        prefs.edit().putBoolean(KEY_FEEDBACK_DETAILS, enabled).apply()
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

    /**
     * Whether Image chat can be in the carousel: there is an AI to answer it (a
     * key with Use API on, or the on-device model). The app keeps it current;
     * the guard counts Image chat only while it is true.
     */
    private var imageChatAvailable = false

    /**
     * Toggle a local feature. Disabling the last feature shown is refused so the
     * carousel is never empty. Returns true if applied, false if blocked.
     */
    private fun setLocalFeature(flow: MutableStateFlow<Boolean>, key: String, enabled: Boolean): Boolean {
        if (!enabled && flow.value && shownFeatures(imageChatAvailable).size <= 1) return false
        flow.value = enabled
        prefs.edit().putBoolean(key, enabled).apply()
        return true
    }

    fun setTextChat(enabled: Boolean): Boolean = setLocalFeature(_textChatEnabled, KEY_TEXT_CHAT, enabled)
    fun setDiscover(enabled: Boolean): Boolean = setLocalFeature(_discoverEnabled, KEY_DISCOVER, enabled)
    fun setFind(enabled: Boolean): Boolean = setLocalFeature(_findEnabled, KEY_FIND, enabled)
    fun setScanLight(enabled: Boolean): Boolean = setLocalFeature(_scanLightEnabled, KEY_SCAN_LIGHT, enabled)
    fun setScanColour(enabled: Boolean): Boolean = setLocalFeature(_scanColourEnabled, KEY_SCAN_COLOUR, enabled)

    /**
     * Guarded like the local features while Image chat is shown: switching it
     * off as the last feature is refused. Returns true if applied.
     */
    fun setImageChat(enabled: Boolean): Boolean {
        if (!enabled && _imageChatEnabled.value && imageChatAvailable &&
            shownFeatures(imageChatAvailable).size <= 1
        ) return false
        _imageChatEnabled.value = enabled
        prefs.edit().putBoolean(KEY_IMAGE_CHAT, enabled).apply()
        return true
    }

    /**
     * Image chat comes and goes with its AI: a key saved or removed, Use API,
     * the on-device model and its switch. If it goes while it is the only
     * feature on, the carousel would be empty, so Text chat comes back on.
     */
    fun setImageChatAvailable(available: Boolean) {
        imageChatAvailable = available
        if (shownFeatures(available).isEmpty()) {
            _textChatEnabled.value = true
            prefs.edit().putBoolean(KEY_TEXT_CHAT, true).apply()
        }
    }

    /**
     * The features the carousel shows, in its order. The one definition: the
     * guard counts from it and the app builds its pages from it.
     */
    fun shownFeatures(imageChatAvailable: Boolean): List<String> = buildList {
        if (imageChatAvailable && _imageChatEnabled.value) add(ModeNames.IMAGE_CHAT)
        if (_textChatEnabled.value) add(ModeNames.TEXT_CHAT)
        if (_discoverEnabled.value) add(ModeNames.DISCOVER_OBJECTS)
        if (_findEnabled.value) add(ModeNames.FIND_OBJECTS)
        if (_scanLightEnabled.value) add(ModeNames.SCAN_LIGHT)
        if (_scanColourEnabled.value) add(ModeNames.SCAN_COLOUR)
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

    fun setSttChoice(key: String) {
        _sttChoice.value = key
        prefs.edit().putString(KEY_STT_ENGINE, key).apply()
    }

    fun setUseLocalChat(enabled: Boolean) {
        _useLocalChat.value = enabled
        prefs.edit().putBoolean(KEY_USE_LOCAL_CHAT, enabled).apply()
    }

    fun setUseApi(enabled: Boolean) {
        _useApi.value = enabled
        prefs.edit().putBoolean(KEY_USE_API, enabled).apply()
    }

    /** Remembers that the offer was made, so it is not pushed a second time. */
    fun markCloudSttAsked() {
        _cloudSttAsked.value = true
        prefs.edit().putBoolean(KEY_CLOUD_STT_ASKED, true).apply()
    }

    /** The one-time hint about a missing voice (issue #26) has been shown. */
    fun missingVoiceHintShown(): Boolean = prefs.getBoolean(KEY_MISSING_VOICE_HINT_SHOWN, false)

    fun markMissingVoiceHintShown() {
        prefs.edit { putBoolean(KEY_MISSING_VOICE_HINT_SHOWN, true) }
    }

    fun setSoundEffects(enabled: Boolean) {
        _soundEffects.value = enabled
        prefs.edit { putBoolean(KEY_SOUND_EFFECTS, enabled) }
    }

    fun setVibration(enabled: Boolean) {
        _vibration.value = enabled
        prefs.edit { putBoolean(KEY_VIBRATION, enabled) }
    }

    fun setStartFeaturesMuted(enabled: Boolean) {
        _startFeaturesMuted.value = enabled
        prefs.edit().putBoolean(KEY_START_FEATURES_MUTED, enabled).apply()
    }

    fun setPitchFeedback(enabled: Boolean) {
        _pitchFeedback.value = enabled
        prefs.edit().putBoolean(KEY_PITCH_FEEDBACK, enabled).apply()
    }

    fun setLoopCarousel(enabled: Boolean) {
        _loopCarousel.value = enabled
        prefs.edit().putBoolean(KEY_LOOP_CAROUSEL, enabled).apply()
    }



    fun setAutoCapture(enabled: Boolean) {
        _autoCaptureEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_CAPTURE, enabled).apply()
    }

    fun setAskFindsPage(enabled: Boolean) {
        _askFindsPage.value = enabled
        prefs.edit().putBoolean(KEY_ASK_FINDS_PAGE, enabled).apply()
    }

    fun setVolumeKeys(enabled: Boolean) {
        _volumeKeys.value = enabled
        prefs.edit().putBoolean(KEY_VOLUME_KEYS, enabled).apply()
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
        // No longer read: the feature bar is always on. Older installs may still
        // hold it; it is removed at start-up, and nothing depends on it either way.
        private const val KEY_BUTTON_NAV_RETIRED = "button_nav_enabled"
        private const val KEY_FEATURE_ANNOUNCEMENTS_RETIRED = "feature_activation_announcements_enabled"
        // Hold to speak, withdrawn in 2.3 and removed: Ask is tap to start, tap to send.
        private const val KEY_HOLD_TO_SPEAK_RETIRED = "hold_to_speak_enabled"
        private const val KEY_AUTO_CAPTURE = "auto_capture_enabled"
        private const val KEY_ASK_FINDS_PAGE = "ask_finds_page"
        private const val KEY_VOLUME_KEYS = "volume_keys"
        private const val KEY_SPEAK_WITH_SCREEN_READER = "speak_with_screen_reader"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_CRASH_REPORTING = "crash_reporting_enabled"
        private const val KEY_FEEDBACK_DETAILS = "feedback_details"

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
        // 2.3.0's Haptic feedback, vibration instead of sound; replaced by the
        // two below, each on its own. Removed at start-up.
        private const val KEY_HAPTIC_FEEDBACK_RETIRED = "haptic_feedback"
        private const val KEY_SOUND_EFFECTS = "sound_effects"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_CAMERA_PREVIEW = "camera_preview"
        private const val KEY_START_FEATURES_MUTED = "start_features_muted"
        // Text scanning guidance: Find page is its own button now (2026-10-30).
        private const val KEY_TEXT_SCAN_GUIDANCE_RETIRED = "text_scan_guidance"
        private const val KEY_LIVE_TEXT = "live_text"
        private const val KEY_CLOUD_STT = "cloud_stt"
        private const val KEY_USE_API = "use_api"
        private const val KEY_STT_ENGINE = "stt_engine"
        // A switch that lived only in test builds; replaced by KEY_STT_ENGINE.
        private const val KEY_USE_LOCAL_STT_RETIRED = "use_local_stt"
        private const val KEY_USE_LOCAL_CHAT = "use_local_chat"
        private const val KEY_CLOUD_STT_ASKED = "cloud_stt_asked"
        private const val KEY_MISSING_VOICE_HINT_SHOWN = "missing_voice_hint_shown"
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
        private const val KEY_COCO_HIDDEN = "coco_hidden_objects"
    }
}
