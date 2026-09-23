package com.example.sightbuddy.core

import com.example.sightbuddy.core.llm.LocalAnswerer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Use API" switch: while it is off, nothing may reach OpenAI, whatever key
 * is stored. A request that got as far as the network would throw here
 * (buildChatCompletionsRequest refuses) or hang on the dead client.
 */
class ApiGateTest {

    private val deadClient = OkHttpClient.Builder()
        .addInterceptor { throw AssertionError("A request reached the network") }
        .build()

    private fun transport(allowed: Boolean, local: LocalAnswerer? = null) = OpenAiTransport(
        apiKeyProvider = { "sk-test" },
        apiAllowed = { allowed },
        local = local,
    )

    private fun ask(t: OpenAiTransport) = OpenAiTransport.executeChatCompletion(
        httpClient = deadClient,
        transport = t,
        openAiRequestBody = JSONObject(),
        logTag = "test",
        registerCall = {},
    )

    @Test
    fun switchedOff_hidesTheKey() {
        val t = transport(allowed = false)
        assertFalse(t.isConfigured())
        assertEquals("", t.apiKey())
    }

    @Test(expected = IllegalStateException::class)
    fun switchedOff_refusesToBuildARequest() {
        transport(allowed = false).buildChatCompletionsRequest(JSONObject())
    }

    @Test
    fun switchedOff_noLocalModel_saysSoWithoutCallingOpenAi() {
        val t = transport(allowed = false)
        assertEquals(t.errors.apiOff, ask(t))
    }

    @Test
    fun tooLongForTheModel_isToldInTheChat() {
        fun localThrowing(chatTooLong: Boolean) = object : LocalAnswerer {
            override fun isReady() = true
            override fun complete(openAiRequestBody: JSONObject): String? =
                throw com.example.sightbuddy.core.llm.PromptTooLongException(chatTooLong)
        }
        val message = transport(allowed = false, local = localThrowing(chatTooLong = false))
        assertEquals(message.errors.tooLong, ask(message))
        val chat = transport(allowed = false, local = localThrowing(chatTooLong = true))
        assertEquals(chat.errors.chatFull, ask(chat))
    }

    @Test
    fun switchedOff_answersOnDevice() {
        val local = object : LocalAnswerer {
            override fun isReady() = true
            override fun complete(openAiRequestBody: JSONObject) = "local answer"
        }
        val t = transport(allowed = false, local = local)
        assertTrue(t.canAnswer())
        assertEquals("local answer", ask(t))
    }
}
