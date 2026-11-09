package com.example.sightbuddy.features.chat

import com.example.sightbuddy.core.OpenAiTransport
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * What each chat request asks the AI, by route: OpenAI, or Gemma on the phone.
 */
class ChatPromptsTest {

    private lateinit var savedLocale: Locale

    @Before
    fun finnishApp() {
        // The app language is the default locale (LanguageStore); Finnish shows
        // the rule most clearly, as it is the one that must not be lost.
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fi"))
    }

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val page = "MENU\nSoup 6.50\nBread 2.00"

    /** Every request's text the chats send on [onDevice]'s route, as system + user. */
    private fun everyRequest(onDevice: Boolean): List<String> = listOf(
        ChatPrompts.documentSummary(page, onDevice).let { it.system + it.user },
        ChatPrompts.documentQuestion(page, "how much is the soup", onDevice).let { it.system + it.user },
        ChatPrompts.documentFollowUp(page, "user: hi", "and the bread", onDevice).let { it.system + it.user },
        ChatPrompts.imageFirst("", onDevice).let { it.system + it.user },
        ChatPrompts.imageFirst("what does the sign say", onDevice).let { it.system + it.user },
        ChatPrompts.imageFollowUp("and the colour", onDevice).let { it.system + it.user },
    )

    // Issue #20: Gemma replies in the question's language, and text it writes
    // out stays in its own language; OpenAI keeps the app-language rule.

    @Test
    fun `OpenAI is still told to reply in the app language`() {
        val rule = OpenAiTransport.replyLanguageInstruction()
        assertTrue(rule.contains("Vastaa aina suomeksi"))
        everyRequest(onDevice = false).forEach { assertTrue(it, it.contains("suomeksi")) }
        // Requests that answer no message from the user keep the rule exactly as it was.
        listOf(
            ChatPrompts.documentSummary(page, onDevice = false),
            ChatPrompts.imageFirst("", onDevice = false),
        ).forEach { assertTrue(it.system + it.user, (it.system + it.user).contains(rule)) }
    }

    /** Every request that answers the user's message, as system + user. */
    private fun readingRequests(onDevice: Boolean): List<String> =
        (textQuestions(onDevice) + imageQuestions(onDevice)).map { it.system + it.user }

    @Test
    fun `on OpenAI, text read out is not translated into the app language`() {
        // "Vastaa aina suomeksi" at the end outweighed the reading rule: a
        // Finnish user reading an English page heard it in Finnish.
        readingRequests(onDevice = false).forEach { request ->
            assertFalse(request, request.contains(OpenAiTransport.replyLanguageInstruction()))
            // Written in the target language, as the app-language rule is.
            assertTrue(request, request.contains("omat sanasi aina suomeksi"))
            assertTrue(request, request.contains("pysyy alkuperäisellä kielellään"))
            assertTrue(request, request.contains("ellei käyttäjä pyydä käännöstä"))
        }
    }

    @Test
    fun `in an English app the reading rule is in English`() {
        Locale.setDefault(Locale.ENGLISH)
        readingRequests(onDevice = false).forEach { request ->
            assertFalse(request, request.contains(OpenAiTransport.replyLanguageInstruction()))
            assertTrue(request, request.contains("Always reply in English in your own words"))
            assertTrue(request, request.contains("Text you read out stays in its original language"))
        }
    }

    @Test
    fun `in a Finnish app Gemma writes its own words in Finnish, and text read out stays in its own`() {
        // The phone, 2026-11-07: in a Finnish app Gemma answered in English, as
        // the summary after a picture has no question of the user's to follow.
        everyRequest(onDevice = true).forEach { request ->
            assertTrue(request, request.contains("suomeksi"))
            assertTrue(request, request.contains("original language"))
            assertFalse(request, request.contains("language of the user's question"))
        }
    }

    @Test
    fun `in an English app Gemma replies in the question's language and keeps quoted text in its own`() {
        Locale.setDefault(Locale.ENGLISH)
        everyRequest(onDevice = true).forEach { request ->
            assertTrue(request, request.contains("language of the user's question"))
            assertTrue(request, request.contains("original language"))
        }
    }

