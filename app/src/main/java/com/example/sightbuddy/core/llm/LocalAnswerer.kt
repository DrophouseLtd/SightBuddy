package com.example.sightbuddy.core.llm

import org.json.JSONObject

/**
 * An on-device model that can stand in for OpenAI's Chat Completions.
 *
 * Takes the same request body the app builds for OpenAI, so the features do not
 * need to know where their answer comes from.
 */
/**
 * The request would not fit the model's context window. [chatTooLong]: it is the
 * conversation so far that does not fit; the latest message alone would.
 */
class PromptTooLongException(val chatTooLong: Boolean) : Exception()

interface LocalAnswerer {
    fun isReady(): Boolean

    /** Blocking. Returns the reply text, or null on failure or [cancel]. Throws [PromptTooLongException]. */
    fun complete(openAiRequestBody: JSONObject): String?

    /**
     * [complete], but the reply's text is handed to [onDelta] as it is written
     * (issue #24), each call with the text new since the last, from whatever
     * thread the model writes on. Blocking; returns the whole reply, or null
     * on failure or [cancel]. The default hands over the complete reply once.
     */
    fun stream(openAiRequestBody: JSONObject, onDelta: (String) -> Unit): String? =
        complete(openAiRequestBody)?.also(onDelta)

    /** Stops whatever [complete] or [stream] is doing or waiting for; it then returns null. */
    fun cancel() {}
}
