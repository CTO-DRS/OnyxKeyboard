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
 * Encoded payload = the palette ONLY: name + the 7 colors + an OPTIONAL
 * additive "shape" object (key corner radius + space-bar face) + an OPTIONAL
 * additive "feedback" object (per-theme tap sound + vibration ids). Row ids,
 * builtin flags and the private background-photo path never leave the
 * device. The format version stays 1 forever: both objects are optional and
 * additive, so an OLDER Onyx still decodes a NEW code (it ignores the
 * unknown keys and falls back to its own defaults), and this decoder
 * accepts old codes without them at all. Decoding is defensive: the
 * prefix may appear anywhere in the pasted text, unknown/garbled payloads
 * yield null, names are capped, colors are masked to 32 bits and feedback
 * ids are re-validated through the profile registries before anything
 * touches Room.
 */
object OnyxThemeCodec {

    const val PREFIX = "ONYX1."
    private const val FORMAT = 1
    private const val MAX_NAME = 24
    val RADIUS_RANGE = 0..26
    val SPACE_STYLES = 0..2
    const val DEFAULT_RADIUS = 14
    const val DEFAULT_SPACE_STYLE = 0

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
            put("shape", JSONObject().apply {
                put("r", theme.keyRadius.coerceIn(RADIUS_RANGE.first, RADIUS_RANGE.last))
                put("s", theme.spaceLabel.coerceIn(SPACE_STYLES.first, SPACE_STYLES.last))
            })
            put("feedback", JSONObject().apply {
                put("snd", KeySoundProfile.from(theme.soundProfile).id)
                put("vib", KeyVibeProfile.from(theme.vibeProfile).id)
            })
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

        // Additive shape block: legacy codes (and older apps) simply skip it.
        val shape = json.optJSONObject("shape")
        val radius = shape?.optInt("r", DEFAULT_RADIUS)
            ?.coerceIn(RADIUS_RANGE.first, RADIUS_RANGE.last) ?: DEFAULT_RADIUS
        val spaceStyle = shape?.optInt("s", DEFAULT_SPACE_STYLE)
            ?.coerceIn(SPACE_STYLES.first, SPACE_STYLES.last) ?: DEFAULT_SPACE_STYLE

        // Additive feedback block: unknown ids (and legacy codes without the
        // block) fall back to the classic system sound + default vibration.
        val feedback = json.optJSONObject("feedback")
        val soundId = KeySoundProfile.from(feedback?.optString("snd")).id
        val vibeId = KeyVibeProfile.from(feedback?.optString("vib")).id

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
            keyRadius = radius,
            spaceLabel = spaceStyle,
            soundProfile = soundId,
            vibeProfile = vibeId,
        )
    }.getOrNull()

    private fun hex(argb: Long): String = String.format("%08X", argb)

    /** Accepts AARRGGBB or RRGGBB, always lands inside unsigned 32-bit. */
    private fun color(value: String): Long =
        value.trim().takeLast(8).toLong(16) and 0xFFFFFFFFL
}
