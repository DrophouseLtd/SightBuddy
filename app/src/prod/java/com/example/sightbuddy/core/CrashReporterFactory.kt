package com.example.sightbuddy.core

import com.google.firebase.crashlytics.FirebaseCrashlytics

fun createCrashReporter(): CrashReporter = object : CrashReporter {
    override fun setEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
    }
}
