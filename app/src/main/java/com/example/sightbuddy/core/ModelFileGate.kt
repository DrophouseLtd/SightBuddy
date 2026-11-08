package com.example.sightbuddy.core

import java.io.File
import java.io.IOException

/**
 * The two places a model file is let through, each against its pinned SHA-256:
 * into memory before a load ([forLoad]) and into place after a download
 * ([install]). Gemma and Whisper both go through here, so a file that has not
 * matched is never loaded and never installed.
 */
object ModelFileGate {

    sealed interface ForLoad {
        /**
         * Matched its SHA-256, now or before: the only file a model may be
         * loaded from. Its constructor is private, so the check below is the
         * only way to make one, and the loaders take nothing else.
         */
        class Verified private constructor(val file: File) : ForLoad {
            internal companion object {
                fun check(file: File, sha256: String, checks: VerifiedModels): ForLoad {
                    if (!file.isFile) return Missing
                    val ok = try {
                        checks.check(file, sha256)
                    } catch (e: IOException) {
                        return Unreadable(e)
                    }
                    return if (ok) Verified(file) else Damaged
                }
            }
        }

        /** Did not match: it has been deleted, and counts as missing from now on. */
        data object Damaged : ForLoad

        data object Missing : ForLoad

        /** Could not be read: not loaded, but kept (it may be fine once readable). */
        class Unreadable(val error: IOException) : ForLoad
    }

    /**
     * Whether [file] may be loaded. Hashed only when not checked before
     * ([VerifiedModels]); blocking, and slow the first time for a large file:
     * never on the main thread.
     */
    fun forLoad(file: File, sha256: String, checks: VerifiedModels): ForLoad =
        ForLoad.Verified.check(file, sha256, checks)

    enum class Install { INSTALLED, WRONG_FILE, NOT_MOVED }

    /**
     * Moves a whole download [part] to [target] only if it matches [sha256],
     * and remembers [target] as checked. A part that does not match is
     * deleted. Throws when [part] cannot be read: nothing is moved then.
     * Blocking: never on the main thread.
     */
    fun install(part: File, target: File, sha256: String, checks: VerifiedModels): Install {
        if (!checks.matches(part, sha256)) {
            part.delete()
            return Install.WRONG_FILE
        }
        if (!part.renameTo(target)) return Install.NOT_MOVED
        checks.remember(target, sha256)
        return Install.INSTALLED
    }
}
