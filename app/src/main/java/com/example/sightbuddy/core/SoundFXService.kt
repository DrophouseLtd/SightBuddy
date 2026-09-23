package com.example.sightbuddy.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.example.sightbuddy.R

/**
 * Low-latency one-shot audio player backed by [SoundPool].
 *
 * Every [play] call stops the previously playing stream so samples never
 * overlap. Direction cues are spoken now; see [DirectionCues].
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

    /**
     * The loops are off: the waiting sound is the tick in [SFX.WORKING], which
     * the cue pool plays reliably. Turning this on plays a loop under the tick,
     * so take the tick out of AppController.workingTick at the same time.
     */
    private val LOOPS_ENABLED = false

    private var loopPlayer: MediaPlayer? = null
    private var playingLoop: Loop? = null
    private val loopAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

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
        sounds[SFX.LISTENING]        = soundPool.load(context, R.raw.sfx_listening, 1)
        sounds[SFX.STOP_LISTENING]   = soundPool.load(context, R.raw.sfx_stop_listening, 1)
        sounds[SFX.RECORDING_PERK]   = soundPool.load(context, R.raw.sfx_recording_perk, 1)
        sounds[SFX.RECORDING_PERK_STOP] = soundPool.load(context, R.raw.sfx_recording_perk_stop, 1)
        sounds[SFX.WORKING]          = soundPool.load(context, R.raw.sfx_working, 1)
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

    /**
     * Starts a quiet loop under everything else: the app is recording, or an
     * answer is on its way. One at a time, and never cut by [play] or [stop],
     * which belong to the one-shot cues.
     *
     * A MediaPlayer, not the pool: these are seconds long, where SoundPool is
     * for short cues held in memory decoded.
     *
     * Off, see [LOOPS_ENABLED].
     */
    fun startLoop(loop: Loop) {
        if (!LOOPS_ENABLED) return
        if (playingLoop == loop) return
        stopLoop()
        // Built by hand rather than MediaPlayer.create: the attributes have to be
        // set before the player is prepared, or it plays as usage=UNKNOWN — which
        // is what Android's audio dump showed while nothing could be heard.
        // Accessibility, like the cues, so the recogniser's ducking of media
        // cannot silence it.
        val player = MediaPlayer()
        val started = runCatching {
            appContext.resources.openRawResourceFd(loop.res).use { fd ->
                player.setAudioAttributes(loopAttributes)
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                player.isLooping = true
                player.setVolume(loop.volume, loop.volume)
                player.prepare()
                player.start()
            }
        }
        if (started.isFailure) {
            Log.w("SoundFXService", "Could not play the $loop loop", started.exceptionOrNull())
            runCatching { player.release() }
            return
        }
        loopPlayer = player
        playingLoop = loop
    }

    fun stopLoop() {
        loopPlayer?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        loopPlayer = null
        playingLoop = null
    }

    fun stop() {
        if (activeStreamId != 0) {
            soundPool.stop(activeStreamId)
            activeStreamId = 0
        }
    }


    /**
     * The hint chime is an ordinary cue now, so silencing it is silencing the pool.
     * Kept as its own name because callers read better for it.
     */
    fun stopHint() {
        stop()
    }

    fun shutdown() {
        stopHint()
        stopLoop()
        soundPool.release()
    }

    /**
     * A sound that runs under the app while something lasts. Quiet on purpose:
     * it marks time rather than asking for attention.
     *
     * The recording loop was withdrawn: it played into an open microphone and
     * was never heard anyway. This one is heard, once the attributes reached
     * the player (see [startLoop]); before that it played as usage=UNKNOWN,
     * with a Bluetooth sink connected, which is a good way to hear nothing.
     * [LOOPS_ENABLED] turns them all off again in one place.
     */
    enum class Loop(val res: Int, val volume: Float) {
        /** While an answer is being worked out. */
        LOADING(R.raw.sfx_loading_loop, 0.45f),
    }

    enum class SFX {
        CAMERA_CLICK,
        LISTENING,
        STOP_LISTENING,
        RECORDING_PERK,
        RECORDING_PERK_STOP,
        /** A soft tick, repeated while an answer is being worked out. */
        WORKING,
    }
}
