package com.example.sightbuddy.core.hints

import android.content.Context

/** Persists which automated [HintId] entries have already been shown. */
class HintStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasShown(id: HintId): Boolean = prefs.getBoolean(id.prefKey, false)

    fun markShown(id: HintId) {
        prefs.edit().putBoolean(id.prefKey, true).apply()
    }

    companion object {
        private const val PREFS = "sight_buddy_hints"
    }
}
