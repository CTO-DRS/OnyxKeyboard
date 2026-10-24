package com.onyx.keyboard.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed local encryption (AES-256/GCM, Android Keystore).
 * Used for clipboard history and other sensitive at-rest data.
 * Nothing is ever transmitted — the key never leaves the secure enclave.
 */
object OnyxCrypto {

    private const val KEY_ALIAS = "onyx_master_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    val isAvailable: Boolean by lazy { runCatching { obtainKey() }.isSuccess }

    private fun obtainKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Encrypts [plain] → Base64(iv ‖ ciphertext). Falls back to plain text on failure. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + ct, Base64.NO_WRAP)
        }.getOrElse { plain }
    }

    /** Decrypts Base64(iv ‖ ciphertext) → plain. Returns "" on failure. */
    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            if (raw.size <= IV_SIZE) return ""
            val iv = raw.copyOfRange(0, IV_SIZE)
            val ct = raw.copyOfRange(IV_SIZE, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrElse { "" }
    }
}
