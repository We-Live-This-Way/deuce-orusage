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
 * Stores API credentials encrypted at rest, one Keystore key per credential slot.
 *
 * The key material lives in the Android Keystore (hardware-backed where the device supports it)
 * and only a ciphertext blob goes into SharedPreferences. That means a rooted device, a bug report
 * dump or a backup of the prefs file does not hand over an account-wide credential.
 *
 * Slots are separate on purpose: signing out of OpenRouter must not delete the Mistral key, and a
 * Keystore entry that fails to decrypt should only cost you the credential it belonged to. Deleting
 * a shared key would take both down at once after a device transfer.
 *
 * Note on why not EncryptedSharedPreferences: androidx.security.crypto is deprecated, so this uses
 * the Keystore directly with AES/GCM - no deprecated dependency, same guarantee.
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("or_usage_secure", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SecureStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        private const val OR_ALIAS = "or_usage_management_key_v1"
        private const val OR_PREFS = "management_key_blob"

        private const val MISTRAL_ALIAS = "or_usage_mistral_key_v1"
        private const val MISTRAL_PREFS = "mistral_key_blob"
    }

    fun saveManagementKey(value: String) = save(OR_PREFS, OR_ALIAS, value)

    fun loadManagementKey(): String? = load(OR_PREFS, OR_ALIAS)

    fun saveMistralKey(value: String) = save(MISTRAL_PREFS, MISTRAL_ALIAS, value)

    fun loadMistralKey(): String? = load(MISTRAL_PREFS, MISTRAL_ALIAS)

    /** Kept for the sign-out path: only the OpenRouter credential is removed. */
    fun clear() = clearSlot(OR_PREFS, OR_ALIAS)

    fun clearMistral() = clearSlot(MISTRAL_PREFS, MISTRAL_ALIAS)

    private fun save(prefsKey: String, alias: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            clearSlot(prefsKey, alias)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(prefsKey, blob).apply()
    }

    private fun load(prefsKey: String, alias: String): String? {
        val blob = prefs.getString(prefsKey, null) ?: return null
        return try {
            val parts = blob.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // Keystore key gone (app data restored to a new device, lock screen reset, etc).
            // Treat as "not connected" rather than crashing on launch.
            Log.w(TAG, "Stored credential could not be decrypted; clearing slot " + prefsKey)
            clearSlot(prefsKey, alias)
            null
        }
    }

    private fun clearSlot(prefsKey: String, alias: String) {
        prefs.edit().remove(prefsKey).apply()
        try {
            keyStore().deleteEntry(alias)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete keystore entry " + alias, e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(alias: String): SecretKey {
        val ks = keyStore()
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
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
