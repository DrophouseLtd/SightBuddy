package com.example.sightbuddy.core.stt

import kotlinx.coroutines.Job

/** A job is not complete until its blocking native call has returned. */
internal fun retireEngineAfter(job: Job?, release: () -> Unit) {
    if (job == null) release() else job.invokeOnCompletion { release() }
}
