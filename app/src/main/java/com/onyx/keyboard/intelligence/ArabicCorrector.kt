package com.onyx.keyboard.intelligence

import com.onyx.keyboard.nativebridge.OnyxNative

/**
 * Arabic orthography helper.
 * Uses the C++ core when available (hot path — runs on every keystroke),
 * falls back to a pure-Kotlin implementation otherwise.
 */
object ArabicCorrector {

    fun normalize(word: String): String =
        if (OnyxNative.ensureLoaded()) {
            OnyxNative.normalizeArabic(word)
        } else {
            normalizeKotlin(word)
        }

    /** Bounded edit distance via native core, Kotlin fallback included. */
    fun distance(a: String, b: String, max: Int = 2): Int =
        if (OnyxNative.ensureLoaded()) {
            OnyxNative.editDistance(normalize(a), normalize(b), max)
        } else {
            levenshtein(normalizeKotlin(a), normalizeKotlin(b), max)
        }

    /**
     * Finds the closest dictionary word for [raw].
     * Returns null when nothing scores within [maxDistance] edits.
     */
    fun correct(raw: String, pool: Collection<String>, maxDistance: Int = 2): String? {
        if (raw.length < 3) return null
        val target = normalize(raw)
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in pool) {
            if (candidate.isEmpty() || candidate == raw) continue
            val norm = normalize(candidate)
            if (norm == target) continue
            val d = if (OnyxNative.ensureLoaded()) {
                OnyxNative.editDistance(target, norm, maxDistance)
            } else {
                levenshtein(target, norm, maxDistance)
            }
            if (d in 1..maxDistance && d < bestScore) {
                bestScore = d
                best = candidate
                if (d == 1) break // can't do better than one edit
            }
        }
        return best
    }

    // ------------------------------------------------------------------ pure Kotlin fallbacks

    fun normalizeKotlin(word: String): String = buildString(word.length) {
        for (c in word) {
            when (c) {
                in '\u064B'..'\u065F', '\u0670', '\u0640' -> Unit // harakat + tatweel
                'آ', 'أ', 'إ', 'ٱ' -> append('ا')
                'ى' -> append('ي')
                'ة' -> append('ه')
                'ؤ' -> append('و')
                'ئ' -> append('ي')
                else -> append(c)
            }
        }
    }

    private fun levenshtein(a: CharSequence, b: CharSequence, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val d = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                curr[j] = d
                if (d < rowMin) rowMin = d
            }
            if (rowMin > max) return max + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
