package com.example.sightbuddy.core

import android.content.Context
import java.util.UUID

/**
 * Stable random id for this app install (survives process death; cleared on uninstall).
 * Used as [install_id] for Supabase Edge quota.
 */
class InstallIdStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val existing = prefs.getString(KEY_INSTALL_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, fresh).apply()
        return fresh
    }

    companion object {
        private const val PREFS = "sight_buddy_device"
        private const val KEY_INSTALL_ID = "install_id"
    }
}
