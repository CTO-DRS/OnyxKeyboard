package com.onyx.keyboard.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * F18 (PRD) — النسخ الاحتياطي المشفر بكلمة سر يملكها المستخدم.
 *
 * Exports settings + learned words + bigrams + shortcuts + custom themes to a
 * single portable text file, encrypted with PBKDF2-HMAC-SHA256 → AES-256/GCM.
 * NO account, NO cloud, NO device-bound Keystore — the password IS the key,
 * so the file restores on any device. Pure crypto + a pure line-based record
 * format (no org.json, and java.util.Base64 instead of android.util.Base64 —
 * API 26+) keep the whole module unit-testable on the plain JVM.
 *
 * File layout (Base64 of the binary blob):
 *   ONYXBP1 | salt(16) | iv(12) | AES-GCM(ciphertext)
 * Plaintext of the ciphertext is the record payload:
 *   ONYXDATA1
 *   S|key|value                — a settings row
 *   W|word|lang|freq|source    — a learned/custom word
 *   B|prev|next|lang|freq      — a bigram
 *   SC|trigger|expansion       — a smart shortcut
 *   T|theme fields…            — a CUSTOM theme (builtin presets never export)
 * Every field is UTF-8 URL-encoded, so pipes/newlines inside values survive.
 */
object OnyxBackup {

    const val BLOB_MAGIC = "ONYXBP1"
    const val PAYLOAD_MAGIC = "ONYXDATA1"
    const val FORMAT_VERSION = 1

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val TAG_BITS = 128
    private const val MIN_PASSWORD = 4

    // ------------------------------------------------------------------ model

    data class SettingRow(val key: String, val value: String)
    data class WordRow(
        val word: String,
        val lang: String,
        val freq: Int,
        val source: String,
    )
    data class BigramRow(val prev: String, val next: String, val lang: String, val freq: Int)
    data class ShortcutRow(val trigger: String, val expansion: String)
    data class ThemeRow(
        val name: String,
        val surfaceTop: Long,
        val surfaceBottom: Long,
        val keyFill: Long,
        val keyText: Long,
        val accent: Long,
        val glow: Long,
        val particle: Long,
        val keyRadius: Int,
        val spaceLabel: Int,
        val soundProfile: String,
        val vibeProfile: String,
        val bgUri: String,
    )

    data class Payload(
        val settings: List<SettingRow> = emptyList(),
        val words: List<WordRow> = emptyList(),
        val bigrams: List<BigramRow> = emptyList(),
        val shortcuts: List<ShortcutRow> = emptyList(),
        val themes: List<ThemeRow> = emptyList(),
    )

    // -------------------------------------------------------------- encoding

    /** Encodes the payload into the canonical plaintext record format. */
    fun encodePayload(p: Payload): String = buildString {
        appendLine(PAYLOAD_MAGIC)
        appendLine("V|$FORMAT_VERSION")
        p.settings.forEach {
            appendLine("S|${enc(it.key)}|${enc(it.value)}")
        }
        p.words.forEach {
            appendLine("W|${enc(it.word)}|${enc(it.lang)}|${it.freq}|${enc(it.source)}")
        }
        p.bigrams.forEach {
            appendLine("B|${enc(it.prev)}|${enc(it.next)}|${enc(it.lang)}|${it.freq}")
        }
        p.shortcuts.forEach {
            appendLine("SC|${enc(it.trigger)}|${enc(it.expansion)}")
        }
        p.themes.forEach {
            appendLine(
                "T|${enc(it.name)}|${it.surfaceTop}|${it.surfaceBottom}|${it.keyFill}|" +
                    "${it.keyText}|${it.accent}|${it.glow}|${it.particle}|${it.keyRadius}|" +
                    "${it.spaceLabel}|${enc(it.soundProfile)}|${enc(it.vibeProfile)}|${enc(it.bgUri)}"
            )
        }
    }.trimEnd('\n', '\r')

    /** Parses the record format back; unknown/garbled lines are skipped. */
    fun decodePayload(text: String): Payload? {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.firstOrNull() != PAYLOAD_MAGIC) return null
        val settings = mutableListOf<SettingRow>()
        val words = mutableListOf<WordRow>()
        val bigrams = mutableListOf<BigramRow>()
        val shortcuts = mutableListOf<ShortcutRow>()
        val themes = mutableListOf<ThemeRow>()
        for (line in lines.drop(1)) {
            val f = line.split('|')
            when (f.firstOrNull()) {
                "S" -> if (f.size == 3) settings += SettingRow(dec(f[1]), dec(f[2]))
                "W" -> if (f.size == 5) words += WordRow(
                    dec(f[1]), dec(f[2]), f[3].toIntOrNull() ?: 1, dec(f[4])
                )
                "B" -> if (f.size == 5) bigrams += BigramRow(
                    dec(f[1]), dec(f[2]), dec(f[3]), f[4].toIntOrNull() ?: 1
                )
                "SC" -> if (f.size == 3) shortcuts += ShortcutRow(dec(f[1]), dec(f[2]))
                "T" -> if (f.size == 14) {
                    val nums = f.drop(2).take(7).map { it.toLongOrNull() }
                    if (nums.all { it != null }) themes += ThemeRow(
                        name = dec(f[1]),
                        surfaceTop = nums[0]!!, surfaceBottom = nums[1]!!,
                        keyFill = nums[2]!!, keyText = nums[3]!!,
                        accent = nums[4]!!, glow = nums[5]!!, particle = nums[6]!!,
                        keyRadius = f[9].toIntOrNull() ?: 14,
                        spaceLabel = f[10].toIntOrNull() ?: 0,
                        soundProfile = dec(f[11]), vibeProfile = dec(f[12]),
                        bgUri = "", // photos are device-local files — never exported
                    )
                }
            }
        }
        return Payload(settings, words, bigrams, shortcuts, themes)
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    private fun dec(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault("")

    // -------------------------------------------------------------- password

    fun validatePassword(password: CharArray): Boolean =
        password.size >= MIN_PASSWORD

    // ---------------------------------------------------------------- crypto

    /**
     * Encrypts the payload text with a password → shareable Base64 blob.
     * Returns null when the password is too short.
     */
    fun encrypt(payloadText: String, password: CharArray): String? {
        if (!validatePassword(password)) return null
        return runCatching {
            val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(payloadText.toByteArray(Charsets.UTF_8))
            val blob = BLOB_MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + ct
            Base64.getEncoder().encodeToString(blob)
        }.getOrNull()
    }

    /**
     * Decrypts a backup blob with the password → payload text.
     * Returns null for a wrong password or any corruption (GCM tag check).
     */
    fun decrypt(blob: String, password: CharArray): String? {
        val raw = runCatching {
            // MIME decoder ignores line breaks/whitespace — same tolerance the
            // old android.util Base64(NO_WRAP or DEFAULT) decode provided.
            Base64.getMimeDecoder().decode(blob.trim())
        }.getOrNull() ?: return null
        val magic = BLOB_MAGIC.toByteArray(Charsets.US_ASCII)
        if (raw.size <= magic.size + SALT_SIZE + IV_SIZE) return null
        if (!raw.copyOfRange(0, magic.size).contentEquals(magic)) return null
        return runCatching {
            val salt = raw.copyOfRange(magic.size, magic.size + SALT_SIZE)
            val ivStart = magic.size + SALT_SIZE
            val iv = raw.copyOfRange(ivStart, ivStart + IV_SIZE)
            val ct = raw.copyOfRange(ivStart + IV_SIZE, raw.size)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull() // wrong password → AEADBadTagException → null
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
