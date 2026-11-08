package com.example.sightbuddy.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 of downloaded model files, read in pieces: the Gemma model is 2.6 GB
 * and never goes into memory whole. The run-time counterpart of the sherpa-onnx
 * check at build time (app/build.gradle.kts): a model replaced at its source,
 * or changed on the way, is never loaded.
 *
 * Blocking, and slow for a large file: never on the main thread.
 */
object ModelChecksums {

    /** Lower-case hex SHA-256 of what [input] holds, read to its end (not closed). */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }
}
