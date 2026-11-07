package com.example.sightbuddy.core.language

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import java.io.Closeable

/**
 * [LanguageIdentifier] by ML Kit Language Identification (issue #26): the
 * bundled model, on the phone and offline. Answers on the main thread, with
 * every language at least 1% likely; [TextLanguage.choose] decides what the
 * guesses are worth. A failure is an empty answer: the app's language.
 */
class MlKitLanguageIdentifier : LanguageIdentifier, Closeable {
    private val clientOnce = lazy { LanguageIdentification.getClient() }
    private val client by clientOnce

    override fun identify(text: String, onResult: (List<LanguageGuess>) -> Unit) {
        try {
            client.identifyPossibleLanguages(text)
                .addOnSuccessListener { found ->
                    onResult(found.map { LanguageGuess(it.languageTag, it.confidence) })
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Could not identify the language", e)
                    onResult(emptyList())
                }
        } catch (e: RuntimeException) {
            // Closed, or the model could not load.
            Log.w(TAG, "Could not identify the language", e)
            onResult(emptyList())
        }
    }

    /** One identification, its answer dropped: the model is loaded and ready. */
    override fun warmUp() {
        identify(WARM_UP_TEXT) { }
    }

    override fun close() {
        if (clientOnce.isInitialized()) client.close()
    }

    private companion object {
        const val TAG = "LanguageId"
        const val WARM_UP_TEXT = "Sight Buddy is ready to read the text aloud."
    }
}
