package com.example.sightbuddy.core

import android.util.Log
import com.example.sightbuddy.BuildConfig

/**
 * Logs that carry what the user said, read or was told: transcriptions, voice
 * commands, the phrase asked for in Find objects, and response bodies that may
 * echo them. Debug builds only. Release logs never hold user content: logcat is
 * readable over USB and goes into every bug report.
 *
 * The message is a lambda, so a release build never even builds the string.
 * Warnings and errors without content keep using [Log] directly.
 */
object PrivateLog {
    fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag, message())
    }
}
