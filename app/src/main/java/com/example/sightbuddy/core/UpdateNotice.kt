package com.example.sightbuddy.core

import android.content.Context
import androidx.core.content.edit

/**
 * The pop-up after an update. The on-device AI's first load after an update
 * builds its GPU programs again and can take a few minutes; an answer waiting
 * for it seemed dead. A spoken note when an answer waited (2.4.2) needed rules
 * to keep it from ordinary starts; one pop-up per update says it plainly.
 */
object UpdateNotice {
    /**
     * Whether to show it: the version changed since it was last shown or
     * passed over, and the on-device AI is on the phone. [lastSeenVersion] is
     * null when none was stored: a fresh install (no pop-up), or an update from
     * a version before the pop-up existed ([updated]).
     */
    fun shouldShow(lastSeenVersion: Int?, currentVersion: Int, updated: Boolean, aiInstalled: Boolean): Boolean =
        aiInstalled && lastSeenVersion != currentVersion && (lastSeenVersion != null || updated)
}

/** The version the update pop-up was last shown or passed over in. */
class UpdateNoticeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastSeenVersion: Int?
        get() = if (prefs.contains(KEY_VERSION)) prefs.getInt(KEY_VERSION, 0) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_VERSION) else putInt(KEY_VERSION, value) }

    /** True when the app was updated since it was installed, not installed fresh. */
    val updated: Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime != info.firstInstallTime
    }.getOrDefault(false)

    companion object {
        private const val PREFS = "sight_buddy_update"
        private const val KEY_VERSION = "last_seen_version_code"
    }
}
