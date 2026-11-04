package com.lyricstage.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key-value store for the Spotify tokens: values are sealed with an AES key
 * that lives in the Android Keystore and never leaves it. The app excludes its data from
 * backups, and a lost key (after a restore or a Keystore reset) simply reads as unlinked.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    fun get(name: String): String? = runCatching {
        val sealed = prefs.getString(name, null) ?: return null
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit { remove(name) }
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit { putString(name, Base64.encodeToString(out, Base64.NO_WRAP)) }
        }
    }

    /** Plain values that need no secrecy (display name, sync progress). */
    fun getPlain(name: String): String? = prefs.getString("plain_$name", null)
    fun putPlain(name: String, value: String?) = prefs.edit { if (value == null) remove("plain_$name") else putString("plain_$name", value) }
    fun getLong(name: String): Long = prefs.getLong("plain_$name", 0L)
    fun putLong(name: String, value: Long) = prefs.edit { putLong("plain_$name", value) }

    fun clear() = prefs.edit { clear() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "lyricstage_spotify"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
