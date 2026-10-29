package com.onyx.keyboard

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.OneHand
import com.onyx.keyboard.keyboard.OnyxInputView
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxKeyboardScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * REGRESSION TESTS — "the keyboard renders at the TOP of the screen with a
 * huge empty surface below it" (the bug screenshot from v1.0.0-keyboard-fix):
 *
 * The IME window is MATCH_PARENT x WRAP_CONTENT, so the window height equals
 * whatever our input view MEASURES. Three Compose sites resolved
 * fillMaxHeight/fillMaxSize against the window's loose upper bound (the full
 * display height) — the cursor cluster and the space key inside the bottom
 * row, and the one-handed grip — inflating the measured height of the entire
 * keyboard to the display height. The keyboard then rendered pinned to the
 * top with a huge empty themed surface (particles) below it.
 *
 * The earlier "keyboard never appears" fixes were verified only with
 * view-type/lifecycle assertions, and the earlier particle matchParentSize
 * fix closed only ONE of the four loose-height paths — nothing ever MEASURED
 * the keyboard against realistic IME-window constraints, which is exactly
 * what these tests do:
 *
 *   host FrameLayout (MATCH_PARENT x WRAP_CONTENT)
 *     └─ OnyxInputView (the production input view)
 *          └─ ComposeView { OnyxKeyboardScreen(controller) }
 *
 * measured with width = display width (EXACTLY) and height = display height
 * (AT_MOST) — the same constraints the framework's input frame hands to the
 * input view. The bug made every measurement below equal the FULL display
 * height; the fix keeps each page at its natural keyboard height.
 *
 * Determinism contract (same as OnyxKeyboardShowFixTest): particles OFF (the
 * particle field runs an infinite animation that never idles) and the page
 * state is set BEFORE composing so no AnimatedContent transition is pending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class, qualifiers = "w411dp-h891dp-420dpi")
class OnyxKeyboardDockGeometryTest {

