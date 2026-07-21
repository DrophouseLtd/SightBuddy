package com.example.sightbuddy.core

/**
 * Crash diagnostics switch.
 *
 * - **dev** → no-op (Crashlytics is a prod-only dependency)
 * - **prod** → toggles Firebase Crashlytics collection
 *
 * Crash reporting is on by default under legitimate interest (keeping an
 * accessibility app stable), but users can turn it off in Settings → Privacy.
 * The preference is persisted, so the choice survives restarts.
 */
interface CrashReporter {
    fun setEnabled(enabled: Boolean)
}