    // Issue #17: the first answer after a fresh scan ends by saying whether the
    // text seems complete; follow-ups do not.

    // The general question, not a reading's own closing sentence.
    private fun asksAboutCompleteness(request: String) =
        request.contains(ChatPrompts.COMPLETENESS_INSTRUCTION.trim())

    @Test
    fun `the first answer after a scan says whether the text seems complete, on both routes`() {
        for (onDevice in listOf(false, true)) {
            val summary = ChatPrompts.documentSummary(page, onDevice).user
            val question = ChatPrompts.documentQuestion(page, "how much is the soup", onDevice).user
            assertTrue(summary, asksAboutCompleteness(summary))
            assertTrue(question, asksAboutCompleteness(question))
        }
    }

    @Test
    fun `after a raw reading, the first AI answer is asked about completeness`() {
        // Auto text summarization is off by default: Capture reads the page
        // with no AI, so the first question about it is the first AI answer.
        for (onDevice in listOf(false, true)) {
            val first = ChatPrompts.documentFollowUp(page, "", "how much is the soup", onDevice)
            assertTrue(first.user, asksAboutCompleteness(first.user))
        }
    }

    @Test
    fun `later follow-ups and Image chat are not asked about completeness`() {
        for (onDevice in listOf(false, true)) {
            val followUp = ChatPrompts.documentFollowUp(page, "user: hi", "and the bread", onDevice)
            assertFalse(asksAboutCompleteness(followUp.system + followUp.user))
            val image = ChatPrompts.imageFirst("", onDevice).let { it.system + it.user }
            assertFalse(asksAboutCompleteness(image))
        }
    }

    // Issue #13. The skills were dropped on 2026-10-28 (Gemma summarised
    // instead of following them): every answer to the user's message says
    // plainly to read the text in full as it is when asked, and to answer
    // from the text's own words.

    private fun textQuestions(onDevice: Boolean): List<ChatPrompts.Prompt> = listOf(
        ChatPrompts.documentQuestion(page, "read the menu", onDevice),
        ChatPrompts.documentFollowUp(page, "", "read the menu", onDevice),
        ChatPrompts.documentFollowUp(page, "user: hi", "look for the soup", onDevice),
    )

    private fun imageQuestions(onDevice: Boolean): List<ChatPrompts.Prompt> = listOf(
        ChatPrompts.imageFirst("read the sign", onDevice),
        ChatPrompts.imageFollowUp("read it", onDevice),
    )

    @Test
    fun `there are no skills to choose any more`() {
        for (onDevice in listOf(false, true)) {
            (textQuestions(onDevice) + imageQuestions(onDevice)).forEach {
                val text = it.system + it.user
                assertFalse(text, text.contains("Skills:"))
                assertFalse(text, text.contains("Look for:"))
                assertFalse(text, text.contains("use a skill"))
            }
        }
    }

    @Test
    fun `asked to read, the AI reads all of the text as it is, on both routes`() {
        for (onDevice in listOf(false, true)) {
            (textQuestions(onDevice) + imageQuestions(onDevice)).forEach {
                val system = it.system
                assertTrue(system, system.contains("If the user asks you to read the text, read out all of the text"))
                assertTrue(system, system.contains("from its very first line to its last, as it is"))
                assertTrue(system, system.contains("do not summarise, reword or correct it"))
                assertTrue(system, system.contains("continues from a previous page or comes before the first heading"))
                // Mixed-up order is put back only when the original is clear (decided 2026-10-28).
                assertTrue(system, system.contains("at least 90% sure of the original order"))
                assertTrue(system, system.contains("changing nothing else"))
                assertTrue(system, system.contains("lines cut off at the edge of the picture from a facing page"))
                assertTrue(system, system.contains("whether the text seems to have recognition errors or missing words"))
                assertFalse(system, system.contains("fix obvious recognition errors"))
            }
            textQuestions(onDevice).forEach { assertTrue(it.system, it.system.contains("all of the text in the scan")) }
            imageQuestions(onDevice).forEach { assertTrue(it.system, it.system.contains("all of the text in the photo")) }
        }
    }

