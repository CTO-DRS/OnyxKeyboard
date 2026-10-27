package com.onyx.keyboard.keyboard

import android.util.Base64
import com.onyx.keyboard.data.db.ThemeEntity
import org.json.JSONObject

/**
 * Shareable theme codes — a custom look travels as ONE line of text that
 * survives any chat app:
 *
 *     ONYX1.<base64url(utf8(json))>
 *
 * The same string is ALSO what an exported .onyx-theme file contains, so a
 * friend can open the file in any text viewer, copy it, and import it from
 * the keyboard with one tap (no file-picker plumbing inside the IME).
 *
 * Encoded payload = the palette ONLY: name + the 7 colors. Row ids,
 * builtin flags and the private background-photo path never leave the
 * device. Decoding is defensive: prefix may appear anywhere in the pasted
 * text, unknown/garbled payloads yield null, names are capped and colors
 * are masked to 32 bits before anything touches Room.
 */
object OnyxThemeCodec {

    const val PREFIX = "ONYX1."
    private const val FORMAT = 1
    private const val MAX_NAME = 24

    fun encode(theme: ThemeEntity): String {
        val json = JSONObject().apply {
            put("v", FORMAT)
            put("name", theme.name)
            put("surfaceTop", hex(theme.surfaceTop))
            put("surfaceBottom", hex(theme.surfaceBottom))
            put("keyFill", hex(theme.keyFill))
            put("keyText", hex(theme.keyText))
            put("accent", hex(theme.accent))
            put("glow", hex(theme.glow))
            put("particle", hex(theme.particle))
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun decode(raw: String): ThemeEntity? = runCatching {
        // Tolerate forwards/wrappers: find the prefix anywhere in the text.
        val idx = raw.indexOf(PREFIX)
        if (idx < 0) return null
        val b64 = raw.substring(idx + PREFIX.length).trim().takeWhile {
            !it.isWhitespace()
        }
        if (b64.isEmpty()) return null
        val json = JSONObject(
            String(Base64.decode(b64, Base64.URL_SAFE), Charsets.UTF_8)
        )
        if (json.optInt("v") != FORMAT) return null

        ThemeEntity(
            id = 0,
            name = json.optString("name").trim().take(MAX_NAME).ifBlank { "Onyx" },
            surfaceTop = color(json.getString("surfaceTop")),
            surfaceBottom = color(json.getString("surfaceBottom")),
            keyFill = color(json.getString("keyFill")),
            keyText = color(json.getString("keyText")),
            accent = color(json.getString("accent")),
            glow = color(json.getString("glow")),
            particle = color(json.getString("particle")),
            builtin = false,
            bgUri = "",
        )
    }.getOrNull()

    private fun hex(argb: Long): String = String.format("%08X", argb)

    /** Accepts AARRGGBB or RRGGBB, always lands inside unsigned 32-bit. */
    private fun color(value: String): Long =
        value.trim().takeLast(8).toLong(16) and 0xFFFFFFFFL
}
