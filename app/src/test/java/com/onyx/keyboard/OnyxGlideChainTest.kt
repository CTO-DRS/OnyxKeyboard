package com.onyx.keyboard

import com.onyx.keyboard.keyboard.GlideMatcher
import com.onyx.keyboard.keyboard.KeyboardLayouts
import com.onyx.keyboard.keyboard.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flat single-char key sets of both boards (derived from the layouts). */
private fun boardChars(lang: Lang): Set<Char> =
    KeyboardLayouts.baseLetterRows(lang)
        .flatten().filter { it.length == 1 }.map { it[0] }.toSet()

/**
 * LETTER-CHAIN glide upgrades: grid-aware neighbor maps derived from the
 * REAL layouts, the weighted half-unit edit distance, the LCS tie-break,
 * and the guarantee that the legacy (no-map) path behaves exactly as
 * before. Pure JVM — GlideMatcher has no Android imports.
 */
class OnyxGlideChainTest {

    // ------------------------------------------------------------- neighbors

    @Test
    fun neighborsAreDerivedFromRealLayoutsAndSymmetric() {
        listOf(Lang.AR to GlideMatcher.AR_NEIGHBORS, Lang.EN to GlideMatcher.EN_NEIGHBORS)
            .forEach { (lang, n) ->
                assertTrue("map must not be empty", n.isNotEmpty())
                // Symmetry: a is in n[b] iff b is in n[a].
                n.forEach { (a, set) ->
                    set.forEach { b ->
                        assertTrue("asymmetry $a->$b", n[b]?.contains(a) == true)
                    }
                }
                // Keys and neighbors are exactly the board's single-char keys.
                val legal = boardChars(lang)
                n.keys.forEach { assertTrue("stray key $it", it in legal) }
                n.values.flatten().forEach { assertTrue("stray neighbor $it", it in legal) }
            }
    }

    @Test
    fun everySingleCharKeyHasAnEntry() {
        listOf(
            Lang.AR to GlideMatcher.AR_NEIGHBORS,
            Lang.EN to GlideMatcher.EN_NEIGHBORS,
        ).forEach { (lang, n) ->
            boardChars(lang).forEach { assertTrue("missing entry for $it", n.containsKey(it)) }
        }
    }

    @Test
    fun knownAdjacenciesHold() {
        // Same-row adjacency: ع sits right next to غ on the Arabic top row.
        assertTrue(GlideMatcher.AR_NEIGHBORS['ع']!!.contains('غ'))
        // Cross-row adjacency within one column: ض (r0c0) / ش (r1c0).
        assertTrue(GlideMatcher.AR_NEIGHBORS['ض']!!.contains('ش'))
        // QWERTY classics.
        assertTrue(GlideMatcher.EN_NEIGHBORS['q']!!.contains('w'))
        assertTrue(GlideMatcher.EN_NEIGHBORS['g']!!.contains('h'))
        // NOT adjacent: far columns / rows.
        assertFalse(GlideMatcher.EN_NEIGHBORS['q']!!.contains('p'))
        assertFalse(GlideMatcher.AR_NEIGHBORS['ض']!!.contains('ظ'))
    }

    @Test
    fun digraphKeyIsExcludedButGeometryStaysTrue() {
        // The Arabic "لا" digraph is one key but two chars: it must never
        // add a map entry of its own — the key set equals EXACTLY the
        // single-char keys of the board.
        assertEquals(boardChars(Lang.AR), GlideMatcher.AR_NEIGHBORS.keys)
        // Its column still counts for spacing: ر (c3) and ى (c5) have the
        // digraph between them, so they are two apart — NOT neighbors.
        assertFalse(GlideMatcher.AR_NEIGHBORS['ر']!!.contains('ى'))
        // The standalone letters themselves are fine.
        assertNotNull(GlideMatcher.AR_NEIGHBORS['ل'])
        assertTrue(GlideMatcher.AR_NEIGHBORS['ل']!!.contains('ا'))
    }

    // ------------------------------------------------------ weighted distance

