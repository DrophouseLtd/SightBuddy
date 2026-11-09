package com.example.sightbuddy.core

import com.example.sightbuddy.core.llm.LocalAnswerer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Issue #24: an on-device answer handed over as it is written. The first
 * attempt returned the part written so far when an answer was cancelled half
 * way (a new picture, leaving), so the old answer's half went into the new
 * chat and its history.
 */
class StreamedAnswerTest {

    /** The unit-test android.jar's JSONObject is a stub; the request needs a body. */
    private val body = object : JSONObject() {
        override fun toString() = "{}"
    }

    private val unusedClient = OkHttpClient.Builder()
        .addInterceptor { throw AssertionError("A request reached the network") }
        .build()

    private fun ask(t: OpenAiTransport, client: OkHttpClient = unusedClient, onDelta: ((String) -> Unit)?) =
        OpenAiTransport.executeChatCompletion(
            httpClient = client,
            transport = t,
            openAiRequestBody = body,
            logTag = "test",
            registerCall = {},
            onDelta = onDelta,
        )

    /** Writes [parts], waiting before the last until let go or cancelled. */
    private class StreamingLocal(private val parts: List<String>) : LocalAnswerer {
        val waiting = CountDownLatch(1)
        val goOn = CountDownLatch(1)
        @Volatile var cancelled = false
        var completeCalls = 0
        override fun isReady() = true
        override fun complete(openAiRequestBody: JSONObject): String? {
            completeCalls++
            return parts.joinToString("")
        }
        override fun stream(openAiRequestBody: JSONObject, onDelta: (String) -> Unit): String? {
            parts.dropLast(1).forEach(onDelta)
            waiting.countDown()
            goOn.await(5, TimeUnit.SECONDS)
            if (cancelled) return null
            onDelta(parts.last())
            return parts.joinToString("")
        }
        override fun cancel() {
            cancelled = true
            goOn.countDown()
        }
    }

    private fun onDevice(local: LocalAnswerer) =
        OpenAiTransport(apiKeyProvider = { "" }, apiAllowed = { false }, local = local)

    @Test
    fun `the answer is handed over as it is written and returned whole`() {
        val local = StreamingLocal(listOf("\nThe shop", " opens at nine.", " Bye. "))
        local.goOn.countDown()
        val parts = mutableListOf<String>()
        val answer = ask(onDevice(local)) { parts += it }
        assertEquals(listOf("\nThe shop", " opens at nine.", " Bye. "), parts)
        // Trimmed, as a complete answer is.
        assertEquals("The shop opens at nine. Bye.", answer)
        assertEquals(0, local.completeCalls)
    }

    @Test
    fun `an answer cancelled half way is cancelled, not the half written so far`() {
        val local = StreamingLocal(listOf("The first sentence. ", "The rest."))
        val t = onDevice(local)
        val answer = FutureTask { ask(t) { } }
        Thread(answer).start()
        assertTrue(local.waiting.await(5, TimeUnit.SECONDS))
        t.cancelLocal()
        assertEquals(OpenAiTransport.CANCELLED, answer.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `an answer cancelled as it ends is still cancelled`() {
        val local = StreamingLocal(listOf("All of it. ", "Done."))
        local.goOn.countDown()
        val t = onDevice(local)
        // The cancel lands while the last part is handed over, and the model
        // still returns all of it: the answer belongs to a chat already left.
        val answer = ask(t) { if (it == "Done.") t.cancelLocal() }
        assertEquals(OpenAiTransport.CANCELLED, answer)
    }

    @Test
    fun `an on-device answer that fails with nothing written still says so`() {
        val local = object : LocalAnswerer {
            override fun isReady() = true
            override fun complete(openAiRequestBody: JSONObject): String? = null
            override fun stream(openAiRequestBody: JSONObject, onDelta: (String) -> Unit): String? = null
        }
        val t = onDevice(local)
        assertEquals(t.errors.localFailed, ask(t) { throw AssertionError("nothing was written") })
    }

    @Test
    fun `an answer that fails after writing part of it is a failure, not the part`() {
        // LocalGemma returns null when the model fails or runs out of time part way.
        val local = object : LocalAnswerer {
            override fun isReady() = true
            override fun complete(openAiRequestBody: JSONObject): String? = null
            override fun stream(openAiRequestBody: JSONObject, onDelta: (String) -> Unit): String? {
                onDelta("The label says. Take two")
                return null
            }
        }
        val t = onDevice(local)
        assertEquals(t.errors.localFailed, ask(t) { })
    }

    @Test
    fun `without a delta handler the answer is complete, as before`() {
        val local = StreamingLocal(listOf("One. ", "Two."))
        assertEquals("One. Two.", ask(onDevice(local), onDelta = null))
        assertEquals(1, local.completeCalls)
    }

    @Test
    fun `OpenAI's answer, or its error, comes whole and is not handed over as parts`() {
        val t = OpenAiTransport(apiKeyProvider = { "sk-test" })
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("no route") }
            .build()
        val parts = mutableListOf<String>()
        assertEquals(t.errors.network, ask(t, client) { parts += it })
        assertEquals(emptyList<String>(), parts)
    }
}
