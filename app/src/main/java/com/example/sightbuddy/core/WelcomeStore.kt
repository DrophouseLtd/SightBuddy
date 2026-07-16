package com.example.sightbuddy.core

import android.content.Context

/**
 * Persists whether the first-launch welcome overlay has been completed.
 */
class WelcomeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasCompletedWelcome(): Boolean =
        prefs.getBoolean(KEY_WELCOME_COMPLETED, false)

    fun markWelcomeCompleted() {
        prefs.edit().putBoolean(KEY_WELCOME_COMPLETED, true).apply()
    }

    companion object {
        private const val PREFS = "sight_buddy_welcome"
        private const val KEY_WELCOME_COMPLETED = "welcome_completed"
    }
}
