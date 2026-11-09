package com.example.sightbuddy.core.stt

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A spoken question is answered once. The app collects transcripts again every
 * time it returns to the foreground, and a transcript kept as state was handed
 * to each new collector: leaving and reopening the app asked the last spoken
 * question again, in whichever feature was showing.
 */
class TranscriptsTest {

    @Test
    fun aQuestionReachesTheFeatureListeningWhenItArrives() = runBlocking {
        val transcripts = Transcripts()
        val heard = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(1_000) { transcripts.flow.first() }
        }
        transcripts.deliver("What colour is the cup?")
        assertEquals("What colour is the cup?", heard.await())
    }

    @Test
    fun reopeningTheAppDoesNotAskTheLastQuestionAgain() = runBlocking {
        val transcripts = Transcripts()
        val firstVisit = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(1_000) { transcripts.flow.first() }
        }
        transcripts.deliver("What colour is the cup?")
        firstVisit.await()

        // Back from the home screen: a new collector starts.
        val afterReturn = withTimeoutOrNull(200) { transcripts.flow.first() }
        assertNull(afterReturn)
    }

    @Test
    fun theSameQuestionTwiceIsAnsweredTwice() = runBlocking {
        val transcripts = Transcripts()
        // Bounded, so a lost second question fails the test instead of hanging it.
        val heard = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(1_000) { transcripts.flow.take(2).toList() }
        }
        transcripts.deliver("Read it again")
        transcripts.deliver("Read it again")
        assertEquals(listOf("Read it again", "Read it again"), heard.await())
    }

    @Test
    fun nothingHeardIsNotAQuestion() = runBlocking {
        val transcripts = Transcripts()
        val heard = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(200) { transcripts.flow.first() }
        }
        transcripts.deliver("  ")
        assertNull(heard.await())
    }
}
