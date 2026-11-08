package com.example.sightbuddy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.intl.LocaleList
import com.example.sightbuddy.core.language.LanguageSpan
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [text] with each of [spans] tagged with its language (issue #26). Compose
 * hands a span's locale to TalkBack as a LocaleSpan in the node's text, and
 * TalkBack switches its voice for it. Spans that do not fit the text are left
 * out. No spans: the text as it is.
 */
fun languageTagged(text: String, spans: List<LanguageSpan>): AnnotatedString {
    val fitting = spans.filter { it.start >= 0 && it.end <= text.length && it.start < it.end }
    if (fitting.isEmpty()) return AnnotatedString(text)
    return AnnotatedString(
        text,
        spanStyles = fitting.map { AnnotatedString.Range(SpanStyle(localeList = LocaleList(it.tag)), it.start, it.end) },
    )
}

/**
 * Finds the stretches of a text in another language with a voice
 * (`TTSService.languagesOf`), for the chat's messages; null finds none.
 */
val LocalTextLanguages = staticCompositionLocalOf<((String, (List<LanguageSpan>) -> Unit) -> Unit)?> { null }

/**
 * [text], tagged with its languages once they are found ([LocalTextLanguages]),
 * so TalkBack reads Arabic or Chinese in a chat message with its own voice
 * (issue #26). Untagged until then, and if finding them takes too long.
 */
@Composable
fun rememberLanguageTagged(text: String): AnnotatedString {
    val find = LocalTextLanguages.current
    // The spans with the text they were found for: a message's text changes
    // as an answer is written, and older spans must not land on newer text.
    val found by produceState<Pair<String, List<LanguageSpan>>?>(null, text, find) {
        val lookup = find ?: return@produceState
        val spans = withTimeoutOrNull(LOOKUP_WAIT_MS) {
            suspendCancellableCoroutine { cont -> lookup(text) { if (cont.isActive) cont.resume(it) } }
        }
        value = text to spans.orEmpty()
    }
    val spans = found?.takeIf { it.first == text }?.second.orEmpty()
    return remember(text, spans) { languageTagged(text, spans) }
}

/** How long a message waits for its languages before it stays untagged. */
private const val LOOKUP_WAIT_MS = 2_000L
