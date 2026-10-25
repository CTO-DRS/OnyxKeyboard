package com.onyx.keyboard

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.AltChars
import com.onyx.keyboard.keyboard.GlideMatcher
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.KeyboardLayouts
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.ShiftState
import com.onyx.keyboard.intelligence.ArabicLexicon
import com.onyx.keyboard.intelligence.EnglishLexicon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the swipe (glide) typing engine + the advanced numpad:
 *  - Arabic normalization (hamza folding, harakat stripping)
 *  - Levenshtein distance
 *  - glide candidate ranking against the built-in lexicons
 *  - controller glide commit / candidate replacement / strip dismissal
 *  - numpad layout completeness + commit mapping + alternates
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxSwipeNumpadTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

    /** Records every IME operation the controller performs. */
    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val deletions = mutableListOf<Pair<Int, Int>>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletions += beforeLength to afterLength
            return true
        }
    }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        runBlocking { AppGraph.repo.ensureSeeded() }
        controller = OnyxKeyboardController(app, scope, AppGraph.repo)
        ic = FakeInputConnection(ApplicationProvider.getApplicationContext())
        controller.attach(ic)
        controller.lang.value = Lang.EN
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ------------------------------------------------------- normalization

    @Test
    fun normalize_folds_hamza_carriers_and_strips_harakat() {
        assertEquals("اهلا", GlideMatcher.normalizeAr("أهلاً"))
        assertEquals("اهلا", GlideMatcher.normalizeAr("إهلا"))
        assertEquals("كتابه", GlideMatcher.normalizeAr("كتابة"))
        assertEquals("علي", GlideMatcher.normalizeAr("على"))
        // Hamza-on-waw folds onto the bare waw.
        assertEquals("مومن", GlideMatcher.normalizeAr("مؤمن"))
    }

    @Test
    fun normalize_strips_tatweel_and_dagger_alif() {
        assertEquals("سلام", GlideMatcher.normalizeAr("سـلام"))
        assertEquals("الله", GlideMatcher.normalizeAr("اللّٰه"))
    }

    // -------------------------------------------------------- levenshtein

    @Test
    fun levenshtein_basic_distances() {
        assertEquals(0, GlideMatcher.levenshtein("hello", "hello"))
        assertEquals(1, GlideMatcher.levenshtein("helo", "hello"))
        assertEquals(2, GlideMatcher.levenshtein("wrd", "world"))
        assertEquals(3, GlideMatcher.levenshtein("abc", "xyz"))
    }

    // ----------------------------------------------------- glide matching

    @Test
    fun glide_arabic_path_matches_lexicon_words() {
        // Direct hit: the shortest word starting and ending with these letters.
        val cands = GlideMatcher.candidates("في", ArabicLexicon.words)
        assertEquals("في", cands.first())
    }

    @Test
    fun glide_arabic_path_normalizes_word_side_too() {
        // Realistic base-layout path for أهلا crosses the bare-alef key, and
        // the word side folds its own hamza/ta-marbuta variants before scoring.
        val cands = GlideMatcher.candidates("اهلا", ArabicLexicon.words)
        assertTrue("expected أهلا in $cands", cands.contains("أهلا"))

        val kitab = GlideMatcher.candidates("كتابه", ArabicLexicon.words)
        assertTrue("expected كتابة in $kitab", kitab.contains("كتابة"))
    }

    @Test
    fun glide_english_path_matches_hello() {
        val cands = GlideMatcher.candidates("helo", EnglishLexicon.words)
        assertEquals("hello", cands.first())
    }

    @Test
    fun glide_short_path_still_finds_compact_words() {
        val cands = GlideMatcher.candidates("wrd", EnglishLexicon.words)
        assertTrue("expected world in $cands", cands.contains("world"))
    }

    @Test
    fun glide_single_char_path_returns_nothing() {
        assertTrue(GlideMatcher.candidates("h", EnglishLexicon.words).isEmpty())
    }

    @Test
    fun glide_first_letter_mismatch_returns_nothing() {
        // No English lexicon word starts with x and ends with q.
        assertTrue(GlideMatcher.candidates("xq", EnglishLexicon.words).isEmpty())
    }

    @Test
    fun glide_candidates_capped_at_limit() {
        val cands = GlideMatcher.candidates("thn", EnglishLexicon.words, limit = 2)
        assertTrue(cands.size <= 2)
        assertTrue("expected than/then in $cands", cands.contains("than") || cands.contains("then"))
    }

    @Test
    fun glide_is_case_insensitive_for_latin() {
        val cands = GlideMatcher.candidates("HELO", EnglishLexicon.words)
        assertEquals("hello", cands.first())
    }

    // ------------------------------------------------- controller glide flow

    @Test
    fun onGlideWord_commits_best_and_shows_strip() {
        controller.onGlideWord(listOf("hello", "help"))
        assertEquals("hello", ic.committed.last())
        assertEquals(listOf("hello", "help"), controller.glideCandidates.value)
    }

    @Test
    fun onGlideWord_applies_shift_capitalization_in_english() {
        controller.lang.value = Lang.EN
        controller.shift.value = ShiftState.ON
        controller.onGlideWord(listOf("hello"))
        assertEquals("Hello", ic.committed.last())
        assertEquals(ShiftState.OFF, controller.shift.value)
    }

    @Test
    fun pickGlideCandidate_replaces_committed_word() {
        controller.onGlideWord(listOf("hello", "help"))
        controller.pickGlideCandidate("help")
        // The previously committed word "hello" (5 chars) must be deleted first.
        assertEquals(listOf(5 to 0), ic.deletions)
        assertEquals("help", ic.committed.last())
        // Strip hides after the replacement.
        assertTrue(controller.glideCandidates.value.isEmpty())
    }

    @Test
    fun plain_tap_dismisses_glide_strip() {
        controller.onGlideWord(listOf("hello", "help"))
        assertTrue(controller.glideCandidates.value.isNotEmpty())
        controller.onChar("a")
        assertTrue(controller.glideCandidates.value.isEmpty())
    }

    @Test
    fun page_change_clears_glide_strip() {
        controller.onGlideWord(listOf("hello"))
        controller.setPage(KeyboardPage.SYMBOLS)
        assertTrue(controller.glideCandidates.value.isEmpty())
    }

    // ------------------------------------------------------ numpad layout

    @Test
    fun numpad_grid_covers_all_digits_and_operators() {
        val flat = KeyboardLayouts.numpadRows.flatten() + listOf("0", ".")
        (0..9).forEach { d -> assertTrue("missing digit $d", d.toString() in flat) }
        listOf("÷", "×", "+", "=", ".", "%", "√", "(", ")", "π").forEach { sym ->
            assertTrue("missing symbol $sym", sym in flat + KeyboardLayouts.numpadSlim)
        }
        assertTrue("-" == KeyboardLayouts.numpadCommit("−"))
    }

    @Test
    fun numpad_commit_mapping() {
        assertEquals("²", KeyboardLayouts.numpadCommit("x²"))
        assertEquals("-", KeyboardLayouts.numpadCommit("−"))
        assertEquals("7", KeyboardLayouts.numpadCommit("7"))
        assertEquals("÷", KeyboardLayouts.numpadCommit("÷"))
    }

    @Test
    fun numpad_alternates_offered_on_long_press() {
        controller.openAltPopup("0", numpad = true)
        assertEquals(listOf("$", "€", "¥", "£"), controller.altPopup.value?.options)

        controller.closeAltPopup()
        controller.openAltPopup(".", numpad = true)
        assertTrue(controller.altPopup.value?.options?.contains("،") == true)

        // Unknown key -> no popup.
        controller.closeAltPopup()
        controller.openAltPopup("7", numpad = true)
        assertNull(controller.altPopup.value)
    }

    @Test
    fun numpad_page_is_valid_keyboard_page() {
        controller.setPage(KeyboardPage.NUMPAD)
        assertEquals(KeyboardPage.NUMPAD, controller.page.value)
        controller.setPage(KeyboardPage.LETTERS)
        assertEquals(KeyboardPage.LETTERS, controller.page.value)
    }

    @Test
    fun arabic_lexicon_used_for_arabic_glide_has_words() {
        assertFalse(ArabicLexicon.words.isEmpty())
        assertFalse(EnglishLexicon.words.isEmpty())
    }
}
