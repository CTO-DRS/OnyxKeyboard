package com.onyx.keyboard

import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.OnyxInputView
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
 * Diagnostic reproduction tests for the "app closes when the user wants to
 * type" crash. Simulates:
 *  1. Application init (AppGraph) like OnyxApp.onCreate
 *  2. Controller creation like OnyxIME.onCreate
 *  3. The exact Compose surface composed inside the IME window
 *  4. Real key presses on the letters page (onChar path)
 *  5. Space press (commitWord → findShortcut SQL with the `trigger` column)
 *  6. The OnyxInputView attach/detach lifecycle dance
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxImeCrashReproTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var controller: OnyxKeyboardController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        val repo = AppGraph.repo
        runBlocking { repo.ensureSeeded() }
        controller = OnyxKeyboardController(app, scope, repo)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Mirrors OnyxIME.onCreateInputView + attach lifecycle exactly. */
    private fun buildInputView(): OnyxInputView {
        val host = FrameLayout(ApplicationProvider.getApplicationContext())
        val inputView = OnyxInputView(ApplicationProvider.getApplicationContext())
        inputView.setContent { OnyxKeyboardScreen(controller) }
        host.addView(
            inputView,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        // Simulate window attach like InputMethodService does for the input view.
        composeRule.runOnUiThread {
            val m = OnyxInputView::class.java.getDeclaredMethod("onAttachedToWindow")
            m.isAccessible = true
            m.invoke(inputView)
        }
        return inputView
    }

    @Test
    fun keyboardSurface_composes_and_renders() {
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        composeRule.waitForIdle()
        // Arabic letters page is the default; at least one key must exist.
        composeRule.onNodeWithText("ض").assertExists()
    }

    @Test
    fun typing_letters_does_not_crash() {
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        composeRule.waitForIdle()

        // Tap Arabic keys exactly like a user wanting to type.
        composeRule.onNodeWithText("م").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ر").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ح").performClick()
        composeRule.waitForIdle()

        assert(controller.buffer.value == "مرح") {
            "buffer should be مرح but was ${controller.buffer.value}"
        }
    }

    @Test
    fun space_after_word_triggers_commitWord_and_findShortcut_query() {
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("م").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ر").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ح").performClick()
        composeRule.waitForIdle()

        // Space → scope.launch { commitWord("مرح") → repo.findShortcut(...) }
        // (the shortcuts table has a column named `trigger`)
        composeRule.runOnUiThread { controller.onSpace() }
        composeRule.waitForIdle()
        Thread.sleep(500) // let the launched coroutine finish
        composeRule.waitForIdle()
    }

    @Test
    fun backspace_word_delete_does_not_crash() {
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            controller.onChar("ت")
            controller.onBackspaceWord()
            controller.onBackspace()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun inputView_attach_detach_attach_cycle_does_not_crash() {
        val view = buildInputView()
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            // detach (keyboard hidden)
            val d = OnyxInputView::class.java.getDeclaredMethod("onDetachedFromWindow")
            d.isAccessible = true
            d.invoke(view)
            // re-attach (keyboard shown again)
            val a = OnyxInputView::class.java.getDeclaredMethod("onAttachedToWindow")
            a.isAccessible = true
            a.invoke(view)
        }
        composeRule.waitForIdle()

        val state = view.lifecycle.currentState
        assert(state.isAtLeast(Lifecycle.State.RESUMED)) {
            "lifecycle should be RESUMED after re-attach but was $state"
        }
    }

    @Test
    fun suggestions_engine_runs_against_room() {
        val repo = AppGraph.repo
        runBlocking {
            val profile = repo.currentProfile()
            assert(profile != null) { "seeded profile missing" }
            repo.recordWord(profile!!.id, "مرحبا", "ar")
            val list = repo.prefixMatch(profile.id, "مر", 10)
            assert(list.isNotEmpty()) { "prefix match should find مرحبا" }
        }
    }
}
