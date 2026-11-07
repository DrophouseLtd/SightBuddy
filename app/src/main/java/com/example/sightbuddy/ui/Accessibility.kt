package com.example.sightbuddy.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.example.sightbuddy.core.ScreenReaderSpeech

/**
 * The spoken name of a clickable control, and nothing else.
 *
 * Plain `semantics { contentDescription = … }` on a clickable merges in the text
 * drawn inside it, so TalkBack read both: "Open settings Settings". Clearing the
 * children keeps only [label]. The click action and enabled state come from the
 * `clickable` earlier in the chain, which this does not touch, so it must be
 * placed after it.
 */
fun Modifier.buttonSemantics(label: String): Modifier =
    clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
    }

/**
 * Names the dialog window this is called in. A dialog window has no title of
 * its own, so TalkBack opened every pop-up by announcing the app name, "Sight
 * Buddy", before anything in it. Call first thing inside a Dialog's content.
 */
@Composable
fun DialogWindowTitle(title: String) {
    val window = (LocalView.current.parent as?
        DialogWindowProvider)?.window
    SideEffect { window?.setTitle(title) }
}

/**
 * Moves the screen reader's own cursor (accessibility focus) to the element
 * tagged [tag] in this Compose view, as Android lets an app do for its own
 * elements; TalkBack reads it there. Input focus does not do this: TalkBack
 * follows it only to editable nodes. False when there is no such element, no
 * screen reader, or the element holds the focus already.
 */
fun View.moveScreenReaderFocusTo(tag: String): Boolean {
    val root = this as? RootForTest ?: return false
    return runCatching {
        val node = root.semanticsOwner.rootSemanticsNode.findTagged(tag) ?: return false
        val provider = accessibilityNodeProvider ?: return false
        provider.performAction(node.id, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
    }.getOrDefault(false)
}

private fun SemanticsNode.findTagged(tag: String): SemanticsNode? {
    if (config.getOrNull(SemanticsProperties.TestTag) == tag) return this
    children.forEach { child -> child.findTagged(tag)?.let { return it } }
    return null
}

/** True while a touch-exploring screen reader such as TalkBack is on. */
fun isScreenReaderOn(context: Context): Boolean =
    (context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager)?.isTouchExplorationEnabled == true

/**
 * Where the app's speech reaches the screen reader (issue #15): two places
 * of 1 dp that draw nothing, each holding at most one line as a live-region
 * node, so no visible text is needed for it. [lines] come from
 * [ScreenReaderSpeech], which takes turns between the two places.
 *
 * Each line is a node of its own, keyed by its serial (issue #24): TalkBack
 * queues a live-region node that appears behind what it is saying, while a
 * node whose text changes is a "content change", and a newer content change
 * drops an older one TalkBack has queued but not begun (TalkBack's source,
 * EventTypeWindowContentChangedFeedbackRule and SpeechControllerImpl). The
 * old two fixed nodes worked only while lines were handed over one at a
 * time. An urgent line is assertive (cuts what TalkBack is saying), the rest
 * polite (said after it); a line leaving says nothing.
 *
 * A line with stretches in another language (issue #26) is the node's text,
 * tagged with them ([languageTagged]), so TalkBack switches voice for them;
 * the others stay its content description, as they always were.
 *
 * Draw it last on the screen, at an edge: a node under another one is left
 * out of what TalkBack is told, and the two sit side by side for that reason.
 * No `announceForAccessibility`: deprecated in Android 16, tried and removed.
 */
@Composable
fun ScreenReaderLines(lines: List<ScreenReaderSpeech.Line?>, modifier: Modifier = Modifier) {
    Row(modifier) {
        lines.forEach { line ->
            Box(Modifier.size(1.dp)) {
                if (line != null) {
                    key(line.serial) {
                        Box(
                            Modifier
                                .size(1.dp)
                                .semantics {
                                    liveRegion = if (line.urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite
                                    if (line.languages.isEmpty()) {
                                        contentDescription = line.text
                                    } else {
                                        text = languageTagged(line.text, line.languages)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * [isScreenReaderOn], kept current: TalkBack is often switched on or off with
 * the app open (the volume-key shortcut), and what a long press does follows it.
 */
@Composable
fun rememberScreenReaderOn(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(isScreenReaderOn(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        on = isScreenReaderOn(context)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}
