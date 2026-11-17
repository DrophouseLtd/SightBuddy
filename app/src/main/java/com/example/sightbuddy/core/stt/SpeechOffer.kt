package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.LanguageStore

/**
 * What a language may be offered, now that the multilingual Whisper model
 * serves Polish and Finnish offline as well as English.
 *
 * Only two things are still English-only: Gemma's speech path (hidden anyway)
 * and the legacy base.en model. Whisper's multilingual model is the one the app
 * downloads, in every language, and its size is the files' real size.
 */
class SpeechOffer private constructor(
    val languageTag: String,
    /** The model this language would be served by. */
    val model: SttModelSet,
    /** Whether the offline Whisper model may be named and offered here. */
    val whisperOffered: Boolean,
    /** Whether on-device Gemma may be recommended for speech here (English only). */
    val gemmaOffered: Boolean,
    /** The size the download offer quotes: the files as published, in bytes. */
    val downloadBytes: Long,
) {
    companion object {
        fun forLanguage(
            languageTag: String,
            installed: Set<SttModelSet>,
            spec: SttModelSpec = SttModelCatalog.of(SttModelSet.MULTILINGUAL),
        ): SpeechOffer {
            val language = WhisperPlan.languageFor(languageTag)
            // The multilingual model serves English, Polish, Finnish and the rest
            // of Whisper's list; for those it is offered wherever it is missing.
            val whisper = SttModelSet.MULTILINGUAL !in installed
            val gemma = language == "en"
            return SpeechOffer(
                languageTag = languageTag,
                model = SttModelSet.MULTILINGUAL,
                whisperOffered = whisper,
                gemmaOffered = gemma,
                downloadBytes = spec.totalBytes,
            )
        }

        /** The offer for the app's own language. */
        fun forAppLanguage(installed: Set<SttModelSet>, tag: String = LanguageStore.LANG_EN): SpeechOffer =
            forLanguage(tag, installed)
    }
}

/**
 * What a recommended setup actually downloads, spelled out so that Gemma is
 * only ever fetched with the user's explicit consent (its own dialog, or the
 * tier offer whose size they saw). Whisper never drags it along.
 */
class SetupDownloadPlan private constructor(
    val whisperSets: List<SttModelSet>,
    val gemma: Boolean,
) {
    companion object {
        fun forTier(tier: com.example.sightbuddy.core.ModelSetup.Tier, gemmaAccepted: Boolean): SetupDownloadPlan =
            when (tier) {
                // Nothing fits: nothing is downloaded, and Gemma certainly is not.
                com.example.sightbuddy.core.ModelSetup.Tier.NONE -> SetupDownloadPlan(emptyList(), gemma = false)
                com.example.sightbuddy.core.ModelSetup.Tier.WHISPER -> SetupDownloadPlan(listOf(SttModelSet.MULTILINGUAL), false)
                com.example.sightbuddy.core.ModelSetup.Tier.FULL -> SetupDownloadPlan(
                    listOf(SttModelSet.MULTILINGUAL),
                    gemma = gemmaAccepted,
                )
            }

        fun whisperOnly(): SetupDownloadPlan = SetupDownloadPlan(listOf(SttModelSet.MULTILINGUAL), false)
    }
}