    @Test
    fun `questions about the text are answered in its own words, not summarised`() {
        // The user, 2026-10-28: quote the original over summarising, follow-ups too.
        for (onDevice in listOf(false, true)) {
            textQuestions(onDevice).forEach {
                assertTrue(it.system, it.system.contains("answer with the text's own words"))
                assertTrue(it.system, it.system.contains("rather than summarising or rewording it"))
                // The last thing the model reads before it answers.
                assertTrue(it.user, it.user.contains("Answer with the text's own words. If I ask you to read it, read all of it as it is."))
                assertFalse(it.user, it.user.contains("concisely"))
            }
            imageQuestions(onDevice).forEach {
                assertTrue(it.system, it.system.contains("quote its own words rather than summarising them"))
            }
        }
    }

    @Test
    fun `a reading starts with the text, in paragraphs, for TalkBack`() {
        for (onDevice in listOf(false, true)) {
            (textQuestions(onDevice) + imageQuestions(onDevice)).forEach {
                val text = it.system + it.user
                assertTrue(text, text.contains("Start straight with the text itself, with no opening sentence"))
                assertTrue(text, text.contains("short paragraphs at the text's own paragraph breaks"))
                assertTrue(text, text.contains("lines that end only because the page is narrow are joined"))
                assertTrue(text, text.contains("screen reader"))
                // The phone round: "no headings" dropped the document's own titles.
                assertFalse(text, text.contains("no headings"))
                assertTrue(text, text.contains("Keep the text's own titles and headings, each as a plain line of its own"))
                assertTrue(text, text.contains("no markdown symbols (#, *, bullets) and no tables"))
                // A reading opened with "This is a scan of..." (2026-10-27).
                assertFalse(text, text.contains("start with what kind of document it is"))
            }
        }
    }

    @Test
    fun `the AI's own words stay brief, while text read out or quoted is never shortened`() {
        for (onDevice in listOf(false, true)) {
            (textQuestions(onDevice) + imageQuestions(onDevice)).forEach {
                // "Shorter is better, stop as soon as the question is answered"
                // made Gemma summarise and stop after a paragraph.
                assertFalse(it.system, it.system.contains(OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION))
                assertFalse(it.system, it.system.contains("concise"))
                assertTrue(it.system, it.system.contains("Keep your own words brief, about 80 words at most"))
                assertTrue(it.system, it.system.contains("Text you read out or quote is as long as it needs to be: never shorten it"))
                // The cap must not cut a page: a dense one is about 1,000 tokens.
                assertTrue("${it.maxTokens}", it.maxTokens >= 1000)
                assertTrue(it.maxTokens == ChatPrompts.CHAT_MAX_OUTPUT_TOKENS)
            }
        }
    }

    @Test
    fun `with no message from the user, the answer is short as before`() {
        // Capture's summary and a new photo's description answer no message.
        for (onDevice in listOf(false, true)) {
            listOf(ChatPrompts.documentSummary(page, onDevice), ChatPrompts.imageFirst("", onDevice)).forEach {
                val text = it.system + it.user
                assertTrue(text, it.system.contains(OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION))
                assertFalse(text, text.contains("If the user asks you to read the text"))
                assertTrue(it.maxTokens <= OpenAiTransport.LLM_MAX_OUTPUT_TOKENS)
            }
        }
    }

    @Test
    fun `text read out keeps its own language, on both routes`() {
        // OpenAI's app-language rule is for its own words, not the text it reads.
        for (onDevice in listOf(false, true)) {
            (textQuestions(onDevice) + imageQuestions(onDevice)).forEach {
                val text = it.system + it.user
                assertTrue(text, text.contains("Text you read out keeps its original language"))
            }
        }
    }
}
