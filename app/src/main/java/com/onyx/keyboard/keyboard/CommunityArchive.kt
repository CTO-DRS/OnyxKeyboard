package com.onyx.keyboard.keyboard

import com.onyx.keyboard.data.db.ThemeEntity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stable identity of a theme LOOK (name + palette + shape) — used by the
 * community archive to detect "already imported" so one-tap imports never
 * stack duplicate rows in the gallery.
 */
internal fun themeSignature(t: ThemeEntity): String =
    listOf(
        t.name,
        t.surfaceTop, t.surfaceBottom, t.keyFill, t.keyText,
        t.accent, t.glow, t.particle,
        t.keyRadius, t.spaceLabel,
    ).joinToString("|")

/** One entry of the community theme archive. */
data class CommunityEntry(
    val theme: ThemeEntity,
    /** Display handle of the contributing author/team. */
    val by: String,
) {
    val signature: String get() = themeSignature(theme)
}

/**
 * COMMUNITY THEME ARCHIVE — a growing shelf of shareable looks.
 *
 * Two layers feed it:
 *  1. [bundled] — a curated pack compiled into the app, available offline
 *     from the very first run.
 *  2. The ONLINE CATALOG (`community/themes.json` in the Onyx repo) — a
 *     JSON array of ONYX1. codes contributed by users through pull
 *     requests. [fetchCatalog] downloads it (hard timeouts + size cap),
 *     [parseCatalog] decodes each code with the same defensive codec the
 *     clipboard import uses, and the controller merges + caches the raw
 *     payload so the shelf survives offline relaunches.
 *
 * Everything flows through [OnyxThemeCodec], so entries carry ONLY a name,
 * colors and shape — no ids, no photos, no device data.
 */
object CommunityArchive {

    const val CATALOG_URL: String =
        "https://raw.githubusercontent.com/CTO-DRS/OnyxKeyboard/main/community/themes.json"
    const val DEFAULT_BY: String = "مجتمع Onyx"

    /** Hard ceiling on catalog entries — a hostile mirror can't flood the shelf. */
    const val MAX_CATALOG = 64
    const val MAX_BYTES: Long = 256L * 1024
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 12_000

