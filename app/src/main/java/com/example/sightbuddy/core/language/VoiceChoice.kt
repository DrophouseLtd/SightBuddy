package com.example.sightbuddy.core.language

/**
 * One of the phone's text-to-speech voices, as far as choosing one goes.
 * [installed]: its data is on the phone (not "not installed"). [needsNetwork]:
 * it speaks only online.
 */
data class VoiceInfo(val localeTag: String, val installed: Boolean, val needsNetwork: Boolean)

/**
 * Which voice says text in another language (issue #26). Only a language the
 * phone has a voice for is spoken, or tagged for TalkBack, in that language;
 * any other is said in the app's language, as before, and reported missing
 * (for the one-time hint, [MissingVoiceHint]).
 */
object VoiceChoice {
    /**
     * The locale to speak [tag]'s language with, or null when the phone has
     * no voice for it: an installed voice that works offline. The bare
     * language when the engine takes it ([available]), so the engine picks
     * its own usual voice for it (English is American, not Australian);
     * else that voice's own locale ("cmn-CN" for Chinese). An engine that
     * lists no voices ([voices] null) is asked about the language alone.
     */
    fun localeFor(tag: String, voices: Collection<VoiceInfo>?, available: (String) -> Boolean): String? {
        if (voices == null) return if (available(tag)) tag else null
        val language = TextLanguage.language(tag)
        val voice = voices
            .filter { it.installed && !it.needsNetwork && TextLanguage.language(it.localeTag) == language }
            .minByOrNull { it.localeTag }
            ?: return null
        return if (available(tag)) tag else voice.localeTag
    }

    /** [spans] with the voice's locale, and the languages with no voice. */
    data class Picked(val spans: List<LanguageSpan>, val missing: Set<String>)

    /** Keeps the [spans] whose language has a voice ([localeFor]), with its locale. */
    fun pick(spans: List<LanguageSpan>, localeFor: (String) -> String?): Picked {
        val missing = LinkedHashSet<String>()
        val kept = spans.mapNotNull { span ->
            val locale = localeFor(span.tag)
            if (locale == null) missing += span.tag
            locale?.let { span.copy(tag = it) }
        }
        return Picked(kept, missing)
    }

    /** A part of a text said with one voice: [tag]'s, or the app's when null. */
    data class Part(val start: Int, val end: Int, val tag: String?)

    /**
     * [text] cut into parts where the voice changes, covering all of it.
     * Spaces and line breaks between parts go with the part before, so two
     * paragraphs in one language with only a line break between are one part.
     */
    fun parts(text: String, spans: List<LanguageSpan>): List<Part> {
        val parts = ArrayList<Part>()
        fun add(start: Int, end: Int, tag: String?) {
            if (end <= start) return
            val last = parts.lastOrNull()
            val blank = text.substring(start, end).isBlank()
            when {
                last != null && (last.tag == tag || blank) -> parts[parts.size - 1] = last.copy(end = end)
                else -> parts += Part(start, end, tag)
            }
        }
        var at = 0
        spans.sortedBy { it.start }.forEach { span ->
            if (span.start < at) return@forEach
            add(at, span.start, null)
            add(span.start, span.end, span.tag)
            at = span.end
        }
        add(at, text.length, null)
        // Spaces before the first part go with it.
        if (parts.size > 1 && text.substring(parts[0].start, parts[0].end).isBlank()) {
            parts[1] = parts[1].copy(start = 0)
            parts.removeAt(0)
        }
        return parts.ifEmpty { listOf(Part(0, text.length, null)) }
    }
}
