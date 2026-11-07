package com.example.sightbuddy.core.stt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Finished transcripts, each delivered once to whoever is listening when it
 * arrives.
 *
 * An event, not state. This used to be a `StateFlow` holding the last
 * transcript, and the app starts collecting again every time it comes back to
 * the foreground: each return handed the last spoken question to the new
 * collector, which answered it again in whichever feature was showing. With no
 * replay, a transcript that arrives while nobody listens is dropped, and one
 * already handled is never seen again.
 */
class Transcripts {
    private val _flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val flow: SharedFlow<String> = _flow.asSharedFlow()

    fun deliver(text: String) {
        if (text.isNotBlank()) _flow.tryEmit(text)
    }
}
