package com.example.sightbuddy.core.language

/** Retains missing-voice events until the controller attaches its listener. */
class MissingVoiceEvents {
    private val lock = Any()
    private val pending = linkedSetOf<String>()
    private var current: ((Set<String>) -> Unit)? = null

    var listener: ((Set<String>) -> Unit)?
        get() = synchronized(lock) { current }
        set(value) {
            val replay = synchronized(lock) {
                current = value
                if (value == null || pending.isEmpty()) emptySet() else pending.toSet().also { pending.clear() }
            }
            if (replay.isNotEmpty()) value?.invoke(replay)
        }

    fun report(languages: Set<String>) {
        if (languages.isEmpty()) return
        val target = synchronized(lock) {
            current.also { if (it == null) pending.addAll(languages) }
        }
        target?.invoke(languages)
    }
}
