package com.example.sightbuddy.core

import com.example.sightbuddy.core.ModelSetup.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSetupTest {

    private val gb = 1_000_000_000L

    @Test
    fun eightGigabytePhoneWithRoomGetsEverything() {
        assertEquals(Tier.FULL, ModelSetup.tier(totalRamBytes = 7_500_000_000L, freeBytes = 10 * gb, english = true))
    }

    @Test
    fun lessMemoryGetsWhisperOnly() {
        assertEquals(Tier.WHISPER, ModelSetup.tier(totalRamBytes = 5_700_000_000L, freeBytes = 10 * gb, english = true))
    }

    @Test
    fun gemmaNeedsItsSizeWhisperAndTheReserveFree() {
        // 2.59 + 0.16 + 1.5 = 4.25 GB needed; 4 GB free is not enough, but Whisper fits.
        assertEquals(Tier.WHISPER, ModelSetup.tier(totalRamBytes = 12 * gb, freeBytes = 4 * gb, english = true))
    }

    @Test
    fun underTheReserveNothingIsRecommended() {
        assertEquals(Tier.NONE, ModelSetup.tier(totalRamBytes = 12 * gb, freeBytes = 1_600_000_000L, english = true))
    }

    @Test
    fun otherLanguagesGetGemmaForImagesWhenHardwareFits() {
        // Chat and image understanding are multilingual; this recommendation
        // must not reuse the independent English-only Gemma speech gate.
        assertEquals(Tier.FULL, ModelSetup.tier(totalRamBytes = 12 * gb, freeBytes = 100 * gb, english = false))
        assertEquals(Tier.WHISPER, ModelSetup.tier(totalRamBytes = 5_700_000_000L, freeBytes = 10 * gb, english = false))
        // The reserve still applies there: a full phone gets nothing.
        assertEquals(Tier.NONE, ModelSetup.tier(totalRamBytes = 12 * gb, freeBytes = 1_600_000_000L, english = false))
    }

    @Test
    fun theWhisperPackageIsTheMultilingualFilesRealSize() {
        // 29,120,534 + 130,672,026 + 816,730 + 643,854
        assertEquals(161_253_144L, ModelSetup.WHISPER_BYTES)
        assertEquals(161_253_144L, ModelSetup.packageBytes(Tier.WHISPER))
        assertTrue(ModelSetup.packageBytes(Tier.FULL) == ModelSetup.GEMMA_BYTES + 161_253_144L)
        assertEquals(0L, ModelSetup.packageBytes(Tier.NONE))
    }
}
