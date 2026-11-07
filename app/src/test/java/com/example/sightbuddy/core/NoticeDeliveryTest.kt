package com.example.sightbuddy.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeDeliveryTest {

    private var waits = 0
    private val noWait: suspend (Long) -> Unit = { waits++ }

    @Test
    fun aFreeVoiceSaysTheNoticeAtOnce() = runBlocking {
        var said = 0
        NoticeDelivery.deliver(micHasFloor = { false }, speak = { said++; true }, wait = noWait)
        assertEquals(1, said)
        assertEquals(0, waits)
    }

    @Test
    fun theNoticeWaitsWhileARecordingHoldsTheMicrophone() = runBlocking {
        var floorChecks = 0
        var said = 0
        NoticeDelivery.deliver(
            // Held for the first three looks, then free.
            micHasFloor = { floorChecks++ < 3 },
            speak = { said++; true },
            wait = noWait,
        )
        assertEquals(1, said)
        assertEquals(3, waits)
    }

    @Test
    fun aLineTheVoiceDidNotTakeIsTriedAgain() = runBlocking {
        var tries = 0
        NoticeDelivery.deliver(micHasFloor = { false }, speak = { ++tries >= 3 }, wait = noWait)
        assertEquals(3, tries)
        assertEquals(2, waits)
    }
}
