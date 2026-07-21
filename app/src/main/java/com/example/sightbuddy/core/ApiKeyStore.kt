package com.example.sightbuddy.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's own OpenAI API key (bring-your-own-key mode).
 *
 * The key never leaves the device except in Authorization headers sent
 * directly to api.openai.com. At rest it is encrypted with an AES/GCM key
 * held in the Android Keystore (non-exportable, hardware-backed where
 * available), so the preference file alone is not enough to recover it.
 */
class ApiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _keyPresent = MutableStateFlow(false)
    val keyPresent = _keyPresent.asStateFlow()

    init {
        _keyPresent.value = getKey().isNotBlank()
    }

    fun getKey(): String {
        val stored = prefs.getString(KEY_ENCRYPTED, null) ?: return ""
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, GCM_IV_BYTES)
            val cipherText = blob.copyOfRange(GCM_IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Could not decrypt stored API key", e)
            ""
        }
    }

    /** Blank input clears the key. */
    fun setKey(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_ENCRYPTED).apply()
            _keyPresent.value = false
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val blob = cipher.iv + cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_ENCRYPTED, Base64.encodeToString(blob, Base64.NO_WRAP))
                .apply()
            _keyPresent.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not encrypt API key", e)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "ApiKeyStore"
        private const val PREFS = "sight_buddy_api"
        private const val KEY_ENCRYPTED = "openai_api_key_enc"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sightbuddy_api_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
    }
}
