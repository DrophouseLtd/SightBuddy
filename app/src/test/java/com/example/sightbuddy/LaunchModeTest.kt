package com.example.sightbuddy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Issue #10: a task an app shortcut started has the shortcut's intent at its
 * root. A later tap on the launcher icon is a different intent, and with the
 * standard launch mode Android starts a second MainActivity on top of the
 * first: a second camera, voice and controller, and Gemma loaded twice.
 * Single-top hands that tap to the activity already running (onNewIntent).
 */
class LaunchModeTest {

    @Test
    fun mainActivityIsSingleTop() {
        // Unit tests run in the app module's folder.
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(manifest.absolutePath, manifest.exists())
        val activities = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(manifest)
            .getElementsByTagName("activity")
        val main = (0 until activities.length)
            .map { activities.item(it) as Element }
            .single { it.getAttribute("android:name") == ".MainActivity" }
        assertEquals("singleTop", main.getAttribute("android:launchMode"))
    }
}
