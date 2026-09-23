package com.example.sightbuddy.core

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/**
 * Which models suit this phone, judged from its specs alone (memory and free
 * storage) and the app language. No benchmark: the specs decide.
 *
 * - [Tier.FULL] (tier 3): Gemma for images, chat and Find objects, plus Whisper for speech.
 * - [Tier.WHISPER] (tier 2): Whisper for speech. No on-device answers.
 * - [Tier.NONE] (tier 1): nothing to download; the platform recogniser.
 *
 * Both models are English only, so other languages are always tier 1.
 */
object ModelSetup {

    enum class Tier { NONE, WHISPER, FULL }

    /** Whisper base.en int8 plus the VAD, as downloaded. */
    const val WHISPER_BYTES = 161_300_000L

    /** Gemma 4 E2B, as downloaded. */
    const val GEMMA_BYTES = 2_588_147_712L

    /** Free space a recommended setup must leave behind, so the phone is not filled up. */
    const val RESERVE_BYTES = 1_500_000_000L

    /**
     * Gemma needs a phone sold with 8 GB of memory; such phones report about
     * 7.3 to 7.7 GB, hence the lower figure.
     */
    const val MIN_GEMMA_RAM_BYTES = 7_000_000_000L

    fun tier(totalRamBytes: Long, freeBytes: Long, english: Boolean): Tier = when {
        !english -> Tier.NONE
        totalRamBytes >= MIN_GEMMA_RAM_BYTES &&
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
