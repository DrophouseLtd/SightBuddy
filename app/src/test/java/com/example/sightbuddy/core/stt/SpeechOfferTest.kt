package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.ModelSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the setup offer and the download UI may say and do now that the
 * multilingual Whisper model serves Polish and Finnish as well as English.
 *
 * The rule the old code encoded — "no speech model outside English, so offer
 * nothing" — is now wrong for Whisper: Polish and Finnish have a model, and
 * only Gemma is English-only. The figures quoted must be the files' real sizes.
 */
class SpeechOfferTest {

    @Test
    fun everyAppLanguageHasAnOfflineWhisperModelToOffer() {
        for (tag in listOf("en", "pl", "fi")) {
            // The worst case today's users are in: only the old English model.
            val offer = SpeechOffer.forLanguage(tag, installed = setOf(SttModelSet.LEGACY_EN))
            assertTrue(tag, offer.whisperOffered)
            assertEquals(tag, SttModelSet.MULTILINGUAL, offer.model)
        }
    }

    @Test
    fun aPhoneWithTheLegacyModelIsOfferedTheMultilingualOne() {
        // English phone, base.en already installed: the new model still applies
        // (it is what serves other languages later, and it is the download now).
        val offer = SpeechOffer.forLanguage("en", installed = setOf(SttModelSet.LEGACY_EN))
        assertTrue(offer.whisperOffered)
        assertEquals(SttModelSet.MULTILINGUAL, offer.model)

        // And once the multilingual one is there, nothing is offered again.
        val done = SpeechOffer.forLanguage("en", installed = setOf(SttModelSet.MULTILINGUAL))
        assertFalse(done.whisperOffered)
    }

    @Test
    fun polishAndFinnishAreNotBlockedBecauseOnlyGemmaIsEnglishOnly() {
        for (tag in listOf("pl", "fi")) {
            val offer = SpeechOffer.forLanguage(tag, installed = setOf(SttModelSet.LEGACY_EN))
            assertTrue("$tag must still get Whisper", offer.whisperOffered)
            // But Gemma, which is English only, is not offered there.
            assertFalse(tag, offer.gemmaOffered)
        }
        assertTrue(SpeechOffer.forLanguage("en", installed = emptySet()).gemmaOffered)
    }

    @Test
    fun theQuotedSizeIsTheFilesRealSizeNotTheOldRoundNumber() {
        val modern = SpeechOffer.forLanguage("pl", installed = emptySet())
        assertEquals(SttModelCatalog.of(SttModelSet.MULTILINGUAL).totalBytes, modern.downloadBytes)
        // 29,120,534 + 130,672,026 + 816,730 + 643,854
        assertEquals(161_253_144L, modern.downloadBytes)
        // And it is the same figure the space check uses, so what is quoted is
        // what is checked, file for file.
        assertEquals(ModelSetup.WHISPER_BYTES, modern.downloadBytes)
    }

    @Test
    fun theRecommendedSetupInPolishIncludesWhisperAndTheRealSize() {
        // Tier is chosen from specs, which do not depend on language any more:
        // a phone that fits Whisper gets it offered in Polish too.
        val tier = ModelSetup.tier(totalRamBytes = 5_700_000_000L, freeBytes = 10_000_000_000L, english = false)
        assertEquals(ModelSetup.Tier.WHISPER, tier)
        assertEquals(161_253_144L, ModelSetup.packageBytes(tier))
        // Image/chat Gemma is multilingual; its speech-only gate is separate.
        assertEquals(
            ModelSetup.Tier.FULL,
            ModelSetup.tier(totalRamBytes = 8_000_000_000L, freeBytes = 10_000_000_000L, english = false),
        )
    }

    @Test
    fun gemmaIsNeverDownloadedWithoutTheUserAsking() {
        // The setup offer downloads Whisper, and Gemma only as part of a tier the
        // user accepted with its size in front of them; nothing here downloads
        // Gemma as a side effect of Whisper being wanted.
        val plan = SetupDownloadPlan.forTier(ModelSetup.Tier.WHISPER, gemmaAccepted = false)
        assertEquals(listOf(SttModelSet.MULTILINGUAL), plan.whisperSets)
        assertFalse(plan.gemma)
        // Even for the full tier: Gemma needs the user's yes, passed in explicitly.
        assertFalse(SetupDownloadPlan.forTier(ModelSetup.Tier.FULL, gemmaAccepted = false).gemma)
        assertTrue(SetupDownloadPlan.forTier(ModelSetup.Tier.FULL, gemmaAccepted = true).gemma)
        // And Whisper's own manual download never drags Gemma along.
        assertFalse(SetupDownloadPlan.whisperOnly().gemma)
    }
}
