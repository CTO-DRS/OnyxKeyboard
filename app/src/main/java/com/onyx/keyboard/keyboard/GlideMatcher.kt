package com.onyx.keyboard.keyboard

import kotlin.math.abs
import kotlin.math.max

/**
 * Word matcher for glide (swipe) typing.
 *
 * The gesture layer records the sequence of letter keys the finger travelled
 * across ("path", e.g. "هلا" or "helo"). This object scores the built-in
 * lexicon against that path and returns the best candidates:
 *
 *  1. Normalization folds Arabic hamza carriers / ta-marbuta / alif maqsura
 *     so "أهلا" matches a "هلا" path, and strips harakat + tatweel.
 *  2. A word is only considered when its first AND last letters match the
 *     path's (the classic gesture-typing pruning heuristic — a finger path
 *     reliably starts and ends on the word's boundary letters).
 *  3. Survivors are ranked by Levenshtein distance to the path, then by
 *     length similarity, and the top [limit] are returned.
 *
 * Pure Kotlin — no Android imports — so it is directly unit-testable.
 */
object GlideMatcher {

    /** Edit-distance budget relative to the path length (never below 2). */
    private fun maxDistance(pathLen: Int): Int = max(2, pathLen / 2)

    /**
     * Folds Arabic spelling variants onto a canonical skeleton:
     *  - hamza carriers (أ إ آ ٱ) -> bare alef
     *  - ta marbuta (ة) -> ha
     *  - alif maqsura (ى) -> ya
     *  - hamza-on-waw/ya (ؤ ئ) -> bare waw/ya
     *  - harakat, dagger alif and tatweel are dropped entirely.
     * Latin text passes through unchanged (case handled by the caller).
     */
    fun normalizeAr(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                '\u0640' -> {}                                  // tatweel
                in '\u064B'..'\u0652' -> {}                     // harakat
                '\u0670' -> {}                                  // dagger alif
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Classic two-row Levenshtein distance (small strings only — fine here). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /**
     * Ranks [lexicon] against the travelled key [path] (raw labels, e.g.
     * "هلا" or "helo"). Returns at most [limit] words, best first.
     * Uppercase paths are matched case-insensitively for Latin words.
     */
    fun candidates(
        path: String,
        lexicon: List<String>,
        limit: Int = 3,
    ): List<String> {
        val p = normalizeAr(path.lowercase())
        if (p.length < 2) return emptyList()
        val first = p.first()
        val last = p.last()
        val budget = maxDistance(p.length)

        return lexicon.asSequence()
            .map { it to normalizeAr(it.lowercase()) }
            .filter { (_, n) -> n.length >= 2 && n.first() == first && n.last() == last }
            .map { (w, n) -> Triple(w, levenshtein(n, p), abs(w.length - p.length)) }
            .filter { (_, d, _) -> d <= budget }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .take(limit)
            .map { it.first }
            .toList()
    }
}
