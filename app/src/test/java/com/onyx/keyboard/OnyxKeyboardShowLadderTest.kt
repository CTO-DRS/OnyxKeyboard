package com.onyx.keyboard

import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.OnyxFallbackScreen
import com.onyx.keyboard.keyboard.OnyxInputView
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxKeyboardScreen
import com.onyx.keyboard.service.OnyxIME
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * THE IME SURFACE FALLBACK LADDER + LIFECYCLE ARMOR — "the keyboard never
 * appears" regression tests (versionCode 13).
 *
 * onCreateInputView walks: cached view -> full surface -> MINIMAL surface
 * (same controller, tiny code path) -> clear Arabic error surface with
 * NOTHING cached (so the next field retries the whole ladder). A transient
 * compose failure on some device must never leave the user without a way to
 * type — and must never become permanent.
 *
 * The lifecycle callbacks (onStartInput / onStartInputView / onFinishInput)
 * are crash-armored: an escaping exception aborts InputMethodService's
 * show-window sequence, which also reads as "the keyboard never appears".
 *
 * NOTE: this class intentionally NEVER composes through the compose rule —
 * the real OnyxIME service (with its Room collectors) runs here, and only
 * view-level interactions are exercised. Compose-idle-sensitive surfaces are
 * covered service-free in [OnyxKeyboardShowFixTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxKeyboardShowLadderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var ime: OnyxIME
    private lateinit var controller: OnyxKeyboardController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        runBlocking { AppGraph.repo.ensureSeeded() }
        ime = Robolectric.setupService(OnyxIME::class.java)
        val f = OnyxIME::class.java.getDeclaredField("controller")
        f.isAccessible = true
        controller = f.get(ime) as OnyxKeyboardController
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun full_surface_failure_falls_back_to_minimal_surface() {
        composeRule.runOnUiThread {
            ime.fullSurfaceFactory = {
                error("simulated full-surface compose failure")
            }
            ime.minimalSurfaceFactory = {
                // A real OnyxInputView WITHOUT composition: the ladder
                // mechanics only care about the VIEW TYPES. Detached
                // compositions (a view that never joins a window) would
                // starve their Recomposer of frames and poison the shared
                // compose-idling machinery for the whole suite.
                OnyxInputView(ime)
            }
            val view = ime.onCreateInputView()
            // The ladder must hand back a REAL keyboard (OnyxInputView), not
            // an error label, because the minimal surface still "composes".
            assert(view is OnyxInputView) {
                "expected the minimal OnyxInputView, got ${view.javaClass.simpleName}"
            }
        }
        composeRule.waitForIdle()
        // Restore for other tests.
        composeRule.runOnUiThread {
            val f = OnyxIME::class.java.getDeclaredField("inputView")
            f.isAccessible = true
            f.set(ime, null)
            ime.fullSurfaceFactory = { OnyxInputView(ime) }
        }
    }

    @Test
    fun ladder_retries_on_next_field_after_total_failure() {
        composeRule.runOnUiThread {
            ime.fullSurfaceFactory = { error("boom-full") }
            ime.minimalSurfaceFactory = { error("boom-minimal") }

            val first = ime.onCreateInputView()
            // Total failure -> the Arabic error surface, and NOTHING cached.
            assert(first is android.widget.LinearLayout) {
                "expected the Arabic error surface, got ${first.javaClass.simpleName}"
            }
            val title = (first as android.view.ViewGroup).let { vg ->
                (0 until vg.childCount).map { vg.getChildAt(it) }
                    .filterIsInstance<TextView>().firstOrNull()?.text?.toString().orEmpty()
            }
            assert(title.contains("Onyx")) { "expected the Arabic error title, got: $title" }
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            // Restore the factories — the NEXT field must retry the ladder
            // and now succeed (nothing was cached by the failure path).
            ime.fullSurfaceFactory = { OnyxInputView(ime) }
            ime.minimalSurfaceFactory = { OnyxInputView(ime) }
            val second = ime.onCreateInputView()
            assert(second is OnyxInputView) {
                "ladder must retry on the next field, got ${second.javaClass.simpleName}"
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun lifecycle_callbacks_survive_a_poisoned_editor_state() {
        composeRule.runOnUiThread {
            // No-compose factory: lifecycle armor must not depend on compose.
            ime.fullSurfaceFactory = { OnyxInputView(ime) }
            val info = EditorInfo() // all defaults — harmless
            ime.onCreateInputView()
            ime.onStartInput(info, false)
            ime.onStartInputView(info, false)
            ime.onStartInputView(null, true)   // poisoned: null editor info
            ime.onStartInput(null, true)
            ime.onFinishInput()
        }
        composeRule.waitForIdle()
        // No exception escaped = the keyboard window can always be shown.
    }
}
