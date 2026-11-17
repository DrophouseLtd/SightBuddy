package com.example.sightbuddy.core.llm

/** Must be accessed while the process-wide inference permit is held. */
internal class ProcessModelOwner {
    private var owner: Any? = null
    private var close: (() -> Unit)? = null

    /** Close the previous owner BEFORE callers construct another native engine. */
    fun replace(next: Any, release: () -> Unit) {
        if (owner === next) return
        close?.invoke()
        owner = next
        close = release
    }

    /** A delayed release of a former Activity must not touch its successor. */
    fun release(expected: Any): Boolean {
        if (owner !== expected) return false
        close?.invoke()
        owner = null
        close = null
        return true
    }
}