    /**
     * The offline starter shelf: 16 curated looks (10 dark + 6 light) with
     * readability-checked contrast, authored by the Onyx team.
     */
    fun bundled(): List<CommunityEntry> = listOf(
        entry("نيون العاصمة",
            0xFF0A0912, 0xFF161024, 0xFF221A38, 0xFFF1EAFF,
            0xFFC084FC, 0xFF9333EA, 0xFFC084FC),
        entry("ميناء الليل",
            0xFF03121A, 0xFF072433, 0xFF0E3548, 0xFFE0F5FF,
            0xFF22D3EE, 0xFF0891B2, 0xFF22D3EE),
        entry("صحراء منتصف الليل",
            0xFF120E06, 0xFF20180B, 0xFF33270F, 0xFFFFF3D9,
            0xFFFBBF24, 0xFFB45309, 0xFFFBBF24),
        entry("زمرّد الرمال",
            0xFF071510, 0xFF0C241A, 0xFF123B2C, 0xFFE8FFF5,
            0xFF34D399, 0xFF059669, 0xFF34D399),
        entry("بحر القرمزي",
            0xFF170A0E, 0xFF27101A, 0xFF3D1526, 0xFFFFEAF1,
            0xFFFB7185, 0xFFBE123C, 0xFFFB7185),
        entry("بنفسج المجرّة",
            0xFF0B0620, 0xFF160D38, 0xFF241457, 0xFFF0EAFF,
            0xFFA78BFA, 0xFF6D28D9, 0xFFA78BFA),
        entry("فولاذ الشتاء",
            0xFF0B1118, 0xFF141E2A, 0xFF233447, 0xFFEAF2FA,
            0xFF7DD3FC, 0xFF0284C7, 0xFF7DD3FC),
        entry("عنبر الدخان",
            0xFF151009, 0xFF251B0F, 0xFF3A2A15, 0xFFFFF1DC,
            0xFFD97706, 0xFF92400E, 0xFFF59E0B),
        entry("نيون طوكيو",
            0xFF0D0512, 0xFF1A0A24, 0xFF2E1040, 0xFFFFE9FF,
            0xFFF472B6, 0xFFC026D3, 0xFF22D3EE),
        entry("قمر البنفسج",
            0xFF0E0A1A, 0xFF181030, 0xFF2A1C4E, 0xFFEFEBFF,
            0xFF8B5CF6, 0xFF7C3AED, 0xFF8B5CF6),
        entry("قهوة الصباح",
            0xFFF7F0E8, 0xFFEFE2D4, 0xFFFFFFFF, 0xFF3E2C1E,
            0xFFA16207, 0xFFD6A756, 0xFFC08A3E),
        entry("سماوي هادئ",
            0xFFEFF6FD, 0xFFDDEBFA, 0xFFFFFFFF, 0xFF1D3348,
            0xFF0284C7, 0xFF7CC4EF, 0xFF42A5F5),
        entry("خوخة الورد",
            0xFFFBF0F2, 0xFFF6DEE4, 0xFFFFFFFF, 0xFF3E222C,
            0xFFDB2777, 0xFFF58EA8, 0xFFEC6E9E),
        entry("نعناع الحديقة",
            0xFFEFFAF3, 0xFFDCF2E7, 0xFFFFFFFF, 0xFF1D3A2B,
            0xFF059669, 0xFF6EE7B7, 0xFF34D399),
        entry("ورق عتيق",
            0xFFFAF5EA, 0xFFEFE4D0, 0xFFFFFFFF, 0xFF33301F,
            0xFF8C6D1F, 0xFFCBB26A, 0xFFA67C00),
        entry("بنفسج الشفق",
            0xFFF5F1FB, 0xFFE9E2F6, 0xFFFFFFFF, 0xFF2B2140,
            0xFF7C3AED, 0xFFB7A3E8, 0xFF9575CD),
    )

    /**
     * Parses a catalog payload: `{"themes":[{"code":"ONYX1.…","by":"…"}]}`.
     * Pure and defensive — malformed JSON yields an empty list, individual
     * broken entries are skipped, codes decode through [OnyxThemeCodec]
     * (which sanitizes names/colors/shape), and the array is capped at
     * [MAX_CATALOG].
     */
    fun parseCatalog(raw: String): List<CommunityEntry> = runCatching {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("themes") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                if (size >= MAX_CATALOG) break
                val obj = arr.optJSONObject(i) ?: continue
                val theme = OnyxThemeCodec.decode(obj.optString("code")) ?: continue
                add(CommunityEntry(theme, obj.optString("by").trim().ifBlank { DEFAULT_BY }))
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Downloads the catalog text. Null on ANY failure (offline, timeout,
     * oversize, non-JSON payload) — the caller keeps the cached/bundled
     * shelf and flashes an offline note. Network-touching, so callers run
     * it on Dispatchers.IO.
     */
    fun fetchCatalog(url: String = CATALOG_URL): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OnyxKeyboard/1.0 (community theme archive)")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            val body = readCapped(conn) ?: return null
            // Sanity gate: must BE a themes catalog, not an HTML error page.
            if (!JSONObject(body).has("themes")) return null
            body
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun readCapped(conn: HttpURLConnection): String? {
        val input = conn.inputStream ?: return null
        val out = ByteArrayOutputStream(16 * 1024)
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    /** Factory for the bundled shelf — every entry is a fresh unsaved draft. */
    private fun entry(
        name: String,
        surfaceTop: Long, surfaceBottom: Long,
        keyFill: Long, keyText: Long,
        accent: Long, glow: Long, particle: Long,
    ) = CommunityEntry(
        theme = ThemeEntity(
            id = 0,
            name = name,
            surfaceTop = surfaceTop, surfaceBottom = surfaceBottom,
            keyFill = keyFill, keyText = keyText,
            accent = accent, glow = glow, particle = particle,
            builtin = false,
        ),
        by = DEFAULT_BY,
    )
}
