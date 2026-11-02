package dev.mooner.starlight.agent.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys and tokens, encrypted with a key that lives in the Android Keystore and never leaves it.
 *
 * Values restored from a backup on another device can't be decrypted there; they read as missing, so the user
 * signs in again instead of the app failing.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * What [get] returned for each name. Decrypting goes through the Keystore service, and screens ask whether
     * providers are set up as they draw; only this store writes the values, so the cache can't go stale.
     */
    private val cache = ConcurrentHashMap<String, Entry>()
    @Volatile
    private var secretKey: SecretKey? = null

    private class Entry(val value: String?)

    fun get(name: String): String? =
        cache.getOrPut(name) { Entry(decrypt(name)) }.value

    private fun decrypt(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val bytes = java.util.Base64.getDecoder().decode(stored)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
        }.getOrNull()
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit { remove(name) }
            cache[name] = Entry(null)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit { putString(name, java.util.Base64.getEncoder().encodeToString(encrypted)) }
        cache[name] = Entry(value)
    }

    fun has(name: String): Boolean =
        get(name) != null

    private fun key(): SecretKey =
        secretKey ?: synchronized(this) { secretKey ?: loadKey().also { secretKey = it } }

    private fun loadKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "agent_secrets"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "starlight_agent_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
