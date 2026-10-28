package com.onyx.keyboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.OnyxFallbackScreen
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxKeyboardScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REGRESSION TESTS — "the keyboard does not appear" (versionCode 13 fix):
 *
 * 1. The emoji SEARCH panel used to render the letters keyboard as a SIBLING
 *    of the panel inside AnimatedContent's Box — the two OVERLAPPED and the
 *    search became unusable. The fix stacks them in one Column; the geometry
 *    test pins it (letters strictly BELOW the search field).
 *
 * 2. The minimal fallback surface (OnyxFallbackScreen) must remain typeable.
 *
 * DETERMINISM CONTRACT (this class hung intermittently in full-suite runs):
 *  - particles are OFF (ParticleField runs an INFINITE animation — the same
 *    prescription OnyxImeWindowHierarchyTest uses for raw compositions);
 *  - the compose main clock is FROZEN (autoAdvance=false) and frames are
 *    advanced MANUALLY — the documented way to keep Espresso's idle detection
 *    out of the picture when infinite transitions or full-suite load are in
 *    play. Every step is: mutate state -> advance a frame -> waitForIdle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxKeyboardShowFixTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var controller: OnyxKeyboardController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        runBlocking { AppGraph.repo.ensureSeeded() }

        // Fresh, service-free controller — no IME service in this class.
        controller = OnyxKeyboardController(
            ApplicationProvider.getApplicationContext(), scope, AppGraph.repo
        )
        // No infinite animations: the particle field is the only one on the
        // keyboard surface, and frozen clocks + infinite animations never idle.
        controller.particles.value = false
        composeRule.mainClock.autoAdvance = false
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** One deterministic frame: apply pending recomposition + layout. */
    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    // ------------------------------------------------------------- 1) emoji search stacking

    @Test
    fun emoji_search_letters_render_strictly_below_the_search_field() {
        // Set the target state BEFORE composing: with a frozen main clock an
        // AnimatedContent page switch never finishes, so the outgoing letters
        // page and the incoming emoji panel would BOTH stay alive and duplicate
        // every letter node. Composing directly in the target state yields a
        // single, settled tree.
        composeRule.runOnUiThread {
            controller.setPage(KeyboardPage.EMOJI)
            controller.beginEmojiSearch()
        }
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        frame()
        frame()

        // Both surfaces must exist: the search field (hint) and a letter key.
        val hintText = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.emoji_search_hint)
        val hintY = composeRule.onNodeWithText(hintText)
            .fetchSemanticsNode().positionInRoot.y
        val keyY = composeRule.onNodeWithText("ض")
            .fetchSemanticsNode().positionInRoot.y

        // THE BUG: with the old sibling layout the letters keyboard was drawn
        // ON TOP of the search field (same Y band). The fix puts the letters
        // keyboard BELOW the panel — a clear positive offset is expected.
        assert(keyY > hintY + 60f) {
            "letters keyboard (y=$keyY) must render BELOW the search field (y=$hintY)"
        }
    }

    @Test
    fun emoji_search_typing_fills_the_query_and_results_follow() {
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        frame()

        composeRule.runOnUiThread {
            controller.setPage(KeyboardPage.EMOJI)
            controller.beginEmojiSearch()
            controller.onChar("ق")
            controller.onChar("ل")
            controller.onChar("ب")
        }
        frame()
        frame()

        assert(controller.emojiSearch.value) { "search must stay open while typing" }
        assert(controller.internalCapture.value == "قلب") {
            "query buffer was ${controller.internalCapture.value}"
        }
        // The heart emoji must surface for the Arabic query "قلب".
        composeRule.onNodeWithText("❤️").assertExists()
    }

    @Test
    fun emoji_search_cleanup_on_field_change() {
        composeRule.runOnUiThread {
            controller.setPage(KeyboardPage.EMOJI)
            controller.beginEmojiSearch()
            controller.onChar("ق")
        }
        // A new field must reset BOTH the capture and the search flag —
        // a stale flag would silently swallow the next field's keystrokes.
        composeRule.runOnUiThread { controller.resetForNewField(false) }
        assert(!controller.emojiSearch.value) { "emojiSearch must reset on field change" }
        assert(controller.internalCapture.value == null) { "capture must reset" }
    }

    @Test
    fun minimal_fallback_screen_types_like_the_real_keyboard() {
        composeRule.setContent { OnyxFallbackScreen(controller) }
        frame()

        // The default letters page is Arabic — tap a real letter.
        composeRule.onNodeWithText("م").performClick()
        frame()
        assert(controller.buffer.value == "م") {
            "fallback key must commit through the controller, buffer was ${controller.buffer.value}"
        }
    }
}
