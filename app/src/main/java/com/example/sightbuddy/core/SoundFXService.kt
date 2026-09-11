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
    private var primed = false
    private var tutorialPlayer: MediaPlayer? = null

    init {
        val attrs = AudioAttributes.Builder()
            // Accessibility, not media. Media is ducked to a fraction of its volume
            // while the speech recogniser holds audio focus, which is exactly when
            // these cues play, and it made them almost inaudible.
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
                Log.d("SoundFXService", "Loaded sample $sampleId (${loadedIds.size}/${sounds.size} ready)")
                // Play it once inaudibly. The first sound through a cold audio path
                // comes out at a fraction of its volume while the output ramps up,
                // which is why the cue right after launch was so quiet. Spending
                // that first play on silence means the user never hears it.
                if (!primed) {
                    primed = true
                    soundPool.play(sampleId, 0f, 0f, 0, 0, 1f)
                }
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
        sounds[SFX.RECORDING_PERK]   = soundPool.load(context, R.raw.sfx_recording_perk, 1)
        sounds[SFX.RECORDING_PERK_STOP] = soundPool.load(context, R.raw.sfx_recording_perk_stop, 1)
        sounds[SFX.HINT]             = soundPool.load(context, R.raw.sfx_hint, 1)
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

    /**
     * Play without silencing whatever is already sounding.
     *
     * [play] cuts the current cue first, which is right when one cue replaces
     * another. Two cues that describe different things are not replacements: the
     * shutter and the end of a recording happen at the same instant in
     * auto-capture, and cutting one to start the other lost the shutter entirely.
     *
     * The stream is deliberately not tracked, so [stop] still refers to the cue
     * that can loop rather than to this one-shot.
     */
    fun playAlongside(sfx: SFX) {
        val id = sounds[sfx] ?: return
        if (id !in loadedIds) {
            Log.w("SoundFXService", "$sfx (id=$id) not loaded yet, skipping")
            return
        }
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
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
        // Spoken guidance, so it declares itself as speech rather than borrowing the
        // cue pool's attributes. It is the one clip in the app with words in it.
        val speechAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val player = MediaPlayer.create(appContext, R.raw.sfx_tutorial, speechAttrs, 0)
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
        stopTutorial()
        play(SFX.HINT)
    }

    /**
     * Hint earcon during welcome tutorial — does not stop [playTutorial] audio.
     */
    fun playHintOverlay() {
        play(SFX.HINT)
    }

    /**
     * The hint chime is an ordinary cue now, so silencing it is silencing the pool.
     * Kept as its own name because callers read better for it.
     */
    fun stopHint() {
        stop()
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
        STOP_LISTENING,
        RECORDING_PERK,
        RECORDING_PERK_STOP,
        HINT
    }
}
