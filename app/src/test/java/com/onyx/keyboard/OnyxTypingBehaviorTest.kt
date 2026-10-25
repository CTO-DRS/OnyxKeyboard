package com.onyx.keyboard

import android.content.Context
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
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
 * Behaviour tests for the v1.1.0 typing upgrades:
 *  - smart Enter (editor action vs newline)
 *  - EN auto-capitalization (sentence terminators + newline)
 *  - cursor movement keys
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxTypingBehaviorTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

    /** Records every IME operation the controller performs. */
    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val composing = mutableListOf<String>()
        val selections = mutableListOf<Pair<Int, Int>>()
        val editorActions = mutableListOf<Int>()
        var fakeBeforeCursor: String = ""
        var fakeExtracted: ExtractedText? = null

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = fakeBeforeCursor

        override fun getExtractedText(
            request: ExtractedTextRequest?,
            flags: Int
        ): ExtractedText? = fakeExtracted

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing += text?.toString().orEmpty()
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            selections += start to end
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            editorActions += actionCode
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
        controller.lang.value = Lang.EN
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ------------------------------------------------------------- smart enter

    @Test
    fun enter_performs_editor_action_when_field_has_one() {
        controller.resetForNewField(numeric = false, editorActionId = EditorInfo.IME_ACTION_SEND)

        controller.commitEnter()

        assertEquals(listOf(EditorInfo.IME_ACTION_SEND), ic.editorActions)
        assertFalse("no literal newline must be committed", ic.committed.contains("\n"))
    }

    @Test
    fun enter_inserts_newline_and_arms_autocap_on_plain_fields() {
        controller.resetForNewField(numeric = false, editorActionId = 0)
        controller.autoCapNext.value = false

        controller.commitEnter()

        assertEquals(listOf("\n"), ic.committed)
        assertTrue(controller.autoCapNext.value)

        controller.onChar("a")
        assertEquals("A", controller.buffer.value)
        assertFalse(controller.autoCapNext.value)
    }

    @Test
    fun resetForNewField_numeric_field_forces_plain_newline_enter() {
        controller.resetForNewField(numeric = true, editorActionId = EditorInfo.IME_ACTION_GO)

        controller.commitEnter()

        assertEquals(listOf("\n"), ic.committed)
        assertEquals(KeyboardPage.SYMBOLS, controller.page.value)
    }

    // ------------------------------------------------------------- auto-cap

    @Test
    fun sentence_terminator_arms_autocap_for_english_only() {
        controller.commitText(".")
        assertTrue(controller.autoCapNext.value)
        controller.onChar("h")
        assertEquals("H", controller.buffer.value)

        // Arabic must never be auto-capitalized.
        controller.lang.value = Lang.AR
        controller.commitText(".")
        assertFalse(controller.autoCapNext.value)
    }

    @Test
    fun ordinary_characters_do_not_arm_autocap() {
        controller.commitText("x")
        assertFalse(controller.autoCapNext.value)
    }

    // ------------------------------------------------------------- cursor keys

    @Test
    fun moveCursor_left_uses_before_cursor_fallback() {
        ic.fakeBeforeCursor = "abc"

        controller.moveCursor(-1)

        assertEquals(listOf(2 to 2), ic.selections)
    }

    @Test
    fun moveCursor_clamps_at_text_start() {
        ic.fakeBeforeCursor = ""

        controller.moveCursor(-5)

        assertEquals(listOf(0 to 0), ic.selections)
    }

    @Test
    fun moveCursor_prefers_extracted_text_selection_edges() {
        val et = ExtractedText()
        et.selectionStart = 4
        et.selectionEnd = 7
        ic.fakeExtracted = et

        // Moving left walks from the selection START…
        controller.moveCursor(-1)
        assertEquals(listOf(3 to 3), ic.selections)

        // …and moving right resumes from the selection END.
        controller.moveCursor(1)
        assertEquals(listOf(3 to 3, 8 to 8), ic.selections)
    }

    @Test
    fun moveCursor_without_connection_is_a_noop() {
        controller.detach()
        controller.moveCursor(-1)
        assertTrue(ic.selections.isEmpty())
    }
}
