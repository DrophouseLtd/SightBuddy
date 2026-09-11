package com.example.sightbuddy.core.stt

/** Which recogniser handles a recording. */
enum class SpeechEngine {
    /** On-device Whisper: ours end to end, silent until we cue it. */
    WHISPER,

    /** OpenAI over the network: ours end to end too, and in any language. */
    CLOUD,

    /** The platform recogniser: owns the mic, its own earcons, and its own timing. */
    PLATFORM,
}

/**
 * The two facts that decide everything about speech input, in one place.
 *
 * These used to be asked separately at each call site, in slightly different
 * ways, and drifted apart: the download was hidden in one place and offered in
 * another, and the app cued the mic on a path where the platform was already
 * doing it. Both questions are now answered here.
 *
 * [modelServesLanguage] is a policy question, fixed for a given app language and
 * bundled model. [modelDownloaded] is a fact about the device that changes when
 * the user downloads. Neither says what is loaded *right now*: for that, ask
 * [VoiceInputService.usesSystemEarcons], because a model can be present on disk
 * and still have failed to load.
 */
class SpeechAvailability(
    val modelServesLanguage: Boolean,
    val modelDownloaded: Boolean,
    val cloudEnabled: Boolean = false,
) {
    /** Which recogniser this device will use, assuming the model loads. */
    val engine: SpeechEngine = when {
        // Chosen deliberately and paid for, so it outranks the free options.
        cloudEnabled -> SpeechEngine.CLOUD
        modelServesLanguage && modelDownloaded -> SpeechEngine.WHISPER
        else -> SpeechEngine.PLATFORM
    }

    /**
     * Whether the app decides when a recording starts and ends.
     *
     * The platform recogniser does not let it: it stops when it judges the user
     * has finished. Anything that depends on a recording lasting as long as the
     * user wants, or on pairing a transcript with a frame, needs this to be true.
     */
    val appOwnsRecording: Boolean = engine != SpeechEngine.PLATFORM

    /**
     * Whether the download may be offered, or even named, anywhere in the UI.
     *
     * False means the model cannot transcribe this language, so mentioning it
     * would offer a large download that changes nothing.
     */
    val downloadRelevant: Boolean = modelServesLanguage

    /** True while the user could still choose to download it. */
    val downloadPending: Boolean = modelServesLanguage && !modelDownloaded
}
