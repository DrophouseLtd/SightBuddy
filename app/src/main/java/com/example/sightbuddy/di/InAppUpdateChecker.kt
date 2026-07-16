package com.example.sightbuddy.di

import android.app.Activity

/**
 * Checks for available app updates on the Play Store.
 *
 * - **dev** → no-op (not distributed via Play)
 * - **prod** → uses Google Play In-App Updates (flexible flow)
 */
interface InAppUpdateChecker {
    fun checkForUpdate(activity: Activity)
}
