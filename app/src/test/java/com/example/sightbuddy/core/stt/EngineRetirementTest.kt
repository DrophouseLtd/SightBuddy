package com.example.sightbuddy.core.stt

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class EngineRetirementTest {
    @Test fun cancellationDoesNotReleaseDuringBlockingNativeWork() = runBlocking {
        val entered = CountDownLatch(1)
        val allowReturn = CountDownLatch(1)
        val released = AtomicInteger()
        val worker = launch(Dispatchers.Default) {
            entered.countDown()
            check(allowReturn.await(5, TimeUnit.SECONDS))
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            retireEngineAfter(worker) { released.incrementAndGet() }
            worker.cancel()
            assertEquals(0, released.get())
        } finally { allowReturn.countDown() }
        worker.join()
        assertEquals(1, released.get())
    }

    @Test fun idleEngineCanBeReleasedImmediately() {
        val released = AtomicInteger()
        retireEngineAfter(null) { released.incrementAndGet() }
        assertEquals(1, released.get())
    }

    @Test fun cloudKeepsPriorityWhenLocalChoiceIsPressed() {
        assertEquals(SttChoice.ANDROID, SttChoice.nextAvailable(SttChoice.ANDROID, true, false, cloudEnabled = true))
    }

    @Test fun failedLoadIsNotOfferedOrConfirmedAsWhisper() {
        assertEquals(SttChoice.ANDROID, SttChoice.nextAvailable(SttChoice.WHISPER, false, false))
        assertEquals(SttChoice.ANDROID, SttChoice.nextAvailable(SttChoice.ANDROID, false, true))
        assertEquals(SttChoice.WHISPER, SttChoice.nextAvailable(SttChoice.ANDROID, true, false))
        assertEquals(SttChoice.ANDROID, SttChoice.nextAvailable(SttChoice.WHISPER, true, false))
    }
}
