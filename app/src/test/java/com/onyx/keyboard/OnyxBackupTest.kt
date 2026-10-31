package com.onyx.keyboard

import com.onyx.keyboard.data.backup.OnyxBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F18 (PRD) — النسخ الاحتياطي المشفر: pure format + crypto round trips.
 * (java.util.Base64 keeps this a pure-JVM test — no Robolectric needed.)
 */
class OnyxBackupTest {

    private val payload = OnyxBackup.Payload(
        settings = listOf(
            OnyxBackup.SettingRow("haptics", "true"),
            OnyxBackup.SettingRow("recent_emoji", "a|b\nc"), // pipe + newline survive
        ),
        words = listOf(
            OnyxBackup.WordRow("مرحبا", "ar", 12, "learned"),
            OnyxBackup.WordRow("hello", "en", 3, "custom"),
        ),
        bigrams = listOf(OnyxBackup.BigramRow("good", "morning", "en", 5)),
        shortcuts = listOf(OnyxBackup.ShortcutRow("brb", "أعود حالاً")),
        themes = listOf(
            OnyxBackup.ThemeRow(
                name = "قالبي", surfaceTop = 0xFF0B0B12, surfaceBottom = 0xFF17121F,
                keyFill = 0xFF221C33, keyText = 0xFFF2EDFF, accent = 0xFFB37BFF,
                glow = 0xFF8A5CFF, particle = 0xFFB37BFF, keyRadius = 18,
                spaceLabel = 1, soundProfile = "wood", vibeProfile = "strong",
                bgUri = "/device/local/photo.jpg"
            )
        ),
    )

    @Test
    fun `payload round trip preserves every field`() {
        val text = OnyxBackup.encodePayload(payload)
        val back = OnyxBackup.decodePayload(text)
        assertNotNull(back)
        assertEquals(payload.settings, back!!.settings)
        assertEquals(payload.words, back.words)
        assertEquals(payload.bigrams, back.bigrams)
        assertEquals(payload.shortcuts, back.shortcuts)
        assertEquals(payload.themes.size, back.themes.size)
        val t = back.themes[0]
        assertEquals(0xFF0B0B12, t.surfaceTop)
        assertEquals(0xFFF2EDFF, t.keyText)
        assertEquals(18, t.keyRadius)
        assertEquals("wood", t.soundProfile)
        // Photos are device-local — they never travel inside the backup.
        assertEquals("", t.bgUri)
    }

    @Test
    fun `garbled payload is rejected`() {
        assertNull(OnyxBackup.decodePayload("NOT ONYX DATA"))
        assertNull(OnyxBackup.decodePayload(""))
    }

    @Test
    fun `crypto round trip with the right password`() {
        val text = OnyxBackup.encodePayload(payload)
        val blob = OnyxBackup.encrypt(text, "سِر1234".toCharArray())
        assertNotNull(blob)
        val restored = OnyxBackup.decrypt(blob!!, "سِر1234".toCharArray())
        assertEquals(text, restored)
    }

    @Test
    fun `wrong password returns null`() {
        val blob = OnyxBackup.encrypt("ONYXDATA1\nV|1", "correct-horse".toCharArray())!!
        assertNull(OnyxBackup.decrypt(blob, "wrong-pass".toCharArray()))
    }

    @Test
    fun `corrupt blob returns null instead of crashing`() {
        assertNull(OnyxBackup.decrypt("not-a-blob", "12345678".toCharArray()))
        assertNull(OnyxBackup.decrypt("", "12345678".toCharArray()))
        assertNull(OnyxBackup.decrypt("T2FF", "12345678".toCharArray()))
    }

    @Test
    fun `short password is rejected before any crypto runs`() {
        assertNull(OnyxBackup.encrypt("x", "abc".toCharArray()))
        assertTrue(!OnyxBackup.validatePassword("abc".toCharArray()))
        assertTrue(OnyxBackup.validatePassword("abcd".toCharArray()))
    }
}
