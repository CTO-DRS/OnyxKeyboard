package com.onyx.keyboard

import android.app.Dialog
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import com.onyx.keyboard.service.OnyxIME

/**
 * LEVEL 4 — reproduces the REAL InputMethodService window hierarchy:
 *
 *   DecorView
 *    └─ content FrameLayout (android.R.id.content)
 *        └─ mRootView  (framework input_method layout root, no owner)
 *            └─ mInputFrame (android.R.id.inputArea)
 *                └─ OnyxInputView   ← owner tags (only here = THE BUG)
 *                    └─ ComposeView ← composition starts here
 *
 * Compose (ui 1.7.x) resolves the Recomposer from the window ROOT by walking
 * UP from mRootView. Proven crash before the fix:
 *   IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.LinearLayout{…}
 * thrown at ComposeView.onAttachedToWindow → the IME process dies the moment
 * the user wants to type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxImeWindowHierarchyTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        runBlocking { AppGraph.repo.ensureSeeded() }
        controller = OnyxKeyboardController(app, scope, AppGraph.repo)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Builds the exact IME window hierarchy around the given content view factory. */
    private fun buildRawImeHierarchy(composeContent: () -> android.view.View): Dialog {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dialog = Dialog(context)

        val mRootView = LinearLayout(context).apply { id = 0x00660001 }
        val mInputFrame = FrameLayout(context).apply { id = android.R.id.inputArea }
        mRootView.addView(
            mInputFrame,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        mInputFrame.addView(
            composeContent(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        dialog.setContentView(mRootView) // IMS: mWindow.setContentView(mRootView)
        return dialog
    }

    /**
     * ROOT-CAUSE DOCUMENTATION: a bare ComposeView inside the IME hierarchy
     * MUST crash with "ViewTreeLifecycleOwner not found" — this is the exact
     * exception users saw when the keyboard appeared.
     */
    @Test
    fun bareComposeView_in_ime_hierarchy_crashes_exactly_as_on_device() {
        val dialog = buildRawImeHierarchy {
            ComposeView(ApplicationProvider.getApplicationContext()).apply {
                setContent { Text("onyx") }
            }
        }
        val crash = runCatching {
            dialog.show()
            ShadowLooper.idleMainLooper()
        }.exceptionOrNull()

        assertNotNull("expected the ViewTreeLifecycleOwner crash", crash)
        assertTrue(
            "unexpected crash: $crash",
            crash is IllegalStateException &&
                (crash.message ?: "").contains("ViewTreeLifecycleOwner not found")
        )
        dialog.dismiss()
    }

    /**
     * THE FIX: OnyxInputView (with the self-healing onAttachedToWindow) inside
     * the same hierarchy must compose WITHOUT crashing.
     */
    @Test
    fun onyxInputView_fixed_recipe_composes_without_crash() {
        val dialog = buildRawImeHierarchy {
            OnyxInputView(ApplicationProvider.getApplicationContext()).apply {
                setContent { OnyxKeyboardScreen(controller) }
            }
        }
        // Must NOT throw.
        dialog.show()
        ShadowLooper.idleMainLooper()

        val decor = dialog.window!!.decorView
        // The self-heal installed the owners on the window root.
        assertNotNull(
            "window root must carry the ViewTreeLifecycleOwner after attach",
            decor.findViewTreeLifecycleOwner()
        )
        assertNotNull("ComposeView must exist", decor.findComposeView())
        dialog.dismiss()
    }

    /**
     * The REAL service path: OnyxIME.onCreateInputView() must install the
     * owners on the IME window's decor view proactively.
     */
    @Test
    fun onyxIMe_onCreateInputView_installs_owners_on_window_root() {
        val ime = Robolectric.setupService(OnyxIME::class.java)
        val view = ime.onCreateInputView()
        assertNotNull(view)

        val decor = ime.window.window!!.decorView
        assertNotNull(
            "decor view must carry the ViewTreeLifecycleOwner",
            decor.findViewTreeLifecycleOwner()
        )
        // And the input view itself still carries them for its own subtree.
        assertNotNull(view.findViewTreeLifecycleOwner())
    }

    /**
     * REGRESSION (keyboard floating at the top of the screen):
     *
     * The decorative ParticleField used Modifier.fillMaxSize() inside the
     * root Box. Inside an IME window the max constraints equal the WHOLE
     * display, so the Canvas expanded the Box to full-screen height — the
     * IME window grew to the full screen, keys rendered at the top and a
     * huge empty particle-filled area sat below (device screenshot 2026-10-24).
     *
     * Fix: matchParentSize() (overlay only, not measured by the Box).
     * Property asserted here: enabling particles must NOT change the
     * measured height of the input view, and the keyboard must wrap its
     * content (far below the display height).
     */
    @Test
    fun particles_overlay_never_inflates_ime_window_height() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val metrics = context.resources.displayMetrics
        val screenHeight = metrics.heightPixels

        fun measuredHeightWith(particles: Boolean): Int {
            controller.particles.value = particles
            val inputView = OnyxInputView(context).apply {
                setContent { OnyxKeyboardScreen(controller) }
            }
            val dialog = buildRawImeHierarchy { inputView }
            dialog.show()
            ShadowLooper.idleMainLooper()
            val h = inputView.measuredHeight
            dialog.dismiss()
            assertTrue("input view must be laid out", h > 0)
            return h
        }

        val withParticles = measuredHeightWith(particles = true)
        val withoutParticles = measuredHeightWith(particles = false)

        assertTrue(
            "particle overlay must not change keyboard height " +
                "(with=${withParticles}px, without=${withoutParticles}px)",
            kotlin.math.abs(withParticles - withoutParticles) <= 8
        )
        assertTrue(
            "keyboard must wrap content, not fill the screen " +
                "(measured=${withParticles}px, screen=${screenHeight}px)",
            withParticles < screenHeight * 0.75
        )
    }

    private fun android.view.View.findComposeView(): ComposeView? {
        if (this is ComposeView) return this
        val group = this as? ViewGroup ?: return null
        for (i in 0 until group.childCount) {
            group.getChildAt(i).findComposeView()?.let { return it }
        }
        return null
    }
}
