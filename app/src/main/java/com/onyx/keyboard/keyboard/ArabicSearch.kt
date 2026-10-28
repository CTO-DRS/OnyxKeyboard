package com.onyx.keyboard.keyboard

/**
 * Arabic text normalization for EMOJI SEARCH — pure Kotlin, no Android
 * imports, fully unit-testable.
 *
 * Arabic users type the same word many different ways: hamza carriers
 * (أحب / احب), taa marbuta (شجرة / شجره), alef maqsura (على / علي),
 * diacritics (مَرْحَبا) and decorative tatweel (عااالي). A search that
 * treats those as different words is broken. [normalize] folds every
 * variant onto ONE canonical form so a single query spelling finds them all.
 */
object ArabicSearch {

    /**
     * Canonical form of [raw]:
     *  - Unicode NFC (composites collapse to single code points)
     *  - harakat / tanween / shadda / small marks stripped (0610–061A, 064B–065F, 0670)
     *  - tatweel (ـ U+0640) stripped
     *  - hamza carriers unified: أ إ آ ٱ → ا ، ؤ → و ، ئ → ي (isolated ء kept)
     *  - taa marbuta ة → ه ، alef maqsura ى → ي
     *  - Latin lowercased (mixed-language aliases stay searchable)
     *  - whitespace collapsed, trimmed
     */
    fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        var prevSpace = false
        for (ch in raw) {
            val c = when (ch) {
                // Diacritics band (fathatan..sukun) + superscript alef.
                in '\u064B'..'\u065F', '\u0670', in '\u06D6'..'\u06DC', in '\u0610'..'\u061A' -> null
                '\u0640' -> null                                  // tatweel
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                'ة' -> 'ه'
                'ى' -> 'ي'
                '\u00A0', '\u2009', '\u200B' -> ' '               // nbsp / thin / ZWSP
                else -> ch.lowercaseChar()
            }
            if (c == null) continue
            if (c == ' ') {
                if (prevSpace) continue
                prevSpace = true
            } else {
                prevSpace = false
            }
            sb.append(c)
        }
        return sb.toString().trim()
    }

    /** Normalized, non-empty tokens of a query (split on whitespace). */
    fun tokens(raw: String): List<String> =
        normalize(raw).split(' ').filter { it.isNotEmpty() }

    /**
     * AND-match: EVERY query token must hit at least one keyword
     * (or the reverse — a keyword inside a longer token, so "قلب"
     * matches inside "قلوب" and "قلوب" typed fully still matches "قلب").
     * Single-character keywords only match EXACTLY to keep one-letter
     * queries from exploding into noise.
     */
    fun matches(keywords: List<String>, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val kws = keywords.map { normalize(it) }.filter { it.isNotEmpty() }
        if (kws.isEmpty()) return false
        return tokens.all { t ->
            kws.any { k ->
                if (k.length == 1) k == t
                else k.contains(t) || (t.length >= 2 && t.contains(k))
            }
        }
    }
}
