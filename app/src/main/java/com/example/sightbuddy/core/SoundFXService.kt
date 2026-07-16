package com.example.sightbuddy.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.example.sightbuddy.R

/**
 * Low-latency one-shot audio player backed by [SoundPool].
 *
 * Every [play] call stops the previously playing stream so samples never
 * overlap — critical for the rapid-fire directional cues in Find Object mode.
 *
 * SoundPool.load() is asynchronous; samples are only playable after the
 * OnLoadCompleteListener fires for their sound ID.
 */
class SoundFXService(context: Context) {

    private val appContext = context.applicationContext
    private val soundPool: SoundPool

    private val sounds = mutableMapOf<SFX, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private var activeStreamId: Int = 0
    private var tutorialPlayer: MediaPlayer? = null
    private var hintPlayer: MediaPlayer? = null

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
                Log.d("SoundFXService", "Loaded sample $sampleId (${loadedIds.size}/${sounds.size} ready)")
            } else {
                Log.e("SoundFXService", "Failed to load sample $sampleId, status=$status")
            }
        }

        sounds[SFX.CAMERA_CLICK]     = soundPool.load(context, R.raw.sfx_camera_click, 1)
        sounds[SFX.DIRECTION_LEFT]   = soundPool.load(context, R.raw.sfx_direction_left, 1)
        sounds[SFX.DIRECTION_RIGHT]  = soundPool.load(context, R.raw.sfx_direction_right, 1)
        sounds[SFX.DIRECTION_UP]     = soundPool.load(context, R.raw.sfx_direction_up, 1)
        sounds[SFX.DIRECTION_DOWN]   = soundPool.load(context, R.raw.sfx_direction_down, 1)
        sounds[SFX.DIRECTION_CENTRE] = soundPool.load(context, R.raw.sfx_direction_centre, 1)
        sounds[SFX.LISTENING]        = soundPool.load(context, R.raw.sfx_listening, 1)
        sounds[SFX.STOP_LISTENING]   = soundPool.load(context, R.raw.sfx_stop_listening, 1)
    }

    /**
     * Play a sound effect, cutting any previously playing sample first.
     * @param loop  -1 = loop forever, 0 = play once (default)
     */
    fun play(sfx: SFX, loop: Int = 0) {
        stop()
        val id = sounds[sfx] ?: return
        if (id !in loadedIds) {
            Log.w("SoundFXService", "$sfx (id=$id) not loaded yet, skipping")
            return
        }
        activeStreamId = soundPool.play(id, 1f, 1f, 1, loop, 1f)
    }

    fun stop() {
        if (activeStreamId != 0) {
            soundPool.stop(activeStreamId)
            activeStreamId = 0
        }
    }

    /**
     * Plays the longer tutorial sample from the sample pack (one-shot).
     * Stops any active SoundPool stream first. Invokes [onComplete] when playback finishes
     * or if the sample cannot be loaded.
     */
    fun playTutorial(onComplete: (() -> Unit)? = null) {
        stop()
        stopTutorial()
        val player = MediaPlayer.create(appContext, R.raw.sfx_tutorial)
        if (player == null) {
            Log.e("SoundFXService", "Failed to create tutorial MediaPlayer")
            onComplete?.invoke()
            return
        }
        tutorialPlayer = player
        player.setOnCompletionListener {
            stopTutorial()
            onComplete?.invoke()
        }
        player.start()
    }

    fun stopTutorial() {
        tutorialPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        tutorialPlayer = null
    }

    /** Short earcon for one-time automated hints only (not manual Help opens). */
    fun playHint() {
        stop()
        stopTutorial()
        stopHint()
        startHintPlayer()
    }

    /**
     * Hint earcon during welcome tutorial — does not stop [playTutorial] audio.
     */
    fun playHintOverlay() {
        stop()
        stopHint()
        startHintPlayer()
    }

    private fun startHintPlayer() {
        val player = MediaPlayer.create(appContext, R.raw.sfx_hint)
        if (player == null) {
            Log.e("SoundFXService", "Failed to create hint MediaPlayer")
            return
        }
        hintPlayer = player
        player.setOnCompletionListener {
            stopHint()
        }
        player.start()
    }

    fun stopHint() {
        hintPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        hintPlayer = null
    }

    fun shutdown() {
        stopTutorial()
        stopHint()
        soundPool.release()
    }

    enum class SFX {
        CAMERA_CLICK,
        DIRECTION_LEFT,
        DIRECTION_RIGHT,
        DIRECTION_UP,
        DIRECTION_DOWN,
        DIRECTION_CENTRE,
        LISTENING,
        STOP_LISTENING
    }
}
