package com.example.sightbuddy.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Work that must finish even if the screen that started it goes: a model
 * download (hundreds of MB to 2.6 GB, kept alive by ModelDownloadService), a
 * model being deleted, a saved chat being named. Lives as long as the app's
 * process; one failure does not cancel the others.
 *
 * What the screen does with the result (saying it, loading a model into the
 * speech service) belongs to the screen's own scope, so it is dropped rather
 * than run against services the activity has already shut down.
 */
object AppWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Runs [block] to the end whatever happens to the caller, and returns its
     * result while the caller is still there to use it.
     */
    suspend fun <T> finish(block: suspend CoroutineScope.() -> T): T = scope.async(block = block).await()
}