    @Test
    fun weightedDistanceBasics() {
        val n = GlideMatcher.AR_NEIGHBORS
        assertEquals(0, GlideMatcher.weightedDistance("هلا", "هلا", n))
        // Neighbor substitution costs 1 (half a plain substitution): غ's
        // first neighbor in the top row is ف.
        val neighborOfGhain = GlideMatcher.AR_NEIGHBORS['غ']!!.first()
        assertEquals(1, GlideMatcher.weightedDistance("غ", "$neighborOfGhain", n))
        // Plain substitution / insertion / deletion cost 2.
        assertEquals(2, GlideMatcher.weightedDistance("abc", "abd", n))
        assertEquals(2, GlideMatcher.weightedDistance("abc", "ab", n))
        assertEquals(2, GlideMatcher.weightedDistance("ab", "abc", n))
        assertEquals(6, GlideMatcher.weightedDistance("", "abc", n))
    }

    @Test
    fun weightedDistanceWithoutMapEqualsTwiceLevenshtein() {
        listOf(
            "hello" to "helo", "world" to "wrd",
            "اهلا" to "هلا", "كتاب" to "كتب",
        ).forEach { (a, b) ->
            assertEquals(
                2 * GlideMatcher.levenshtein(a, b),
                GlideMatcher.weightedDistance(a, b, emptyMap()),
            )
        }
    }

    // ------------------------------------------------------------------ lcs

    @Test
    fun lcsLengthBasics() {
        assertEquals(4, GlideMatcher.lcsLength("اهلا", "اهلا"))
        assertEquals(3, GlideMatcher.lcsLength("اهلا", "هلا"))
        assertEquals(1, GlideMatcher.lcsLength("ab", "ba"))   // only single letters are shared
        assertEquals(0, GlideMatcher.lcsLength("", "xyz"))
        assertEquals(0, GlideMatcher.lcsLength("abc", ""))
        assertEquals(4, GlideMatcher.lcsLength("كتاب", "كتابان"))
    }

    // -------------------------------------------------------------- ranking

    @Test
    fun neighborSubstitutionBeatsDistantOne() {
        // Path "عفا", candidates "عقا" (ق is a key-neighbor of ف) and
        // "عزا" (ز is far away). LEGACY ranking sees one plain substitution
        // in BOTH words -> stable order keeps the lexicon order ("عزا"
        // first). Chain mode prices the neighbor slip at HALF and pulls the
        // intended word to the top.
        val lex = listOf("عزا", "عقا")
        assertEquals("عزا", GlideMatcher.candidates("عفا", lex).first())
        assertEquals(
            "عقا",
            GlideMatcher.candidates("عفا", lex, neighbors = GlideMatcher.AR_NEIGHBORS).first()
        )
    }

    @Test
    fun chainModeKeepsBoundaryPruningAndCap() {
        // A path can never resolve to a word whose boundary letters differ:
        // no lexicon word starts with ظ -> empty even in chain mode.
        val lex = listOf("بلا", "غلا", "علا")
        assertTrue(GlideMatcher.candidates("ظا", lex, neighbors = GlideMatcher.AR_NEIGHBORS).isEmpty())
        // The exact word still wins and the list stays capped.
        val got = GlideMatcher.candidates(
            "اهلا", listOf("اهلا", "الله", "اهل"), limit = 2,
            neighbors = GlideMatcher.AR_NEIGHBORS,
        )
        assertEquals("اهلا", got.first())
        assertTrue(got.size <= 2)
    }

    @Test
    fun legacyModeUnchanged() {
        // Without a neighbor map the ranking MUST match the pre-chain
        // behaviour exactly (same winner, same order semantics). "helot"
        // sits at the same distance AND length diff as "hello", so stable
        // sort keeps the lexicon order with "hello" on top.
        val en = GlideMatcher.candidates("helo", listOf("hello", "helot"))
        assertEquals("hello", en.first())
        assertTrue(GlideMatcher.candidates("h", listOf("hello")).isEmpty())
        assertTrue(GlideMatcher.candidates("xq", listOf("xxxx", "qqqq")).isEmpty())
    }

    @Test
    fun lcsTiebreakPrefersTheTracedChain() {
        // Path "ktb": "kzb" (plain substitution) and "ktab" (insertion) sit
        // at the SAME legacy distance. Legacy breaks the tie by length
        // similarity -> "kzb". Chain mode breaks it by the longest common
        // subsequence -> "ktab" CONTAINS the whole traced chain k-t-b.
        val lex = listOf("kzb", "ktab")
        assertEquals("kzb", GlideMatcher.candidates("ktb", lex).first())
        assertEquals(
            "ktab",
            GlideMatcher.candidates("ktb", lex, neighbors = GlideMatcher.EN_NEIGHBORS).first()
        )
    }
}
