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

    /** Stops whatever [complete] is doing or waiting for; it then returns null. */
    fun cancel() {}
}
