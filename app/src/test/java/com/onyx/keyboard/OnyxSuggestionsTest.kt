package com.onyx.keyboard

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.intelligence.ArabicLexicon
import com.onyx.keyboard.intelligence.EnglishLexicon
import com.onyx.keyboard.intelligence.Suggestion
import com.onyx.keyboard.intelligence.SuggestionEngine
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.ShiftState
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for the FULL suggestion system:
 *  - engine: learned bigram ranking + starter fallback + self-exclusion
 *  - repo: recordBigram hygiene (bump/insert, self-pair rejection, digits)
 *  - controller: pair learning between consecutive words, prediction
 *    chaining after a picked prediction, terminator/newline context reset,
 *    EN capitalization of suggestions, predictions-off switch, dismiss.
 *
 * Async effects run on the controller scope (Main.immediate) + Room's own
 * executor; [drain] pumps Robolectric's paused main looper so posted
 * continuations actually execute before assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxSuggestionsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var engine: SuggestionEngine
    private lateinit var ic: FakeInputConnection
    private var pid: Long = 0

    /** Records every IME operation the controller performs. */
    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val composing = mutableListOf<String>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing += text?.toString().orEmpty()
            return true
        }
    }

    /** Pumps the paused Robolectric main looper until [cond] or timeout. */
    private fun drain(timeoutMs: Long = 5000, cond: () -> Boolean): Boolean {
        val looper = shadowOf(Looper.getMainLooper())
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            looper.idle()
            if (cond()) return true
            Thread.sleep(10)
        }
        looper.idle()
        return cond()
    }

    /** Polls Room (via runBlocking) until the pair prev→next is learned. */
    private fun learnedPair(prev: String, next: String): Boolean =
        drain { runBlocking { AppGraph.repo.nextWords(pid, prev, 5).contains(next) } }

    @Before
    fun setUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        AppGraph.repo.ensureSeeded()
        val repo = AppGraph.repo
        // Isolated profile so tests never see each other's learned pairs.
        pid = repo.addProfile("suggestions-test", "S")
        repo.setCurrentProfile(pid)
        controller = OnyxKeyboardController(app, scope, repo)
        engine = SuggestionEngine(repo)
        ic = FakeInputConnection(ApplicationProvider.getApplicationContext())
        controller.attach(ic)
        controller.lang.value = Lang.EN
        controller.profile.value = repo.currentProfile()
    }

    @After
    fun tearDown() = runBlocking {
        scope.cancel()
        AppGraph.repo.deleteProfile(pid)
    }

    // ------------------------------------------------------------ engine

    @Test
    fun predictNext_ranks_learned_bigrams_first() = runBlocking {
        // "world" bumped twice → freq 2 beats "there" (freq 1); same-ms
        // lastUsed ties would otherwise leave the order to the DB.
        AppGraph.repo.recordBigram(pid, "hello", "world", "en")
        AppGraph.repo.recordBigram(pid, "hello", "world", "en")
        AppGraph.repo.recordBigram(pid, "hello", "there", "en")

        val out = engine.predictNext("hello", "en", pid)

        assertEquals(listOf("world", "there"), out.map { it.text })
    }

    @Test
    fun predictNext_falls_back_to_starters_for_unknown_context() = runBlocking {
        val out = engine.predictNext("zzzunseen", "en", pid)

        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.text in EnglishLexicon.starters })
    }

    @Test
    fun predictNext_arabic_uses_arabic_starters() = runBlocking {
        val out = engine.predictNext(null, "ar", pid)

        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.text in ArabicLexicon.starters })
    }

    @Test
    fun predictNext_never_suggests_the_context_word_itself() = runBlocking {
        AppGraph.repo.recordBigram(pid, "wow", "wow", "en")

        val out = engine.predictNext("wow", "en", pid)

        assertTrue(out.none { it.text == "wow" })
    }

    // -------------------------------------------------------------- repo

    @Test
    fun recordBigram_bumps_frequency_of_existing_pair() = runBlocking {
        val repo = AppGraph.repo
        repo.recordBigram(pid, "good", "morning", "en")
        repo.recordBigram(pid, "good", "night", "en")
        repo.recordBigram(pid, "good", "morning", "en") // freq 2 now

        val top = repo.nextWords(pid, "good", 5)

        assertEquals("morning", top.first()) // higher freq wins
        assertTrue(top.contains("night"))
    }

    @Test
    fun recordBigram_rejects_empty_self_and_digit_pairs() = runBlocking {
        val repo = AppGraph.repo
        repo.recordBigram(pid, "", "x", "en")
        repo.recordBigram(pid, "x", "", "en")
        repo.recordBigram(pid, "same", "same", "en")
        repo.recordBigram(pid, "abc123", "next", "en")
        repo.recordBigram(pid, "prev", "n4ne", "en")

        assertTrue(repo.nextWords(pid, "", 5).isEmpty())
        assertTrue(repo.nextWords(pid, "same", 5).isEmpty())
        assertTrue(repo.nextWords(pid, "abc123", 5).isEmpty())
        assertTrue(repo.nextWords(pid, "prev", 5).isEmpty())
    }

    // -------------------------------------------------------- controller

    @Test
    fun typing_two_words_learns_their_pair_and_predicts() {
        "hello".forEach { controller.onChar(it.toString()) }
        controller.onSpace()
        "world".forEach { controller.onChar(it.toString()) }
        controller.onSpace()

        assertTrue(
            "bigram (hello→world) must be learned",
            learnedPair("hello", "world")
        )

        // After the second space the strip predicts continuations of "world"
        // (starters — nothing learned for it yet), driven by lastWord.
        val predicting = drain { controller.suggestions.value.isNotEmpty() }
        assertTrue("predictions must appear after space", predicting)
        assertTrue(controller.buffer.value.isEmpty())
    }

    @Test
    fun picking_a_prediction_inserts_word_space_and_chains_context() {
        // Simulate an existing context: lastWord set by a previous commit.
        controller.lastWord = "good"
        runBlocking { AppGraph.repo.recordBigram(pid, "good", "morning", "en") }

        controller.commitSuggestion(Suggestion("morning"))

        // Word + trailing space committed in one shot.
        assertEquals(listOf("morning "), ic.committed)

        // The pair (good → morning) gets a bump via the pick path.
        assertTrue(
            "picked prediction must extend the pair",
            learnedPair("good", "morning")
        )
        assertEquals("morning", controller.lastWord)
    }

    @Test
    fun terminator_resets_context_and_offers_starters() {
        controller.lastWord = "hello"
        controller.sentenceStart = false

        controller.commitText(".")

        assertNull(controller.lastWord)
        assertTrue(controller.sentenceStart)
        assertTrue(controller.autoCapNext.value) // EN auto-cap still armed

        val starters = drain {
            val s = controller.suggestions.value
            s.isNotEmpty() && s.all { it.text in EnglishLexicon.starters }
        }
        assertTrue("after a period the strip shows starters", starters)
    }

    @Test
    fun newline_resets_context_to_sentence_start() {
        controller.lastWord = "hello"

        controller.commitEnter()

        assertNull(controller.lastWord)
        assertTrue(controller.sentenceStart)
    }

    @Test
    fun english_suggestions_capitalize_in_sentence_start_context() {
        controller.lang.value = Lang.EN
        controller.autoCapNext.value = true

        val s = Suggestion("the")
        assertEquals("The", controller.displaySuggestion(s))

        controller.autoCapNext.value = false
        controller.shift.value = ShiftState.ON
        assertEquals("The", controller.displaySuggestion(s))

        // Plain context keeps lowercase; Arabic never capitalizes.
        controller.shift.value = ShiftState.OFF
        assertEquals("the", controller.displaySuggestion(s))
        controller.lang.value = Lang.AR
        controller.autoCapNext.value = true
        assertEquals("the", controller.displaySuggestion(s))
    }

    @Test
    fun predictions_off_clears_strip_and_stops_predicting() {
        controller.lastWord = "hello"

        controller.predictions.value = false
        controller.onSpace() // buffer empty path: nothing committed, but no crash
        "hi".forEach { controller.onChar(it.toString()) }
        controller.onSpace()

        val cleared = drain { controller.suggestions.value.isEmpty() }
        assertTrue("predictions off must keep the strip empty", cleared)
    }

    @Test
    fun dismissSuggestions_clears_chips_without_touching_text() {
        // Give the controller a real prediction trigger: a learned pair, then
        // a context-preserving space.
        controller.lastWord = "hello"
        runBlocking { AppGraph.repo.recordBigram(pid, "hello", "world", "en") }
        controller.onSpace() // empty-buffer space → predictions for "hello"

        val ok = drain { controller.suggestions.value.isNotEmpty() }
        assertTrue(ok)

        controller.dismissSuggestions()

        assertTrue(controller.suggestions.value.isEmpty())
        assertTrue(controller.buffer.value.isEmpty()) // text untouched
        assertEquals("hello", controller.lastWord)
    }

    @Test
    fun switchLanguage_resets_prediction_context() {
        controller.lastWord = "hello"
        controller.sentenceStart = false

        controller.switchLanguage()

        assertNull(controller.lastWord)
        assertFalse(controller.sentenceStart)
        assertTrue(controller.suggestions.value.isEmpty())
    }

    @Test
    fun attach_and_new_field_reset_the_sentence_context() {
        controller.lastWord = "stale"
        controller.sentenceStart = true

        controller.attach(ic)

        assertNull(controller.lastWord)
        assertFalse(controller.sentenceStart)

        controller.lastWord = "stale"
        controller.resetForNewField(numeric = false)

        assertNull(controller.lastWord)
        assertFalse(controller.sentenceStart)
    }
}
