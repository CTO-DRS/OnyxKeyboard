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
 *  4. LETTER-CHAIN MODE (pass a key-neighbor map from [keyNeighbors]): the
 *     edit distance becomes grid-aware — sliding onto an ADJACENT key costs
 *     half a plain substitution — and ties break by the longest common
 *     subsequence (the chain the finger actually traced). This mirrors how
 *     real fingers drift onto neighbours, especially on the Arabic grid.
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
     *
     * Pass a [neighbors] map (from [keyNeighbors]) to enable letter-chain
     * mode: grid-aware distance + longest-common-subsequence tie-breaks.
     * Without it the function behaves EXACTLY like the legacy plain
     * Levenshtein ranking (distances scale by 2 in half-units, which is
     * order-monotonic and changes nothing).
     */
    fun candidates(
        path: String,
        lexicon: List<String>,
        limit: Int = 3,
        neighbors: Map<Char, Set<Char>> = emptyMap(),
    ): List<String> {
        val p = normalizeAr(path.lowercase())
        if (p.length < 2) return emptyList()
        val first = p.first()
        val last = p.last()
        val chainMode = neighbors.isNotEmpty()
        // Legacy budget (2 * maxDistance) in half-units; chain mode keeps the
        // same ceiling — neighbor substitutions are just CHEAPER inside it.
        val budget = if (chainMode) max(4, p.length) else 2 * maxDistance(p.length)

        data class Scored(val word: String, val d: Int, val lcs: Int, val lenDiff: Int)

        val scored = lexicon.asSequence()
            .map { it to normalizeAr(it.lowercase()) }
            .filter { (_, n) -> n.length >= 2 && n.first() == first && n.last() == last }
            .map { (w, n) ->
                Scored(
                    w,
                    if (chainMode) weightedDistance(n, p, neighbors) else 2 * levenshtein(n, p),
                    lcsLength(p, n),
                    abs(w.length - p.length),
                )
            }
            .filter { it.d <= budget }
            .toList()

        val comparator: Comparator<Scored> = if (chainMode) {
            compareBy({ it.d }, { -it.lcs }, { it.lenDiff })
        } else {
            compareBy({ it.d }, { it.lenDiff })
        }
        return scored.sortedWith(comparator).take(limit).map { it.word }
    }

    // ------------------------------------------------- letter-chain upgrades

    /**
     * Builds a key-neighbor map from the physical rows: two single-char keys
     * are neighbors when they TOUCH on the grid — adjacent columns in the
     * same row, or adjacent rows within one column. Multi-char labels (the
     * Arabic "لا" digraph) are skipped as keys but still advance columns so
     * the geometry stays true to the rendered board.
     *
     * Symmetric by construction: a is in n[b] iff b is in n[a].
     */
    fun keyNeighbors(rows: List<List<String>>): Map<Char, Set<Char>> {
        val cells = mutableListOf<Triple<Int, Int, Char>>()   // row, col, char
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, label ->
                if (label.length == 1) cells += Triple(r, c, label[0])
            }
        }
        val out = mutableMapOf<Char, MutableSet<Char>>()
        fun link(a: Char, b: Char) {
            if (a == b) return
            out.getOrPut(a) { mutableSetOf() }.add(b)
            out.getOrPut(b) { mutableSetOf() }.add(a)
        }
        for (i in cells.indices) for (j in i + 1 until cells.size) {
            val (r1, c1, a) = cells[i]
            val (r2, c2, b) = cells[j]
            val touch = (r1 == r2 && abs(c1 - c2) == 1) ||
                (abs(r1 - r2) == 1 && abs(c1 - c2) <= 1)
            if (touch) link(a, b)
        }
        return out
    }

    /** Neighbor map of the physical ARABIC letter grid. */
    val AR_NEIGHBORS: Map<Char, Set<Char>> by lazy { keyNeighbors(KeyboardLayouts.baseLetterRows(Lang.AR)) }

    /** Neighbor map of the physical QWERTY letter grid. */
    val EN_NEIGHBORS: Map<Char, Set<Char>> by lazy { keyNeighbors(KeyboardLayouts.baseLetterRows(Lang.EN)) }

    /**
     * Grid-aware edit distance in HALF-UNITS: match 0, substitution of key
     * neighbors 1, any other substitution 2, insertion/deletion 2. Without a
     * neighbor map this degenerates to exactly 2 * [levenshtein].
     */
    fun weightedDistance(a: String, b: String, neighbors: Map<Char, Set<Char>>): Int {
        if (a == b) return 0
        if (a.isEmpty()) return 2 * b.length
        if (b.isEmpty()) return 2 * a.length
        var prev = IntArray(b.length + 1) { 2 * it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = 2 * i
            for (j in 1..b.length) {
                val ca = a[i - 1]
                val cb = b[j - 1]
                val sub = when {
                    ca == cb -> 0
                    neighbors[ca]?.contains(cb) == true -> 1
                    else -> 2
                }
                cur[j] = minOf(cur[j - 1] + 2, prev[j] + 2, prev[j - 1] + sub)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /** Longest common subsequence length — the tie-break of chain mode. */
    fun lcsLength(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var prev = IntArray(b.length + 1)
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
                else maxOf(prev[j], cur[j - 1])
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
