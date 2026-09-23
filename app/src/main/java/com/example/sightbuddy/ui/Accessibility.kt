package com.example.sightbuddy.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

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

/** True while a touch-exploring screen reader such as TalkBack is on. */
fun isScreenReaderOn(context: Context): Boolean =
    (context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager)?.isTouchExplorationEnabled == true
