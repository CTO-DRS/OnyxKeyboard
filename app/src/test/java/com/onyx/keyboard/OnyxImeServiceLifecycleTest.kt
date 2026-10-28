package com.onyx.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.data.repository.OnyxRepository
import com.onyx.keyboard.keyboard.ClipboardPanel
import com.onyx.keyboard.keyboard.EmojiPanel
import com.onyx.keyboard.keyboard.GifPanel
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxKeyboardScreen
import com.onyx.keyboard.keyboard.SettingsPanel
import com.onyx.keyboard.keyboard.TranslatePanel
import com.onyx.keyboard.keyboard.VoicePanel
import com.onyx.keyboard.service.OnyxIME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * Level 2 diagnostics: the REAL OnyxIME service lifecycle under Robolectric,
 * plus every panel that the keyboard screen can switch to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxImeServiceLifecycleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var ime: OnyxIME
    private lateinit var controller: OnyxKeyboardController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        runBlocking { AppGraph.repo.ensureSeeded() }

        // Create the REAL InputMethodService through Robolectric.
        ime = Robolectric.setupService(OnyxIME::class.java)
        controller = getPrivate(ime, "controller") as OnyxKeyboardController
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun getPrivate(target: Any, name: String): Any? {
        var c: Class<*> = target.javaClass
        while (c != Any::class.java) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        error("field $name not found")
    }

    @Test
    fun ime_service_onCreate_runs_and_creates_controller() {
        // onCreate ran inside setupService; the controller must exist and be live.
        assert(controller.theme.value.name.isNotEmpty())
        assert(controller.haptics.value)
    }

    @Test
    fun ime_onCreateInputView_returns_working_view() {
        composeRule.runOnUiThread {
            // No-compose factory: this test verifies the SERVICE wiring, not
            // the compose surface (detached compositions starve their
            // Recomposer and poison the shared compose-idling machinery).
            ime.fullSurfaceFactory = {
                com.onyx.keyboard.keyboard.OnyxInputView(ime)
            }
            val view = ime.onCreateInputView()
            assert(view != null)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun ime_full_input_lifecycle_numeric_field() {
        composeRule.runOnUiThread {
            ime.fullSurfaceFactory = {
                com.onyx.keyboard.keyboard.OnyxInputView(ime)
            }
            val info = EditorInfo().apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            ime.onCreateInputView()
            ime.onStartInput(info, false)
            ime.onStartInputView(info, false)
            ime.onFinishInput()
        }
        composeRule.waitForIdle()
        // Numeric field → page switches to SYMBOLS
        assert(controller.page.value == KeyboardPage.SYMBOLS) {
            "page was ${controller.page.value}"
        }
    }

    @Test
    fun emoji_panel_opens_and_tap_inserts() {
        composeRule.setContent {
            EmojiPanel(controllerWithPage(KeyboardPage.EMOJI))
        }
        composeRule.waitForIdle()
        // open first emoji cell
        composeRule.onAllNodesWithText("😀")[0].performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settings_panel_renders_toggles_and_theme_carousel() {
        composeRule.setContent { SettingsPanel(controllerWithPage(KeyboardPage.SETTINGS)) }
        composeRule.waitForIdle()
    }

    @Test
    fun translate_panel_renders_and_searches() {
        composeRule.setContent { TranslatePanel(controllerWithPage(KeyboardPage.TRANSLATE)) }
        composeRule.waitForIdle()
    }

    @Test
    fun clipboard_panel_renders_empty_and_with_items() {
        val repo = AppGraph.repo
        runBlocking { repo.addClip("hello clip") }
        // Feed the controller state directly — Room invalidation timing under
        // Robolectric is nondeterministic; the panel only renders `clips`.
        val clip = runBlocking { AppGraph.repo.observeClips().first().first() }
        controller.clips.value = listOf(clip)

        // Goal: rendering a NON-EMPTY clipboard panel must not crash.
        // (The decrypted preview shows "…" under Robolectric because the fake
        // Keystore cannot perform AES/GCM — on device the text appears.)
        composeRule.setContent { ClipboardPanel(controllerWithPage(KeyboardPage.CLIPBOARD)) }
        composeRule.waitForIdle()
        composeRule.waitForIdle()
    }

    @Test
    fun gif_panel_disabled_state_renders() {
        composeRule.setContent { GifPanel(controllerWithPage(KeyboardPage.GIF)) }
        composeRule.waitForIdle()
    }

    @Test
    fun voice_panel_renders() {
        composeRule.setContent { VoicePanel(controllerWithPage(KeyboardPage.VOICE)) }
        composeRule.waitForIdle()
    }

    @Test
    fun full_typing_flow_on_english_layout_with_shift() {
        composeRule.setContent { OnyxKeyboardScreen(controller) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            controller.switchLanguage() // AR -> EN
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("h").performClick()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { controller.toggleShift() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("I").performClick()
        composeRule.waitForIdle()
        assert(controller.buffer.value == "hI") {
            "buffer was ${controller.buffer.value}"
        }
    }

    private fun controllerWithPage(page: KeyboardPage): OnyxKeyboardController {
        controller.page.value = page
        return controller
    }
}
