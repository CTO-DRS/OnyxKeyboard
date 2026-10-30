package com.onyx.keyboard

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.KeyboardScale
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.SelectionMath
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.0.0-area tests: the RESIZE panel's global key scale (مساحة اللوحة),
 * the TEXT SELECTION mode (select-all / copy / cut / extend), the
 * double-space→period user switch, and the backspace-swipe threshold math.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxScaleSelectionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

    /** Records every IME operation the controller performs. */
    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val selections = mutableListOf<Pair<Int, Int>>()
        var fakeExtracted: ExtractedText? = null
        var fakeSelected: String? = null

        override fun getExtractedText(
            request: ExtractedTextRequest?,
            flags: Int
        ): ExtractedText? = fakeExtracted

        override fun getSelectedText(flags: Int): CharSequence? = fakeSelected

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            selections += start to end
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
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ------------------------------------------------- KeyboardScale (pure)

    @Test
    fun scale_clamps_into_range_and_snaps_to_grid() {
        assertEquals(0.80f, KeyboardScale.clamp(0.1f))
        assertEquals(1.30f, KeyboardScale.clamp(9f))
        assertEquals(1.0f, KeyboardScale.clamp(1.0f))
        assertEquals(1.15f, KeyboardScale.clamp(1.16f))   // snaps to 5% grid
        assertEquals(0.85f, KeyboardScale.clamp(0.849f))
        assertEquals(KeyboardScale.DEFAULT, KeyboardScale.clamp(Float.NaN))
        assertEquals(KeyboardScale.DEFAULT, KeyboardScale.clamp(Float.POSITIVE_INFINITY))
    }

    @Test
    fun scale_percent_renders_whole_numbers() {
        assertEquals(100, KeyboardScale.percent(1.0f))
        assertEquals(85, KeyboardScale.percent(0.85f))
        assertEquals(130, KeyboardScale.percent(1.34f)) // clamped
    }

    // ------------------------------------------------- SelectionMath (pure)

    @Test
    fun selection_extend_right_grows_focus_around_anchor() {
        val r = SelectionMath.extend(anchor = 5, focus = 5, delta = 2, textLength = 20)
        assertEquals(5, r.anchor)
        assertEquals(7, r.focus)
        assertEquals(5, r.start)
        assertEquals(7, r.end)
    }

    @Test
    fun selection_extend_left_crossing_anchor_normalizes_bounds() {
        val r = SelectionMath.extend(anchor = 5, focus = 8, delta = -5, textLength = 20)
        assertEquals(5, r.anchor)
        assertEquals(3, r.focus)
        assertEquals(3, r.start)   // focus crossed the anchor — span flips
        assertEquals(5, r.end)
    }

    @Test
    fun selection_extend_clamps_to_document_bounds() {
        val r = SelectionMath.extend(anchor = 0, focus = 0, delta = -50, textLength = 10)
        assertEquals(0, r.focus)
        assertEquals(0, r.start)
        assertEquals(0, r.end)

        val r2 = SelectionMath.extend(anchor = 2, focus = 2, delta = +99, textLength = 10)
        assertEquals(10, r2.focus)
        assertEquals(2, r2.start)
        assertEquals(10, r2.end)
    }

    @Test
    fun swipe_threshold_decides_word_delete() {
        assertTrue(SelectionMath.swipeDeletesWord(dragPx = -80f, thresholdPx = 64f))
        assertTrue(SelectionMath.swipeDeletesWord(dragPx = 70f, thresholdPx = 64f))
        assertFalse(SelectionMath.swipeDeletesWord(dragPx = 30f, thresholdPx = 64f))
        assertFalse(SelectionMath.swipeDeletesWord(dragPx = -64f * 0.9f, thresholdPx = 64f))
    }

    // ------------------------------------------------- scale via controller

    @Test
    fun setKeyboardScale_snaps_and_persists() {
        controller.setKeyboardScale(1.2f)
        assertEquals(1.2f, controller.keyboardScale.value)

        controller.setKeyboardScale(42f)
        assertEquals(KeyboardScale.MAX, controller.keyboardScale.value)

        runBlocking {
            assertEquals(
                KeyboardScale.MAX.toString(),
                AppGraph.repo.setting(OnyxRepository.KEY_KEYBOARD_SCALE, "x")
            )
        }
    }

    @Test
    fun double_space_switch_toggles_and_persists() {
        assertTrue(controller.doubleSpacePeriod.value)
        controller.setDoubleSpacePeriod(false)
        assertFalse(controller.doubleSpacePeriod.value)
        runBlocking {
            assertEquals("false", AppGraph.repo.setting(OnyxRepository.KEY_DOUBLE_SPACE, "x"))
        }
        controller.setDoubleSpacePeriod(true)
        assertTrue(controller.doubleSpacePeriod.value)
    }

    // ------------------------------------------------- selection via controller

    @Test
    fun select_all_pins_full_span() {
        ic.fakeExtracted = ExtractedText().apply {
            text = "hello world"
            selectionStart = 3
            selectionEnd = 3
        }

        controller.toggleSelectionMode()
        assertTrue(controller.selectionMode.value)
        controller.selectionSelectAll()

        assertEquals(listOf(0 to 11), ic.selections)
    }

    @Test
    fun extend_selection_pins_anchor_then_moves_focus() {
        ic.fakeExtracted = ExtractedText().apply {
            text = "hello world"
            selectionStart = 5
            selectionEnd = 5
        }
        controller.toggleSelectionMode()

        controller.selectionExtend(1)
        assertEquals(listOf(5 to 6), ic.selections)

        controller.selectionExtend(1)
        assertEquals(listOf(5 to 6, 5 to 7), ic.selections)

        // Shrink back over the anchor: bounds normalize, span flips.
        controller.selectionExtend(-4)
        assertEquals(listOf(5 to 6, 5 to 7, 3 to 5), ic.selections)
    }

    @Test
    fun copy_puts_selection_on_system_clipboard() {
        ic.fakeSelected = "onyx span"
        controller.toggleSelectionMode()
        controller.selectionCopy()

        val cm = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("onyx span", cm.primaryClip?.getItemAt(0)?.text?.toString())
        // Copy keeps the mode armed — the user may still cut/paste/extend.
        assertTrue(controller.selectionMode.value)
    }

    @Test
    fun cut_commits_empty_and_exits_the_mode() {
        ic.fakeSelected = "delete me"
        controller.toggleSelectionMode()
        controller.selectionCut()

        assertTrue(ic.committed.contains(""))
        assertFalse(controller.selectionMode.value)
    }

    @Test
    fun paste_commits_clipboard_over_the_span() {
        val cm = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("t", "pasted text"))
        controller.toggleSelectionMode()

        controller.selectionPaste()

        assertTrue(ic.committed.contains("pasted text"))
    }

    @Test
    fun backspace_with_active_selection_deletes_it_whole_and_exits() {
        ic.fakeSelected = "whole"
        controller.toggleSelectionMode()

        controller.onBackspace()

        assertTrue(ic.committed.contains(""))
        assertFalse(controller.selectionMode.value)
    }

    @Test
    fun typing_any_character_collapses_the_selection_mode() {
        controller.toggleSelectionMode()
        assertTrue(controller.selectionMode.value)

        controller.onChar("س")

        assertFalse(controller.selectionMode.value)
    }

    @Test
    fun reset_for_new_field_exits_selection_mode() {
        controller.toggleSelectionMode()
        assertTrue(controller.selectionMode.value)

        controller.resetForNewField(numeric = false)

        assertFalse(controller.selectionMode.value)
        assertEquals(KeyboardPage.LETTERS, controller.page.value)
    }

    @Test
    fun toggle_twice_returns_to_plain_typing() {
        controller.toggleSelectionMode()
        controller.toggleSelectionMode()
        assertFalse(controller.selectionMode.value)
    }
}
