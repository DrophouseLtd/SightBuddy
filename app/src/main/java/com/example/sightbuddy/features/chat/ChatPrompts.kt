package com.example.sightbuddy.features.chat

import com.example.sightbuddy.core.OpenAiTransport

/**
 * What Text chat and Image chat ask the AI, as text: the system message, the
 * user's turn and how long the reply may be. The view models put it into the
 * request; this part is plain Kotlin, so a test can read what each case asks.
 *
 * [onDevice] in each builder: the answer comes from Gemma, not OpenAI
 * ([OpenAiTransport.answersOnDevice]). The two differ only in the rule for the
 * reply's language; see [languageRule].
 */
object ChatPrompts {

    /** One request's text, and how long and how free its reply may be. */
    data class Prompt(
        val system: String,
        val user: String,
        val maxTokens: Int = OpenAiTransport.LLM_MAX_OUTPUT_TOKENS,
        val temperature: Double = 0.7,
    )

    /**
     * Gemma's rule for the reply's language (issue #20), in place of the app's
     * language: it answers in the language it is asked in, and text it writes
     * out (Arabic, say) stays as it is.
     */
    const val ON_DEVICE_LANGUAGE_INSTRUCTION =
        " Reply in the language of the user's question. Text you quote or write out stays in its " +
            "original language, unless the user asks for a translation."

    /**
     * The rule for the reply's language on this route. OpenAI keeps the app's
     * language, so a Finnish user is answered in Finnish whatever they say.
     * [reading]: the request answers the user's message, which may ask for the
     * text to be read out or quoted, so on OpenAI only the AI's own words
     * follow the app language and the text keeps its own (issue #13); Gemma's
     * rule says so already.
     */
    fun languageRule(onDevice: Boolean, reading: Boolean = false): String = when {
        onDevice -> ON_DEVICE_LANGUAGE_INSTRUCTION
        reading -> OpenAiTransport.readingLanguageInstruction()
        else -> OpenAiTransport.replyLanguageInstruction()
    }

    // Reading (issue #13). The skills (Look for, Read in full, Read from image)
    // were dropped on 2026-10-28: Gemma did not follow them, summarising
    // instead. What the user's message may ask is now said once, plainly, in
    // every answer to it: read the text in full as it is, and answer from the
    // text's own words. "Read original" stays the app's own ([ChatKeywords]).

    /**
     * Most an answer to the user's message may run to, on both routes: it may
     * be a page read out (a dense one is about 1,000 tokens). Ordinary answers
     * are kept short by [ANSWER_LENGTH], not by the cap; a cap is only a
     * ceiling (nothing unused is paid for or waited on). On the phone, Gemma's
     * window is shared with the prompt, and
     * [com.example.sightbuddy.core.llm.ContextBudget] lowers the cap to what is left.
     */
    const val CHAT_MAX_OUTPUT_TOKENS = 1200

    /**
     * How long an answer to the user's message is, in place of
     * [OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION]: its "shorter is better, stop
     * as soon as the question is answered" outweighed the reading and made
     * Gemma summarise and stop after a paragraph (phone rounds to 2026-10-28).
     */
    private const val ANSWER_LENGTH =
        "Keep your own words brief, about 80 words at most. Text you read out or quote is as " +
            "long as it needs to be: never shorten it."

    /**
     * Reading the text out, for the scan or the photo ([where]: "in the scan",
     * "in the photo"). The text as it is (decided 2026-10-27): correcting and
     * choosing "the complete piece" made the AI edit and drop text.
     */
    private fun readingRule(where: String) =
        "If the user asks you to read the text, read out all of the text $where, from its " +
            "very first line to its last, as it is: do not summarise, reword or correct it, and " +
            "keep the text at the top that continues from a previous page or comes before the " +
            "first heading. " + AS_IS + " " + READING_LAYOUT

    /**
     * What every reading keeps and how it ends. Order is the one thing put
     * right (decided 2026-10-28): the scan can mix words from neighbouring
     * lines or columns, and that is fixed only when the original is clear.
     */
    private const val AS_IS =
        "Keep the order of the words and lines, except where the scan has mixed them up " +
            "and you are at least 90% sure of the original order: then put them back in " +
            "that order, changing nothing else. " +
            // Phone round, 2026-10-28: half lines cut off from the facing page were read out.
            "Leave out only fragments too garbled to read, such as lines cut off at the edge " +
            "of the picture from a facing page or a neighbouring column. " +
            "End by saying in one short sentence whether the text seems to have recognition " +
            "errors or missing words."

