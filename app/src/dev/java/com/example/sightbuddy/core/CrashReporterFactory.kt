package com.example.sightbuddy.core

/** dev flavor has no Crashlytics dependency — nothing to toggle. */
fun createCrashReporter(): CrashReporter = object : CrashReporter {
    override fun setEnabled(enabled: Boolean) = Unit
}
