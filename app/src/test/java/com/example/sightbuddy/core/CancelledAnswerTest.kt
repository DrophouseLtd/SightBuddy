package com.example.sightbuddy.core

import com.example.sightbuddy.core.llm.LocalAnswerer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * A request cancelled on purpose (a new picture, leaving the feature) has
 * nothing to say. It used to be said as "Could not reach OpenAI", or on the
 * phone as "The on-device model could not answer".
 */
class CancelledAnswerTest {

    /** The unit-test android.jar's JSONObject is a stub; the request needs a body. */
    private val body = object : JSONObject() {
        override fun toString() = "{}"
    }

    private fun ask(t: OpenAiTransport, client: OkHttpClient, registerCall: (okhttp3.Call?) -> Unit = {}) =
        OpenAiTransport.executeChatCompletion(
            httpClient = client,
            transport = t,
            openAiRequestBody = body,
            logTag = "test",
            registerCall = registerCall,
        )

    @Test
    fun `a cancelled OpenAI call is not a network error`() {
        val t = OpenAiTransport(apiKeyProvider = { "sk-test" })
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (!chain.call().isCanceled()) throw AssertionError("A request reached the network")
                chain.proceed(chain.request())
            }
            .build()
        // Cancelled as soon as it is registered, as cancelActiveRequest does.
        assertEquals("", ask(t, client, registerCall = { it?.cancel() }))
    }

    @Test
    fun `a cancel while the reply is still arriving is not a network error`() {
        val t = OpenAiTransport(apiKeyProvider = { "sk-test" })
        var registered: okhttp3.Call? = null
        // The headers have come; the body is cancelled half way, as when a new
        // picture is taken while a long answer downloads.
        val stalled = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                registered?.cancel()
                throw IOException("Socket closed")
            }
            override fun timeout() = Timeout.NONE
            override fun close() {}
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(stalled.buffer().asResponseBody("application/json".toMediaType(), -1))
                    .build()
            }
            .build()
        assertEquals("", ask(t, client, registerCall = { if (it != null) registered = it }))
    }

    @Test
    fun `a call that fails on its own is still a network error`() {
        val t = OpenAiTransport(apiKeyProvider = { "sk-test" })
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("no route") }
            .build()
        assertEquals(t.errors.network, ask(t, client))
    }

    /** Waits in [complete] until cancelled, then gives up as LocalGemma does. */
    private class WaitingLocal(private val answer: String? = null) : LocalAnswerer {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        override fun isReady() = true
        override fun complete(openAiRequestBody: JSONObject): String? {
            started.countDown()
            cancelled.await(5, TimeUnit.SECONDS)
            return answer
        }
        override fun cancel() = cancelled.countDown()
    }

    private val unusedClient = OkHttpClient.Builder()
        .addInterceptor { throw AssertionError("A request reached the network") }
        .build()

    @Test
    fun `a cancelled on-device answer is not an error`() {
        val local = WaitingLocal()
        val t = OpenAiTransport(apiKeyProvider = { "" }, apiAllowed = { false }, local = local)
        val answer = FutureTask { ask(t, unusedClient) }
        Thread(answer).start()
        assertEquals(true, local.started.await(5, TimeUnit.SECONDS))
        t.cancelLocal()
        assertEquals("", answer.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `an on-device answer that fails on its own still says so`() {
        val local = object : LocalAnswerer {
            override fun isReady() = true
            override fun complete(openAiRequestBody: JSONObject): String? = null
        }
        val t = OpenAiTransport(apiKeyProvider = { "" }, apiAllowed = { false }, local = local)
        assertEquals(t.errors.localFailed, ask(t, unusedClient))
    }
}
