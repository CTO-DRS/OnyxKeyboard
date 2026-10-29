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
        // ParticleField runs an INFINITE animation; in the raw (non
        // composeRule) dialog path ShadowLooper.idleMainLooper() would keep
        // pumping its frames forever, so the static shell is composed here.
        controller.particles.value = false
        val input = OnyxInputView(ApplicationProvider.getApplicationContext()).apply {
            setContent { OnyxKeyboardScreen(controller) }
        }
        val dialog = buildRawImeHierarchy { input }
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
        // SUITE HYGIENE: this dialog composition must NOT outlive the test —
        // a leftover live Recomposer pumps Choreographer frames in this shared
        // JVM and (combined with other raw compositions) drives every later
        // composeRule-based class into endless AppNotIdleException waits.
        // dispose() alone relies on Robolectric's paused-looper dispatch timing,
        // so the composition is killed DIRECTLY first.
        decor.findComposeView()?.disposeComposition()
        input.dispose()
        ShadowLooper.idleMainLooper()
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

        // SUITE HYGIENE: the service (its scope, receiver, composed input
        // view) must not outlive the test — onDestroy disposes the input view
        // and cancels the controller scope, leaving no live Recomposer behind
        // to pump frames into later classes.
        ime.onDestroy()
        ShadowLooper.idleMainLooper()
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