    /** How a reading is set out, for a screen reader. */
    private const val READING_LAYOUT =
        // Phone round, 2026-10-27: "line breaks" made every printed line a
        // paragraph, and answers opened with "This is a scan of...".
        "Start straight with the text itself, with no opening sentence about it. Set it out in " +
            "short paragraphs at the text's own paragraph breaks, easy to listen to with a " +
            "screen reader: lines that end only because the page is narrow are joined, and " +
            "words split by hyphenation are rejoined. " +
            // The phone round: "no headings" dropped the document's own titles.
            "Keep the text's own titles and headings, each as a plain line of its own; " +
            "no markdown symbols (#, *, bullets) and no tables. " +
            "Text you read out keeps its original language, unless the user asks for a translation."

    /**
     * Any other question about the text, follow-ups too: the text's own words,
     * quoted, rather than a summary of them (decided 2026-10-28).
     */
    private const val QUOTE_RULE =
        "When the user asks about the text or something in it, answer with the text's own " +
            "words: quote the part that answers the question in full, rather than summarising " +
            "or rewording it."

    /** All of the above, for Text chat's answers to the user's message. */
    private val TEXT_READING: String =
        readingRule("in the scan") + " " + QUOTE_RULE + " " + ANSWER_LENGTH

    /** The same, for the photo's text in Image chat. */
    private val IMAGE_READING: String =
        readingRule("in the photo") + " " +
            "When the user asks about text in the photo, quote its own words rather than " +
            "summarising them. " + ANSWER_LENGTH

    /**
     * The user's turn ends with this, as the last thing a small model reads
     * before it answers: "answer clearly and concisely" here had the last word.
     */
    private const val READ_OR_QUOTE =
        "Answer with the text's own words. If I ask you to read it, read all of it as it is."

    // Text chat

    /** [reading]: the request answers the user's message (see [TEXT_READING]). */
    private fun documentSystem(onDevice: Boolean, extra: String = "", reading: Boolean = false): String =
        "You are a helpful assistant for a visually impaired user. " +
            "Be clear and conversational. " +
            extra +
            OpenAiTransport.LLM_USER_SAFETY_INSTRUCTION + " " +
            (if (reading) TEXT_READING else OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION) +
            // The prompt is English whatever the app language; the answer follows the route's rule.
            languageRule(onDevice, reading)

    private const val DESCRIBE_DOCUMENT = "When describing documents, start with what kind of document it is. "

    /**
     * Issue #17: the first answer after a fresh scan ends by saying whether the
     * page seems whole. The camera may have missed an edge, and only the AI
     * reads the text closely enough to notice. Follow-ups are not asked.
     */
    // Phone round 4: "whether it seems complete" misled, as the AI cannot know
    // that; what it can see is recognition errors and missing words.
    const val COMPLETENESS_INSTRUCTION =
        " Unless you have already said so, end your answer with one short sentence saying whether " +
            "the text seems to have recognition errors or missing words, and where."

    /** The first answer after a scan with no question: what the page is, in short. */
    fun documentSummary(text: String, onDevice: Boolean) = Prompt(
        system = documentSystem(onDevice, DESCRIBE_DOCUMENT),
        user = "I just scanned a document with my camera. " +
            "Please read and summarize the following text clearly and concisely." +
            COMPLETENESS_INSTRUCTION +
            // English prompt, so it carries the language requirement.
            languageRule(onDevice) + " " +
            "For example, say 'Here's a bill from the gas company, would you like to know the payment details?':\n\n$text",
    )

    /** The first answer after a scan made for [question]. */
    fun documentQuestion(text: String, question: String, onDevice: Boolean) = Prompt(
        // Not DESCRIBE_DOCUMENT: a reading opened with "This is a scan of..." (2026-10-27).
        system = documentSystem(onDevice, reading = true),
        user = "Here is text extracted from a document I scanned:\n\n" +
            "\"$text\"\n\n" +
            "My question: $question\n\n" +
            READ_OR_QUOTE +
            COMPLETENESS_INSTRUCTION +
            languageRule(onDevice, reading = true),
        maxTokens = CHAT_MAX_OUTPUT_TOKENS,
    )

