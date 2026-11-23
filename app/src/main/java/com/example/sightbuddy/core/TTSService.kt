package com.example.sightbuddy.core

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.sightbuddy.R
import com.example.sightbuddy.core.language.LanguageIdentifier
import com.example.sightbuddy.core.language.LanguageSpan
import com.example.sightbuddy.core.language.LanguageTagger
import com.example.sightbuddy.core.language.SpeechLanguageOrder
import com.example.sightbuddy.core.language.SpokenParts
import com.example.sightbuddy.core.language.VoiceChoice
import com.example.sightbuddy.core.language.VoiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The one place the app speaks. Each line goes to the app's own voice, or,
 * when the user chose Speak with screen reader and one is running, to the
 * screen reader (issue #15): see [SpeechRoute] for which, and
 * [ScreenReaderSpeech] for how.
 *
 * Text in another language than the app's (issue #26) is said with that
 * language's voice where the phone has one: the language is found per
 * paragraph ([LanguageTagger], with the language identifier), the app's
 * voice switches for those parts and back after, and the screen reader's
 * lines carry the language for TalkBack to switch too. A line that must wait
 * for its language holds the lines after it ([SpeechLanguageOrder]).
 *
 * @param screenReaderChosen the user's Speak with screen reader setting.
 * @param screenReaderRunning a screen reader is on now.
 * @param appInFront the app's window is in front and has the focus: not in the
 *   background, and no pop-up window over it. Only then does the screen reader
 *   hear the app's live regions.
 * @param languageIdentifier finds a text's language; null keeps every line
 *   in the app's language.
 */
class TTSService(
    context: Context,
    private val screenReaderChosen: () -> Boolean = { false },
    private val screenReaderRunning: () -> Boolean = { false },
    private val appInFront: () -> Boolean = { true },
    private val languageIdentifier: LanguageIdentifier? = null,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Said in place of a text Android's voice would refuse ([SpeechRoute.forVoice]). */
    private val tooLongNotice = context.getString(R.string.spoken_text_too_long)

    /** The lines on their way to the screen reader; the screen shows them to TalkBack. */
    private val screenReader = ScreenReaderSpeech(
        now = SystemClock::uptimeMillis,
        schedule = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
    )

    /** The language the app's voice speaks: the app's, or English when the phone has no voice for it. */
    @Volatile
    private var appVoiceLocale: Locale = Locale.getDefault()

    private val tagger = languageIdentifier?.let { LanguageTagger(it) { appVoiceLocale.language } }

    /** Lines waiting for their language, and the lines after them (issue #26). */
    private val languageOrder = SpeechLanguageOrder(
        schedule = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
        onGaveUp = {
            Log.w(
                LANGUAGE_LOG_TAG,
                "A line waited ${SpeechLanguageOrder.WAIT_MS} ms for its language and was said in the app's language",
            )
        },
    )

    /** The utterance ids of a text said in parts, one per voice. */
    private val spokenParts = SpokenParts()

    /**
     * Told the languages a line was in that the phone has no voice for, as
     * it is said (said in the app's language instead): for the one-time hint.
     */
    @Volatile
    var onVoiceMissing: ((Set<String>) -> Unit)? = null

    /** The phone's voices, asked for at most every [VOICES_KEPT_MS]. */
    private var voices: List<VoiceInfo>? = null
    private var voicesAtMs = Long.MIN_VALUE / 2
    private val engineTakes = HashMap<String, Boolean>()

    /** For the screen's two live-region nodes (ScreenReaderLines). */
    val screenReaderLines: StateFlow<List<ScreenReaderSpeech.Line?>> = screenReader.lines
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    /** When true, [speak] and cooldown helpers no-op (used during STT so mic audio is not masked). */
    private val _suppressAppSpeech = MutableStateFlow(false)
    val suppressAppSpeech = _suppressAppSpeech.asStateFlow()

    /**
     * The last line spoken, for the scanning features to show on screen. Set
     * while muted too, so muting silences the voice, not the text, but never
     * while app speech is suppressed: nothing is said then either.
     */
    private val _lastSpoken = MutableStateFlow("")
    val lastSpoken = _lastSpoken.asStateFlow()

    fun clearLastSpoken() {
        _lastSpoken.value = ""
    }

    /** The user's Mute: feature output is silent; forced speech still plays. */
    @Volatile
    var muted: Boolean = false

    private var utteranceDoneListener: ((String?) -> Unit)? = null
    private var utteranceRangeListener: ((String?, Int, Int) -> Unit)? = null

    /** One-shot per-utterance completion callbacks (e.g. resume reading after a rate announcement). */
    private val oneShotDone = ConcurrentHashMap<String, () -> Unit>()

    fun setOnUtteranceDone(listener: ((String?) -> Unit)?) {
        utteranceDoneListener = listener
    }

    /** [start]/[end] are character offsets within the spoken utterance text. */
    fun setOnUtteranceRange(listener: ((String?, Int, Int) -> Unit)?) {
        utteranceRangeListener = listener
    }

    fun setSuppressAppSpeech(suppress: Boolean) {
        _suppressAppSpeech.value = suppress
    }

    private val announcementCooldowns = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 3000L

    /** Applied on init and whenever the user changes the Settings speech-rate. */
    @Volatile
    private var speechRate: Float = 1.0f

    /** Set while a recording owns the output; see [takeMicrophoneFloor]. */
    @Volatile
    private var micFloorUntilMs = 0L

    init {
        tts = TextToSpeech(context, this)
    }

    /**
     * 1.0 = normal. Safe at any time: stored and re-applied after async init.
     * Playback cursor logic is character-indexed (TTS range callbacks), so
     * changing the rate never desynchronises Text chat playback.
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate
        if (_isInitialized.value) {
            tts?.setSpeechRate(rate)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Follow the app's chosen language (LanguageStore.wrap sets the default
            // locale). If the device has no voice for it, fall back to English so
            // the app still speaks rather than going silent.
            val preferred = Locale.getDefault()
            var result = tts?.setLanguage(preferred)
            appVoiceLocale = preferred
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TTSService", "No voice for $preferred; falling back to English")
                result = tts?.setLanguage(Locale.US)
                appVoiceLocale = Locale.US
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSService", "The Language specified is not supported!")
            } else {
                _isInitialized.value = true
                // The language model is loaded now, on a thread of its own, so
                // the first line in another language does not wait for it.
                languageIdentifier?.let { identifier ->
                    thread(name = "language-id-warm-up", isDaemon = true) {
                        try {
                            identifier.warmUp()
                        } catch (e: RuntimeException) {
                            Log.w(LANGUAGE_LOG_TAG, "Could not warm up the language model", e)
                        }
                    }
                }
                tts?.setSpeechRate(speechRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onRangeStart(
                        utteranceId: String?,
                        start: Int,
                        end: Int,
                        frame: Int,
                    ) {
                        if (utteranceId == null) {
                            utteranceRangeListener?.invoke(null, start, end)
                            return
                        }
                        // A part of a text said in parts: at its place in the whole text.
                        val (whole, at) = spokenParts.inWhole(utteranceId, start)
                        utteranceRangeListener?.invoke(whole, at, at + (end - start))
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { spokenParts.ended(it) }
                        // A part before a text's last: the text is not done yet.
                        if (utteranceId != null && spokenParts.isInnerPart(utteranceId)) return
                        utteranceId?.let { oneShotDone.remove(it)?.invoke() }
                        utteranceDoneListener?.invoke(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        // Flushed/stopped utterances never complete; drop their callback.
                        utteranceId?.let {
                            spokenParts.ended(it)
                            oneShotDone.remove(it)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { spokenParts.ended(it) }
                        if (utteranceId != null && spokenParts.isInnerPart(utteranceId)) return
                        utteranceId?.let { oneShotDone.remove(it)?.invoke() }
                        utteranceDoneListener?.invoke(utteranceId)
                    }
                })
            }
        } else {
            Log.e("TTSService", "Initialization Failed!")
        }
    }

    /**
     * The microphone outranks the voice.
     *
     * While a recording holds the floor, speech in flight is stopped and anything
     * newly requested is dropped rather than queued. Queueing was the bug: a line
     * asked for during a recording played once the recording ended, or worse
     * played into the open mic and came back transcribed as the user.
     *
     * [maxHoldMs] bounds the hold, so a release that never arrives cannot leave
     * the app permanently mute.
     */
    fun takeMicrophoneFloor(maxHoldMs: Long) {
        micFloorUntilMs = System.currentTimeMillis() + maxHoldMs
        tts?.stop()
        // Lines still waiting for their language are dropped with the rest.
        languageOrder.clear()
        // Lines for the screen reader not yet handed over are dropped too.
        screenReader.stop()
        // And what it is already saying is cut, while the app speaks through it:
        // a recording started without a touch (the volume keys) would otherwise
        // record a long answer. TalkBack holds back its own words while the
        // microphone records (unless headphones are in), so nothing else is lost.
        // Only here: Mute and the rest leave TalkBack alone.
        if (SpeechRoute.cutsScreenReaderForRecording(screenReaderChosen(), screenReaderRunning())) {
            interruptScreenReader()
        }
    }

    /** Asks the screen reader to stop speaking. Android refuses when accessibility is off. */
    private fun interruptScreenReader() {
        val manager = accessibilityManager ?: return
        if (!manager.isEnabled) return
        try {
            manager.interrupt()
        } catch (e: IllegalStateException) {
            // Accessibility went off between the check and the call.
            Log.w("TTSService", "Could not interrupt the screen reader", e)
        }
    }

    fun releaseMicrophoneFloor() {
        micFloorUntilMs = 0L
    }

    /** True while a recording owns the output. Cues are exempt; only speech is held. */
    fun micHasFloor(): Boolean = System.currentTimeMillis() < micFloorUntilMs

    /**
     * @return true if speech was queued (initialized and not suppressed).
     */
    /**
     * @param force speak even while app speech is suppressed. Use only for
     *   directly user-initiated feedback (e.g. Settings confirmations), never
     *   for background/feature output — that is what suppression exists to stop.
     * @param caption false for a line that is not the feature's own: it is said
     *   but never becomes [lastSpoken], which the scanning features show on
     *   screen. A download finishing is not what the camera is looking at.
     * @param cue a direction cue ([DirectionCues]): urgent when it goes to the
     *   screen reader, and kept on the app's voice if
     *   [CUES_THROUGH_SCREEN_READER] is false.
     * @param onDone runs once the line has been said. Through the screen reader,
     *   which never reports back, once it counts as said (an estimate).
     */
    fun speak(
        text: String,
        flush: Boolean = false,
        utteranceId: String = "TTS_ID_${System.currentTimeMillis()}",
        force: Boolean = false,
        caption: Boolean = true,
        cue: Boolean = false,
        onDone: (() -> Unit)? = null,
    ): Boolean {
        val decision = SpeechRoute.decide(
            text = text,
            recording = micHasFloor(),
            suppressed = _suppressAppSpeech.value,
            force = force,
            muted = muted,
            screenReaderChosen = screenReaderChosen(),
            screenReaderRunning = screenReaderRunning(),
            appInFront = appInFront(),
            cue = cue,
        )
        if (decision.shown && caption) _lastSpoken.value = text
        if (decision.output == SpeechRoute.Output.NONE) return false
        if (decision.output == SpeechRoute.Output.APP_VOICE && !_isInitialized.value) return false
        // A flush drops the lines still waiting for their language, too.
        if (flush) languageOrder.clear()
        // Only a line that may be in another language waits to be looked at
        // (issue #26); short app lines and cues go at once, as before.
        val find: ((onFound: (List<LanguageSpan>) -> Unit) -> Unit)? =
            tagger?.takeIf { it.needsAsking(text) }?.let { t -> { onFound -> t.spans(text, onFound) } }
        return languageOrder.add(find) { found ->
            say(decision.output, text, flush, utteranceId, cue, onDone, found)
        }
    }

    /**
     * Says a line, now that the stretches of it in another language are
     * known ([found]): those with a voice in their language, the rest in the
     * app's, reported to [onVoiceMissing].
     */
    private fun say(
        output: SpeechRoute.Output,
        text: String,
        flush: Boolean,
        utteranceId: String,
        cue: Boolean,
        onDone: (() -> Unit)?,
        found: List<LanguageSpan>,
    ): Boolean {
        // A recording took the floor while the line waited for its language:
        // as for any line asked for then, nothing is said (SpeechRoute.decide).
        if (micHasFloor()) return false
        val languages = if (found.isEmpty() || !_isInitialized.value) {
            emptyList()
        } else {
            val picked = VoiceChoice.pick(found, ::localeFor)
            if (picked.missing.isNotEmpty()) onVoiceMissing?.invoke(picked.missing)
            picked.spans
        }
        if (output == SpeechRoute.Output.SCREEN_READER) {
            // A flush cuts the app's voice too, should it still be speaking.
            if (flush) tts?.stop()
            screenReader.say(text, flush = flush, urgent = cue, languages = languages) {
                // As the voice reports a line done: its own callback, then the listener.
                onDone?.invoke()
                utteranceDoneListener?.invoke(utteranceId)
            }
            return true
        }
        // And the other way: lines not yet handed to the screen reader go.
        if (flush) screenReader.stop()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (onDone != null) oneShotDone[utteranceId] = onDone
        spokenParts.forget(utteranceId)
        val maxChars = TextToSpeech.getMaxSpeechInputLength()
        val parts = VoiceChoice.parts(text, languages)
        val result = if (text.length >= maxChars || parts.all { it.tag == null }) {
            // Too long for Android's voice (issue #23): said so, rather than nothing.
            val said = SpeechRoute.forVoice(text, maxChars, tooLongNotice)
            tts?.speak(said, queueMode, Bundle(), utteranceId)
        } else {
            sayInParts(text, parts, queueMode, utteranceId)
        }
        if (result == TextToSpeech.ERROR) oneShotDone.remove(utteranceId)
        return result != TextToSpeech.ERROR
    }

    /**
     * The app's voice says [text] a part at a time, each part in its own
     * language's voice, and is set back to the app's language after. The
     * voice goes with each utterance as it is queued (Android copies the
     * language into each request), so setting it back does not touch the
     * parts already queued.
     */
    private fun sayInParts(text: String, parts: List<VoiceChoice.Part>, queueMode: Int, utteranceId: String): Int {
        var result = TextToSpeech.SUCCESS
        parts.forEachIndexed { index, part ->
            val id = spokenParts.idOf(utteranceId, index, last = index == parts.lastIndex)
            spokenParts.started(id, part.start)
            val set = tts?.setLanguage(part.tag?.let(Locale::forLanguageTag) ?: appVoiceLocale)
            if (set == TextToSpeech.LANG_MISSING_DATA || set == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(appVoiceLocale)
            }
            val mode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
            if (tts?.speak(text.substring(part.start, part.end), mode, Bundle(), id) != TextToSpeech.SUCCESS) {
                result = TextToSpeech.ERROR
            }
        }
        tts?.setLanguage(appVoiceLocale)
        return result
    }

    /**
     * The locale to speak [tag]'s language with, or null when the phone has
     * no voice for it ([VoiceChoice.localeFor]). The voices are asked for at
     * most every [VOICES_KEPT_MS], so one added in the phone's settings is
     * found soon after.
     */
    private fun localeFor(tag: String): String? {
        val engine = tts ?: return null
        val list = synchronized(engineTakes) {
            val now = SystemClock.uptimeMillis()
            if (now - voicesAtMs > VOICES_KEPT_MS) {
                voicesAtMs = now
                engineTakes.clear()
                voices = try {
                    engine.voices?.takeIf { it.isNotEmpty() }?.map { voice ->
                        VoiceInfo(
                            localeTag = voice.locale.toLanguageTag(),
                            installed = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true,
                            needsNetwork = voice.isNetworkConnectionRequired,
                        )
                    }
                } catch (e: RuntimeException) {
                    Log.w("TTSService", "Could not list the voices", e)
                    null
                }
            }
            voices
        }
        return VoiceChoice.localeFor(tag, list) { candidate ->
            synchronized(engineTakes) {
                engineTakes.getOrPut(candidate) {
                    engine.isLanguageAvailable(Locale.forLanguageTag(candidate)) >= TextToSpeech.LANG_AVAILABLE
                }
            }
        }
    }

    /**
     * The stretches of [text] in another language the phone has a voice for,
     * for TalkBack to read the text with it (issue #26): to [onResult], once.
     * Nothing when the app does not look for languages.
     */
    fun languagesOf(text: String, onResult: (List<LanguageSpan>) -> Unit) {
        val t = tagger
        if (t == null || !_isInitialized.value) {
            onResult(emptyList())
            return
        }
        t.spans(text) { found ->
            onResult(if (found.isEmpty()) emptyList() else VoiceChoice.pick(found, ::localeFor).spans)
        }
    }

    fun speakWithCooldown(objectName: String) {
        if (micHasFloor() || _suppressAppSpeech.value) return
        val currentTime = System.currentTimeMillis()
        val lastAnnouncedTime = announcementCooldowns[objectName] ?: 0L

        if (currentTime - lastAnnouncedTime > COOLDOWN_MS) {
            speak(objectName, flush = false)
            announcementCooldowns[objectName] = currentTime
        }
    }

    fun speakLatestWithCooldown(key: String, message: String) {
        if (micHasFloor() || _suppressAppSpeech.value) return
        val currentTime = System.currentTimeMillis()
        val lastAnnouncedTime = announcementCooldowns[key] ?: 0L

        if (currentTime - lastAnnouncedTime > COOLDOWN_MS) {
            speak(message, flush = true)
            announcementCooldowns[key] = currentTime
        }
    }

    /**
     * True while anything is being spoken: by the app's voice, or by the
     * screen reader until its last line counts as said.
     */
    fun isSpeaking(): Boolean =
        tts?.isSpeaking == true || screenReader.isSpeaking() || languageOrder.waiting()

    /** Stops all in-flight and queued speech; for the screen reader, all it has not been handed yet. */
    fun stop(@Suppress("UNUSED_PARAMETER") utteranceId: String? = null) {
        tts?.stop()
        screenReader.stop()
        languageOrder.clear()
    }

    fun shutdown() {
        tts?.stop()
        screenReader.stop()
        languageOrder.clear()
        mainHandler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        (languageIdentifier as? Closeable)?.close()
    }

    private companion object {
        /** How long the list of the phone's voices is kept before it is asked for again. */
        const val VOICES_KEPT_MS = 30_000L

        /** Log tag for finding a line's language (issue #26). */
        const val LANGUAGE_LOG_TAG = "SPEECH-LANG"
    }
}
