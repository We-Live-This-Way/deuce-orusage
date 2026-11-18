package com.welivethisway.orusage.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the management key encrypted at rest.
 *
 * The key material lives in the Android Keystore (hardware-backed where the device supports it)
 * and only a ciphertext blob goes into SharedPreferences. That means a rooted device, a bug report
 * dump or a backup of the prefs file does not hand over an account-wide credential.
 *
 * Note on why not EncryptedSharedPreferences: androidx.security.crypto is deprecated, so this uses
 * the Keystore directly with AES/GCM - no deprecated dependency, same guarantee.
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("or_usage_secure", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SecureStore"
        private const val ALIAS = "or_usage_management_key_v1"
        private const val PREFS_KEY = "management_key_blob"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }

    fun saveManagementKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(PREFS_KEY, blob).apply()
    }

    fun loadManagementKey(): String? {
        val blob = prefs.getString(PREFS_KEY, null) ?: return null
        return try {
            val parts = blob.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // Keystore key gone (app data restored to a new device, lock screen reset, etc).
            // Treat as "not signed in" rather than crashing on launch.
            Log.w(TAG, "Stored management key could not be decrypted; clearing it")
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove(PREFS_KEY).apply()
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete keystore entry", e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