    /**
     * A question about a page already scanned; [history] is the conversation
     * so far. With none, this is the first AI answer after the scan (the page
     * was read raw, Auto text summarization being off, the default), so it
     * ends on whether the text seems to have recognition errors or missing words (#17), as a summary would.
     */
    fun documentFollowUp(text: String, history: String, question: String, onDevice: Boolean) = Prompt(
        system = documentSystem(onDevice, reading = true),
        user = "Here is text from a document I scanned:\n\n" +
            "\"$text\"\n\n" +
            "Previous conversation:\n$history\n\n" +
            "My new question: $question\n\n" +
            READ_OR_QUOTE +
            (if (history.isBlank()) COMPLETENESS_INSTRUCTION else "") +
            languageRule(onDevice, reading = true),
        maxTokens = CHAT_MAX_OUTPUT_TOKENS,
    )

    // Image chat

    /**
     * Image chat's system message, for every turn. [reading]: the turn answers
     * the user's message, which may ask for the photo's text (see [IMAGE_READING]).
     */
    fun imageSystem(onDevice: Boolean, reading: Boolean = false): String =
        "You are a helpful visual assistant for a visually impaired user. " +
            "Be clear and conversational. " +
            "Describe what matters most first. Mention colours, weather, distances, and potential hazards when relevant. " +
            // The user speaks their questions; speech-to-text may garble them. Guard
            // against the model's canned "I can't access images" refusal — the photo
            // IS attached to every request.
            "The user's photo is attached to every message, so you can always see it. " +
            "Never say you cannot access, view, or see images. " +
            "Questions are transcribed from speech and may contain recognition errors. " +
            "Interpret them charitably as being about the photo. If a question mentions " +
            "something that is not in the image, do not just say it is absent — describe " +
            "what you DO see and its relevant details (such as colours), since the wording " +
            "was probably misheard. " +
            // Follow-ups were being answered with another tour of the scene instead of
            // the thing that was actually asked.
            "When the user asks a question, answer that question first and directly. " +
            "Do not re-describe the scene unless the question calls for it. " +
            OpenAiTransport.LLM_USER_SAFETY_INSTRUCTION + " " +
            // A camera snapshot must never be used as a mobility aid: the scene is
            // stale by the time the answer is spoken, and errors are dangerous.
            "Do not provide traffic, navigation, road-crossing, or mobility routing guidance. " +
            "If the user asks about those topics, politely decline and suggest dedicated mobility aids or a sighted companion. " +
            (if (reading) IMAGE_READING else OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION) +
            // The prompt is English whatever the app language; the answer follows the route's rule.
            languageRule(onDevice, reading)

    /**
     * The user's turn with a new photo: a description, or [question] when one
     * was asked, which comes with the reading rules (see [imageFirst]).
     */
    fun imageFirstTurn(question: String, onDevice: Boolean): String =
        if (question.isBlank()) {
            "Describe what you see in this image clearly and concisely for a visually impaired user. " +
                "Start with the overall scene, then mention notable details. " +
                "Keep it conversational — for example: 'You're looking at a park with a few people walking. " +
                "The sky looks overcast, it might rain soon.'" +
                // This turn is English, example and all, so it has to carry
                // the language requirement or the answer follows it.
                languageRule(onDevice)
        } else {
            question + languageRule(onDevice, reading = true)
        }

    /**
     * A new photo's request: its description, short as before, or the answer
     * to [question], which may ask for the photo's text read out.
     */
    fun imageFirst(question: String, onDevice: Boolean) =
        if (question.isBlank()) {
            Prompt(system = imageSystem(onDevice), user = imageFirstTurn(question, onDevice))
        } else {
            Prompt(
                system = imageSystem(onDevice, reading = true),
                user = imageFirstTurn(question, onDevice),
                maxTokens = CHAT_MAX_OUTPUT_TOKENS,
            )
        }

    /** A later question about the same photo. */
    fun imageFollowUp(question: String, onDevice: Boolean) = Prompt(
        system = imageSystem(onDevice, reading = true),
        user = question,
        maxTokens = CHAT_MAX_OUTPUT_TOKENS,
    )
}
