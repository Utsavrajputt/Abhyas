package com.invictus.abhyas.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the user's own Gemini API key, AES-GCM encrypted with a key that lives in the Android Keystore
 * (it never leaves secure hardware). The file is excluded from backup and device transfer, because the
 * Keystore key would not come along and the ciphertext would be useless on another device.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _present = MutableStateFlow(prefs.contains(ENTRY))

    /** True while a key is saved. The key itself is never exposed through this flow. */
    val present: StateFlow<Boolean> = _present

    /** Decrypts the key, or returns null (and forgets the entry) if it cannot be read. */
    @Synchronized
    fun get(): String? {
        val stored = prefs.getString(ENTRY, null) ?: return null
        return try {
            decrypt(stored)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun set(plain: String) {
        prefs.edit().putString(ENTRY, encrypt(plain.trim())).apply()
        _present.value = true
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(ENTRY).apply()
        _present.value = false
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        /** Keep in sync with res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val FILE = "ai_secure"
        const val ENTRY = "gemini_key"
        const val ALIAS = "abhyas_ai_key"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
