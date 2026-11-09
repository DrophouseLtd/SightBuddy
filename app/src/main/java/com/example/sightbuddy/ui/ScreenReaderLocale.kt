package com.example.sightbuddy.ui

import android.content.res.Resources
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LocaleSpan
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.example.sightbuddy.core.ScreenReaderLanguage
import java.util.Locale

/**
 * Tells the screen reader which language this window's words are in (issue
 * #28). Call once in each window's content: the main screen, each dialog
 * ([DialogWindowTitle] does), each menu. When the app's language differs from
 * the phone's, every node's text and description reach TalkBack tagged with
 * the app's locale, so it reads Finnish with a Finnish voice; stretches tagged
 * with another language keep it (issue #26).
 */
@Composable
fun AppLanguageForScreenReader() {
    val view = LocalView.current
    val app = LocalConfiguration.current.locales[0]
    DisposableEffect(view, app) {
        val phone = Resources.getSystem().configuration.locales[0]
        ScreenReaderLocale.install(view, app.takeIf { ScreenReaderLanguage.wanted(it.language, phone.language) })
        onDispose { }
    }
}

/** [text] with the stretches not in a language of their own tagged [locale]; as it is when null. */
fun CharSequence.taggedWith(locale: Locale?): CharSequence {
    if (locale == null || isEmpty()) return this
    val spannable = SpannableString(this)
    val tagged = spannable.getSpans(0, length, LocaleSpan::class.java)
        .map { spannable.getSpanStart(it) until spannable.getSpanEnd(it) }
    val untagged = ScreenReaderLanguage.untagged(length, tagged)
    if (untagged.isEmpty()) return this
    untagged.forEach { spannable.setSpan(LocaleSpan(locale), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    return spannable
}

/**
 * The hook: Compose answers TalkBack through an accessibility node provider
 * on its view, reached through the view's accessibility delegate. This wraps
 * both, passing everything through and tagging each node on its way out.
 */
private object ScreenReaderLocale {

    fun install(view: View, locale: Locale?) {
        val current = view.accessibilityDelegate
        if (current is TaggingDelegate) {
            current.locale = locale
        } else if (locale != null) {
            view.accessibilityDelegate = TaggingDelegate(current ?: View.AccessibilityDelegate(), locale)
        }
    }

    fun tag(info: AccessibilityNodeInfo?, locale: Locale?): AccessibilityNodeInfo? {
        if (info == null || locale == null) return info
        info.text?.let { info.text = it.taggedWith(locale) }
        info.contentDescription?.let { info.contentDescription = it.taggedWith(locale) }
        info.stateDescription?.let { info.stateDescription = it.taggedWith(locale) }
        info.hintText?.let { info.hintText = it.taggedWith(locale) }
        info.paneTitle?.let { info.paneTitle = it.taggedWith(locale) }
        return info
    }

    private class TaggingDelegate(
        private val inner: View.AccessibilityDelegate,
        @Volatile var locale: Locale?,
    ) : View.AccessibilityDelegate() {
        private var wrapped: Pair<AccessibilityNodeProvider, AccessibilityNodeProvider>? = null

        override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? {
            val provider = inner.getAccessibilityNodeProvider(host) ?: return null
            wrapped?.let { (from, to) -> if (from === provider) return to }
            return TaggingProvider(provider).also { wrapped = provider to it }
        }

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            inner.onInitializeAccessibilityNodeInfo(host, info)
            tag(info, locale)
        }

        override fun sendAccessibilityEvent(host: View, eventType: Int) = inner.sendAccessibilityEvent(host, eventType)
        override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) =
            inner.sendAccessibilityEventUnchecked(host, event)
        override fun dispatchPopulateAccessibilityEvent(host: View, event: AccessibilityEvent): Boolean =
            inner.dispatchPopulateAccessibilityEvent(host, event)
        override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) =
            inner.onPopulateAccessibilityEvent(host, event)
        override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) =
            inner.onInitializeAccessibilityEvent(host, event)
        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean =
            inner.onRequestSendAccessibilityEvent(host, child, event)
        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean =
            inner.performAccessibilityAction(host, action, args)
        override fun addExtraDataToAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfo,
            extraDataKey: String,
            arguments: Bundle?,
        ) = inner.addExtraDataToAccessibilityNodeInfo(host, info, extraDataKey, arguments)

        private inner class TaggingProvider(private val provider: AccessibilityNodeProvider) : AccessibilityNodeProvider() {
            override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? =
                tag(provider.createAccessibilityNodeInfo(virtualViewId), locale)

            override fun findAccessibilityNodeInfosByText(text: String, virtualViewId: Int): MutableList<AccessibilityNodeInfo>? =
                provider.findAccessibilityNodeInfosByText(text, virtualViewId)?.onEach { tag(it, locale) }

            override fun findFocus(focus: Int): AccessibilityNodeInfo? = tag(provider.findFocus(focus), locale)

            override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean =
                provider.performAction(virtualViewId, action, arguments)

            override fun addExtraDataToAccessibilityNodeInfo(
                virtualViewId: Int,
                info: AccessibilityNodeInfo,
                extraDataKey: String,
                arguments: Bundle?,
            ) = provider.addExtraDataToAccessibilityNodeInfo(virtualViewId, info, extraDataKey, arguments)
        }
    }
}
