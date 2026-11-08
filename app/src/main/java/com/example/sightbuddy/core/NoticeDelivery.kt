package com.example.sightbuddy.core

/**
 * A notice that must be said once and not lost, such as a model found damaged
 * and removed. Speech asked for while a recording holds the microphone is
 * dropped, not queued ([TTSService.takeMicrophoneFloor]), so the notice waits
 * for the floor to be free and is tried again until the voice takes it.
 */
object NoticeDelivery {

    /**
     * Returns once [speak] has taken the line (returned true), never while
     * [micHasFloor]; between tries it waits [retryMs].
     */
    suspend fun deliver(
        micHasFloor: () -> Boolean,
        speak: () -> Boolean,
        retryMs: Long = 1_000L,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ) {
        while (micHasFloor() || !speak()) wait(retryMs)
    }
}
