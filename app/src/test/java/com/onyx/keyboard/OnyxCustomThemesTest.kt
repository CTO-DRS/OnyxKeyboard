package com.onyx.keyboard

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.luminanceOf
import com.onyx.keyboard.keyboard.shuffledThemeColors
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Tests for the CUSTOM THEMES pack:
 *  - repo: saveCustomTheme creates/updates user rows (builtin=false), never
 *    overwrites factory presets, honors the cap; deleteCustomTheme removes
 *    custom rows only
 *  - shuffler: pure + seedable, distinct dark/light luminance profiles
 *  - controller: the editor's internal capture routes the IME's own keys
 *    into the name buffer (code-point safe backspace), save persists +
 *    applies, deleting the active custom theme falls back to a builtin
 *
 * Async effects run on the controller scope (Main.immediate) + Room's own
 * executor; [drain] pumps Robolectric's paused main looper so posted
 * continuations actually execute before assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxCustomThemesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val composed = mutableListOf<String>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composed += text?.toString().orEmpty()
            return super.setComposingText(text, newCursorPosition)
        }
    }

    /** Pumps the paused Robolectric main looper until [cond] or timeout. */
    private fun drain(timeoutMs: Long = 5000, cond: () -> Boolean): Boolean {
        val looper = shadowOf(Looper.getMainLooper())
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            looper.idle()
            if (cond()) return true
            Thread.sleep(10)
        }
        looper.idle()
        return cond()
    }

    /**
     * The Robolectric sandbox shares one JVM (and thus one Room DB singleton)
     * across test methods AND test classes — wipe user themes + reset the
     * active theme so runs are order-independent and never leak into
     * OnyxThemesPackTest's exact-28 factory counts.
     */
    private fun wipeCustomThemes() = runBlocking {
        val repo = AppGraph.repo
        repo.themeDao.all().filter { !it.builtin }.forEach { repo.themeDao.deleteById(it.id) }
        repo.applyTheme(1L)
    }

    @Before
    fun setUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        AppGraph.repo.ensureSeeded()
        wipeCustomThemes()
        controller = OnyxKeyboardController(app, scope, AppGraph.repo)
        ic = FakeInputConnection(ApplicationProvider.getApplicationContext())
        controller.attach(ic)
        controller.lang.value = Lang.EN
    }

    @After
    fun tearDown() = runBlocking {
        wipeCustomThemes()
        scope.cancel()
    }

    /** A plausible user-drafted look (builtin flag is sanitized on save). */
    private fun sample(name: String = "Mine") = ThemeEntity(
        name = name,
        surfaceTop = 0xFF101014, surfaceBottom = 0xFF181820,
        keyFill = 0xFF23232E, keyText = 0xFFEDEDF5,
        accent = 0xFFFF5CA8, glow = 0xFFB37BFF, particle = 0xFFFF5CA8,
    )

    // ------------------------------------------------------------ repo: save

    @Test
    fun saveCustomTheme_creates_custom_row_and_applies() = runBlocking {
        val repo = AppGraph.repo
        val id = repo.saveCustomTheme(sample())
        assertTrue("custom ids must land past the factory range", id > 28)

        val saved = repo.themeById(id)
        assertNotNull(saved)
        assertFalse(saved!!.builtin)
        assertEquals("Mine", saved.name)

        repo.applyTheme(id)
        assertEquals(id, repo.observeKeyboardTheme().first().id)
    }

    @Test
    fun saveCustomTheme_updates_existing_custom_row_in_place() = runBlocking {
        val repo = AppGraph.repo
        val id = repo.saveCustomTheme(sample("v1"))
        val edited = repo.themeById(id)!!.copy(name = "v2", accent = 0xFF00FF00)

        assertEquals(id, repo.saveCustomTheme(edited))

        val customs = repo.observeThemes().first().filter { !it.builtin }
        assertEquals(1, customs.size)
        assertEquals("v2", customs[0].name)
        assertEquals(0xFF00FF00, customs[0].accent)
    }

    @Test
    fun saveCustomTheme_refuses_to_overwrite_builtin() = runBlocking {
        val repo = AppGraph.repo
        val before = repo.themeById(1L)
        assertEquals(-1L, repo.saveCustomTheme(sample().copy(id = 1L)))
        assertEquals(before, repo.themeById(1L))
    }

    @Test
    fun custom_theme_cap_is_enforced() = runBlocking {
        val repo = AppGraph.repo
        repeat(OnyxRepository.MAX_CUSTOM_THEMES) { i ->
            assertTrue(repo.saveCustomTheme(sample("t$i")) > 0)
        }
        assertEquals(-1L, repo.saveCustomTheme(sample("over the cap")))
        assertEquals(
            OnyxRepository.MAX_CUSTOM_THEMES,
            repo.observeThemes().first().count { !it.builtin }
        )
    }

    // ------------------------------------------------------------ repo: delete

    @Test
    fun deleteCustomTheme_removes_only_custom_rows() = runBlocking {
        val repo = AppGraph.repo
        val id = repo.saveCustomTheme(sample())
        assertTrue(repo.deleteCustomTheme(id))
        assertNull(repo.themeById(id))

        // Factory presets are immutable.
        assertFalse(repo.deleteCustomTheme(1L))
        assertNotNull(repo.themeById(1L))
    }

    // ------------------------------------------------------------ shuffler

    @Test
    fun shuffledThemeColors_is_deterministic_and_seedable() {
        assertEquals(shuffledThemeColors(Random(42)), shuffledThemeColors(Random(42)))
        assertEquals(
            shuffledThemeColors(Random(9), dark = true),
            shuffledThemeColors(Random(9), dark = true)
        )
    }

    @Test
    fun shuffledThemeColors_produces_coherent_dark_and_light_variants() {
        val dark = shuffledThemeColors(Random(7), dark = true)
        val light = shuffledThemeColors(Random(7), dark = false)

        assertTrue(luminanceOf(dark.surfaceTop) < 0.5f)
        assertTrue(luminanceOf(dark.keyText) > 0.8f)
        assertTrue(dark.accent != dark.surfaceTop)
        assertTrue(dark.accent != dark.keyFill)
        assertFalse(dark.builtin)

        assertTrue(luminanceOf(light.surfaceTop) > 0.5f)
        assertTrue(luminanceOf(light.keyText) < 0.45f)
        assertFalse(light.builtin)
    }

    // ------------------------------------------------------------ editor capture

    @Test
    fun editor_capture_routes_typing_into_the_name_buffer() {
        controller.openThemeEditor(null, "قالبي 1")
        assertEquals(KeyboardPage.THEME_EDITOR, controller.page.value)
        assertNotNull(controller.editingTheme.value)
        assertEquals(0L, controller.editingTheme.value!!.id)
        assertEquals("قالبي 1", controller.internalCapture.value)

        // Keys must NEVER reach the app while the editor owns the input.
        controller.onChar("a")
        controller.onSpace()
        controller.onAltChar("ً")
        assertEquals("قالبي 1a ً", controller.internalCapture.value)
        assertTrue(ic.committed.isEmpty())
        assertTrue(ic.composed.isEmpty())

        // Backspace drops one CODE POINT — an emoji survives as a unit.
        controller.onAltChar("😀")
        assertEquals("قالبي 1a ً😀", controller.internalCapture.value)
        controller.onBackspace()
        assertEquals("قالبي 1a ً", controller.internalCapture.value)
        controller.onBackspaceWord()
        assertEquals("", controller.internalCapture.value)
    }

    @Test
    fun closing_the_editor_returns_typing_to_the_app() {
        controller.openThemeEditor(null, "X")
        controller.onChar("y") // captured
        assertTrue(ic.composed.isEmpty())

        controller.closeThemeEditor()
        assertNull(controller.internalCapture.value)
        assertNull(controller.editingTheme.value)
        assertEquals(KeyboardPage.THEMES, controller.page.value)

        controller.onChar("z")
        drain { ic.composed.isNotEmpty() }
        assertEquals("z", ic.composed.last())
    }

    // ------------------------------------------------------------ editor flow

    @Test
    fun saveEditingTheme_persists_names_and_applies() = runBlocking {
        controller.openThemeEditor(null, "Blue Dream")
        controller.updateEditingTheme { it.copy(accent = 0xFF3EE6FF) }
        controller.saveEditingTheme()

        drain { controller.page.value == KeyboardPage.THEMES }
        assertNull(controller.editingTheme.value)

        val customs = AppGraph.repo.observeThemes().first().filter { !it.builtin }
        assertEquals(1, customs.size)
        assertEquals("Blue Dream", customs[0].name)
        assertEquals(0xFF3EE6FF, customs[0].accent)
        assertEquals(customs[0].id, AppGraph.repo.currentProfile()?.selectedThemeId)
    }

    @Test
    fun deleting_the_active_custom_theme_falls_back_to_a_builtin() = runBlocking {
        val repo = AppGraph.repo
        val id = repo.saveCustomTheme(sample("victim"))
        repo.applyTheme(id)

        // Simulate the service's theme collector having caught up.
        controller.userTheme.value = repo.themeById(id)
        controller.openThemeEditor(repo.themeById(id), "")
        assertEquals(KeyboardPage.THEME_EDITOR, controller.page.value)

        controller.deleteEditingThemeNow()
        // Wait for the FULL outcome (row gone AND the profile moved back to
        // a builtin) — draining on "row gone" alone races the applyTheme(1)
        // continuation inside the delete coroutine.
        drain { runBlocking { repo.currentProfile()?.selectedThemeId == 1L } }
        assertNull(repo.themeById(id))
        assertEquals(1L, repo.currentProfile()?.selectedThemeId)
        assertEquals(KeyboardPage.THEMES, controller.page.value)
    }

    @Test
    fun fresh_field_never_leaks_editor_state() {
        controller.openThemeEditor(null, "draft")
        controller.resetForNewField(false)
        assertNull(controller.editingTheme.value)
        assertNull(controller.internalCapture.value)
        assertEquals(KeyboardPage.LETTERS, controller.page.value)
    }

    @Test
    fun builtin_cards_have_no_edit_path_but_customs_do() = runBlocking {
        val repo = AppGraph.repo
        val customId = repo.saveCustomTheme(sample())
        val themes = repo.observeThemes().first()
        val builtin = themes.first { it.id == 1L }
        val custom = themes.first { it.id == customId }

        assertTrue(builtin.builtin)
        assertFalse(custom.builtin)
        // The UI opens the editor for customs only — assert the seed payload
        // keeps the id so "edit" updates instead of cloning.
        assertNotEquals(0L, custom.id)
    }
}
