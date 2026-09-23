package com.example.sightbuddy.core

import android.content.Context
import com.example.sightbuddy.R

/**
 * Says which way to move the phone, in the app's language, through the voice.
 *
 * These were recorded samples, one set per language. Spoken cues come from the
 * string resources instead, so a new language needs only its translations.
 * Each cue cuts whatever is being said: a direction that arrives late is wrong.
 */
class DirectionCues(private val context: Context, private val tts: TTSService) {

    enum class Cue(val textRes: Int) {
        LEFT(R.string.cue_left),
        RIGHT(R.string.cue_right),
        UP(R.string.cue_up),
        DOWN(R.string.cue_down),
        CLOSER(R.string.cue_closer),
        FURTHER(R.string.cue_further),
        CENTRE(R.string.cue_centre),
        /** Text scanning's "centred": the picture is taken if the phone stays put. */
        HOLD_STEADY(R.string.cue_hold_steady),
    }

    fun say(cue: Cue) {
        tts.speak(context.getString(cue.textRes), flush = true, utteranceId = UTTERANCE_ID)
    }

    private companion object {
        const val UTTERANCE_ID = "DIRECTION_CUE"
    }
}
