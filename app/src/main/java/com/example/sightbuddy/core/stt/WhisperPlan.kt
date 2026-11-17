package com.example.sightbuddy.core.stt

import java.util.Locale

/**
 * Which Whisper model runs and which language it is told to expect.
 *
 * [language] is a Whisper language code: `"en"`, `"pl"`, `"fi"` … for a language
 * the model lists, and the empty string for "detect it". That is what
 * sherpa-onnx and Whisper itself mean by an empty language, and it is the
 * honest answer for a language the app cannot name: the model decides, rather
 * than being told English and mishearing everything.
 */
data class WhisperPlan(val set: SttModelSet, val language: String) {

    /** Only the multilingual model speaks anything but English. */
    val multilingual: Boolean get() = set == SttModelSet.MULTILINGUAL

    /**
     * True when [loaded] (what the recogniser was built with) cannot serve this
     * plan: sherpa-onnx takes the language when the recogniser is built, so a
     * change of language needs the model reopened, not retold.
     */
    fun requiresReload(loaded: WhisperPlan?): Boolean = loaded != this

    companion object {
        /**
         * The languages this build offers for speech recognition, as Whisper
         * spells them. Polish, Finnish and English are the app's own languages;
         * the rest let the multilingual model serve the other phones that run
         * the app in their own language without a per-language build.
         */
        private val WHISPER_LANGUAGES = setOf(
            "af", "am", "ar", "as", "az", "ba", "be", "bg", "bn", "bo", "br", "bs", "ca", "cs", "cy",
            "da", "de", "el", "en", "es", "et", "eu", "fa", "fi", "fo", "fr", "gl", "gu", "ha", "haw",
            "he", "hi", "hr", "ht", "hu", "hy", "id", "is", "it", "ja", "jw", "ka", "kk", "km", "kn",
            "ko", "la", "lb", "ln", "lo", "lt", "lv", "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt",
            "my", "ne", "nl", "nn", "no", "oc", "pa", "pl", "ps", "pt", "ro", "ru", "sa", "sd", "si",
            "sk", "sl", "sn", "so", "sq", "sr", "su", "sv", "sw", "ta", "te", "tg", "th", "tk", "tl",
            "tr", "tt", "uk", "ur", "uz", "vi", "yi", "yo", "zh",
        )

        /** The language code for [languageTag], or "" when Whisper should detect it. */
        fun languageFor(languageTag: String): String {
            val primary = languageTag.replace('_', '-')
                .substringBefore('-')
                .lowercase(Locale.ROOT)
            return if (primary in WHISPER_LANGUAGES) primary else ""
        }

        /**
         * The model to run and its language, or null when the platform
         * recogniser must take the recording.
         *
         * The multilingual model wins whenever it is ready: it serves every
         * language, English included, and is the one the app now downloads. The
         * legacy model is used only when it is what the phone has, and only for
         * English — its base.en weights cannot transcribe anything else, and
         * telling it otherwise would produce confident nonsense.
         */
        fun choose(ready: (SttModelSet) -> Boolean, appLanguageTag: String): WhisperPlan? {
            val language = languageFor(appLanguageTag)
            if (ready(SttModelSet.MULTILINGUAL)) return WhisperPlan(SttModelSet.MULTILINGUAL, language)
            if (ready(SttModelSet.LEGACY_EN) && language == "en") {
                return WhisperPlan(SttModelSet.LEGACY_EN, "en")
            }
            return null
        }
    }
}
