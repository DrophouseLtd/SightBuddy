package com.example.sightbuddy.core

import java.io.File

/**
 * Model files already checked against their pinned SHA-256, remembered so a
 * file is hashed once and not at every launch (Gemma is 2.6 GB). Files that
 * came down before the app checked hashes are checked on their first use.
 *
 * A record holds the hash the file was checked against, its size and its
 * last-modified time. A file that has changed since, or a new pinned hash in
 * a later version, is hashed again. One small record per model file name, in
 * app-private storage ([dir]), apart from the models themselves.
 */
class VerifiedModels(
    private val dir: File,
    private val hash: (File) -> String = ModelChecksums::sha256,
) {
    /** True when [file], as it is now, was checked against [sha256] before. */
    fun isRemembered(file: File, sha256: String): Boolean {
        val record = recordOf(file)
        if (!file.isFile || !record.isFile) return false
        return runCatching { record.readText() }.getOrNull() == recordText(file, sha256)
    }

    /** Remembers [file], as it is now, as matching [sha256]: only after hashing it. */
    fun remember(file: File, sha256: String) {
        dir.mkdirs()
        recordOf(file).writeText(recordText(file, sha256))
    }

    fun forget(file: File) {
        recordOf(file).delete()
    }

    /** Hashes [file] now and compares, remembering nothing. Throws when it cannot be read. */
    fun matches(file: File, sha256: String): Boolean = hash(file) == sha256.lowercase()

    /**
     * True when [file] is what [sha256] was taken from: remembered, or hashed
     * now and then remembered. A file that does not match is deleted, and the
     * answer is false, as for a missing one. Blocking, and may read the whole
     * file: never on the main thread. Throws when the file cannot be read.
     */
    fun check(file: File, sha256: String): Boolean {
        if (!file.isFile) {
            forget(file)
            return false
        }
        if (isRemembered(file, sha256)) return true
        forget(file)
        if (hash(file) != sha256.lowercase()) {
            file.delete()
            return false
        }
        remember(file, sha256)
        return true
    }

    private fun recordOf(file: File) = File(dir, "${file.name}.sha256")

    private fun recordText(file: File, sha256: String) =
        "${sha256.lowercase()} ${file.length()} ${file.lastModified()}"

    companion object {
        /** The records' folder under the app's private files. */
        const val DIR = "verified-models"
    }
}
