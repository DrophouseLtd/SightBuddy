package com.example.sightbuddy.core.llm

import android.util.Log
import com.example.sightbuddy.BuildConfig

/**
 * The dev build's log of streamed answers (issue #24), under `STREAM-ANSWER`:
 * each message LiteRT-LM hands over (when, how long, how it starts) and, per
 * answer, when its first sentence was said. It holds the start of what the AI
 * wrote, so like [com.example.sightbuddy.core.PrivateLog] it is never in a
 * release build, and only in the dev flavour the phone tests use.
 */
object StreamLog {
    const val TAG = "STREAM-ANSWER"

    fun i(message: () -> String) {
        if (BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev") Log.i(TAG, message())
    }
}
