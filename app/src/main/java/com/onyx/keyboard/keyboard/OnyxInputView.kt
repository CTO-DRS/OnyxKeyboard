package com.onyx.keyboard.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts Jetpack Compose content inside an InputMethodService window.
 * Bridges the three owners Compose needs (Lifecycle, SavedStateRegistry,
 * ViewModelStore) onto a plain FrameLayout — the standard recipe for
 * Compose-driven IMEs.
 */
class OnyxInputView(context: Context) :
    FrameLayout(context),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedStateRegistryController.performRestore(null)
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)

        // Android 15 (targetSdk 35) enforces edge-to-edge for IME windows too:
        // the window extends under the system bars, so pad the keyboard content
        // by navigation-bar + display-cutout insets. On older OS versions the
        // IME window already stops above the nav bar and reports zero insets,
        // which keeps behaviour unchanged.
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }

        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun setContent(content: @Composable () -> Unit) {
        removeAllViews()
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent(content)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        addView(view)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // CRITICAL FIX (crash when the user wants to type):
        // Compose resolves the Recomposer from the WINDOW ROOT by walking UP
        // from the framework's root input view. A ViewTreeLifecycleOwner set
        // only on this view (a descendant) is unreachable there, and Compose
        // throws "IllegalStateException: ViewTreeLifecycleOwner not found from…"
        // the moment the keyboard window is shown, killing the IME process.
        // Self-heal here: parent attach callbacks run BEFORE children attach,
        // so this always runs before ComposeView starts its composition.
        runCatching {
            val root = rootView
            if (root !== this && root.findViewTreeLifecycleOwner() == null) {
                root.setViewTreeLifecycleOwner(this)
                root.setViewTreeViewModelStoreOwner(this)
                root.setViewTreeSavedStateRegistryOwner(this)
            }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    fun dispose() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