    private lateinit var controller: OnyxKeyboardController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        runBlocking { AppGraph.repo.ensureSeeded() }
        controller = OnyxKeyboardController(
            ApplicationProvider.getApplicationContext(), scope, AppGraph.repo
        )
        controller.particles.value = false
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /**
     * Measures the composed keyboard exactly like the IME's input frame does.
     *
     * The dialog + input view are ALWAYS disposed afterwards: the commit
     * history of this suite documents that a composition left running (a
     * "detached Recomposer") poisons the compose idling machinery for every
     * composeRule-based test class that runs later in the same JVM — the
     * exact failure that originally hung OnyxKeyboardShowFixTest. dispose()
     * fires ON_DESTROY, which makes DisposeOnViewTreeLifecycleDestroyed tear
     * the composition and its Recomposer down deterministically.
     */
    private fun measuredKeyboardHeight(content: @Composable () -> Unit): Int {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val input = OnyxInputView(ctx).apply { setContent(content) }
        val host = FrameLayout(ctx)
        host.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val dialog = Dialog(ctx)
        dialog.setContentView(host)
        dialog.show()
        ShadowLooper.idleMainLooper()

        val dm = ctx.resources.displayMetrics
        host.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST)
        )
        val height = host.measuredHeight
        dialog.dismiss()
        // BELT-AND-BRACES TEARDOWN (suite hygiene): disposeComposition() kills
        // the Recomposer DIRECTLY instead of relying on the ON_DESTROY event
        // reaching DisposeOnViewTreeLifecycleDestroyed through Robolectric's
        // paused-looper dispatch timing. A leftover live composition keeps
        // pumping Choreographer frames and poisons every composeRule-based
        // class that runs later in this shared JVM (proven: together with
        // OnyxImeWindowHierarchyTest's undisposed dialog, this class made
        // OnyxKeyboardShowFixTest/ShowLadderTest spin in AppNotIdleException
        // for the full 60 s budget). dispose() alone was not enough.
        input.findComposeView()?.disposeComposition()
        input.dispose()
        ShadowLooper.idleMainLooper()
        return height
    }

    private fun android.view.View.findComposeView(): androidx.compose.ui.platform.ComposeView? {
        if (this is androidx.compose.ui.platform.ComposeView) return this
        val group = this as? ViewGroup ?: return null
        for (i in 0 until group.childCount) {
            group.getChildAt(i).findComposeView()?.let { return it }
        }
        return null
    }

    private fun density(): Float =
        ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private fun screenHeightPx(): Int =
        ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.heightPixels

    // ---------------------------------------------------- the reported bug

    @Test
    fun letters_page_measures_docked_height_not_full_screen() {
        val h = measuredKeyboardHeight { OnyxKeyboardScreen(controller) }
        // Sanity: a real keyboard composed (not collapsed to zero). The
        // letters surface is toolbar + 4 key rows ≈ 270dp by design.
        assertTrue("keyboard must compose to a usable height, was $h", h > 150 * density())
        assertTrue(
            "letters keyboard measured $h px — must stay well below the " +
                "${screenHeightPx()} px display (docked, not stretched)",
            h < screenHeightPx() * 0.5f
        )
    }

    @Test
    fun symbols_page_measures_docked_height_not_full_screen() {
        controller.setPage(KeyboardPage.SYMBOLS)
        val h = measuredKeyboardHeight { OnyxKeyboardScreen(controller) }
        assertTrue("symbols keyboard must compose, was $h", h > 150 * density())
        assertTrue(
            "symbols keyboard measured $h px — must stay docked below 50% of the display",
            h < screenHeightPx() * 0.5f
        )
    }

    @Test
    fun one_handed_mode_measures_docked_height_not_full_screen() {
        controller.setOneHand(OneHand.LEFT)
        val h = measuredKeyboardHeight { OnyxKeyboardScreen(controller) }
        assertTrue("one-handed keyboard must compose, was $h", h > 150 * density())
        assertTrue(
            "one-handed keyboard measured $h px — the grip's fillMaxHeight must bind to " +
                "the keyboard's intrinsic height, not the display height",
            h < screenHeightPx() * 0.5f
        )
    }

    @Test
    fun one_handed_right_measures_docked_height_not_full_screen() {
        controller.setOneHand(OneHand.RIGHT)
        val h = measuredKeyboardHeight { OnyxKeyboardScreen(controller) }
        assertTrue(
            "one-handed (right) keyboard measured $h px — must stay docked",
            h < screenHeightPx() * 0.5f
        )
    }

    @Test
    fun emoji_search_page_never_exceeds_the_hard_cap() {
        controller.setPage(KeyboardPage.EMOJI)
        controller.beginEmojiSearch()
        val h = measuredKeyboardHeight { OnyxKeyboardScreen(controller) }
        val capPx = OnyxInputView.MAX_KEYBOARD_HEIGHT_DP * density()
        // The tallest legitimate surface must fit under the host ceiling…
        assertTrue(
            "emoji-search keyboard measured $h px — exceeds the ${capPx.toInt()} px hard cap",
            h <= capPx + 80 * density()
        )
        // …and must NEVER measure the full display again.
        assertTrue(
            "emoji-search keyboard measured $h px — must stay below the display",
            h < screenHeightPx() * 0.92f
        )
    }

    // ---------------------------------------------------- the last line of defense

    @Test
    fun hard_cap_neutralizes_hostile_fillMaxHeight_content() {
        // A hostile/accidental fillMaxHeight at the top of the hierarchy used
        // to pull the whole IME window to the display height. The onMeasure
        // cap must clamp ANY such content to the keyboard ceiling.
        val h = measuredKeyboardHeight { Box(Modifier.fillMaxHeight()) }
        val capPx = OnyxInputView.MAX_KEYBOARD_HEIGHT_DP * density()
        assertTrue(
            "hostile fillMaxHeight measured $h px — the onMeasure cap must clamp it to ${capPx.toInt()}",
            h <= capPx + 1
        )
        assertTrue("cap must still yield a real surface, was $h", h > 0)
    }

    @Test
    fun fallback_screen_measures_docked_height_not_full_screen() {
        val h = measuredKeyboardHeight { com.onyx.keyboard.keyboard.OnyxFallbackScreen(controller) }
        assertTrue("fallback keyboard must compose, was $h", h > 100 * density())
        assertTrue(
            "fallback keyboard measured $h px — must stay docked below 50% of the display",
            h < screenHeightPx() * 0.5f
        )
    }
}
