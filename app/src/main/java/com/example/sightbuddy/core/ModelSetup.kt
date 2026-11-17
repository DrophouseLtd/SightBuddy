package com.example.sightbuddy.core

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.example.sightbuddy.core.stt.SttModelCatalog
import com.example.sightbuddy.core.stt.SttModelSet

/**
 * Which models suit this phone, judged from its specs alone (memory and free
 * storage) and the app language. No benchmark: the specs decide.
 *
 * - [Tier.FULL] (tier 3): Gemma for images, chat and Find objects, plus Whisper for speech.
 * - [Tier.WHISPER] (tier 2): Whisper for speech. No on-device answers.
 * - [Tier.NONE] (tier 1): nothing to download; the platform recogniser.
 *
 * Whisper now has a multilingual model, so speech is offered in Polish and
 * Finnish as well as English. Only Gemma is English-only: it needs an English
 * phone, and it is never downloaded as a side effect of wanting Whisper.
 */
object ModelSetup {

    enum class Tier { NONE, WHISPER, FULL }

    /**
     * The voice model a new download fetches: the multilingual Whisper base
     * (encoder, decoder, tokens) plus the silero VAD, as published — the real
     * byte count, not a rounded one.
     */
    val WHISPER_BYTES: Long = SttModelCatalog.of(SttModelSet.MULTILINGUAL).totalBytes

    /** Gemma 4 E2B, as downloaded. */
    const val GEMMA_BYTES = 2_588_147_712L

    /** Free space a recommended setup must leave behind, so the phone is not filled up. */
    const val RESERVE_BYTES = 1_500_000_000L

    /**
     * Gemma needs a phone sold with 8 GB of memory; such phones report about
     * 7.3 to 7.7 GB, hence the lower figure.
     */
    const val MIN_GEMMA_RAM_BYTES = 7_000_000_000L

    /**
     * [english] decides Gemma only. Whisper's multilingual model serves every
     * app language, so a Polish or Finnish phone is recommended Whisper exactly
     * as an English one is.
     */
    fun tier(totalRamBytes: Long, freeBytes: Long, english: Boolean): Tier = when {
        english && totalRamBytes >= MIN_GEMMA_RAM_BYTES &&
            freeBytes >= GEMMA_BYTES + WHISPER_BYTES + RESERVE_BYTES -> Tier.FULL
        freeBytes >= WHISPER_BYTES + RESERVE_BYTES -> Tier.WHISPER
        else -> Tier.NONE
    }

    fun packageBytes(tier: Tier): Long = when (tier) {
        Tier.FULL -> GEMMA_BYTES + WHISPER_BYTES
        Tier.WHISPER -> WHISPER_BYTES
        Tier.NONE -> 0L
    }

    fun totalRamBytes(context: Context): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.totalMem
    }

    /** Free space where the models go (app storage; both live on the same volume). */
    fun freeBytes(context: Context): Long = StatFs(context.filesDir.path).availableBytes

    /** Room for a manual download of [bytes], with a small margin. */
    fun hasSpaceFor(context: Context, bytes: Long): Boolean =
        freeBytes(context) >= bytes + 100_000_000L
}
