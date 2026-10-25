package com.onyx.keyboard

import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.data.repository.OnyxRepository
import com.onyx.keyboard.data.repository.defaultThemes
import com.onyx.keyboard.keyboard.CursorDrag
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.isLight
import com.onyx.keyboard.keyboard.luminanceOf
import com.onyx.keyboard.keyboard.resolveTheme
import com.onyx.keyboard.network.GifHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the polish pack:
 *  - auto day/night theme (light presets + system-follow resolution)
 *  - battery-saver state plumbing
 *  - seeding upgrade (older installs gain the light presets)
 *  - space-bar cursor slide accumulator
 *  - hardened GIF download gate
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxPolishPackTest {

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

    // --------------------------------------------------- light/dark presets

    @Test
    fun factory_themes_have_six_dark_and_two_light_presets() {
        val themes = defaultThemes()
        assertEquals(8, themes.size)
        assertTrue(themes.filter { it.id in 1L..6L }.none { it.isLight() })
        assertTrue(themes.filter { it.id in 7L..8L }.all { it.isLight() })
    }

    @Test
    fun luminance_matches_obvious_bright_and_dark_colors() {
        assertTrue(luminanceOf(0xFFF4F6FB) > 0.9f)   // Frost Light surface
        assertTrue(luminanceOf(0xFF0B0B12) < 0.1f)   // Onyx Neon surface
        assertTrue(luminanceOf(0xFF808080) in 0.4f..0.6f)
    }

    // --------------------------------------------------- resolveTheme (pure)

    @Test
    fun resolveTheme_without_selection_returns_the_default_preset() {
        val resolved = resolveTheme(null, defaultThemes(), auto = true, dark = false)
        assertEquals("Onyx Neon", resolved.name)
    }

    @Test
    fun resolveTheme_keeps_selection_when_auto_is_off() {
        val selected = defaultThemes().first { it.id == 1L }
        // Even though the system is LIGHT and the preset is dark.
        assertSame(selected, resolveTheme(selected, defaultThemes(), auto = false, dark = false))
    }

    @Test
    fun resolveTheme_keeps_selection_while_system_mode_is_unknown() {
        val selected = defaultThemes().first { it.id == 1L }
        assertSame(selected, resolveTheme(selected, defaultThemes(), auto = true, dark = null))
    }

    @Test
    fun resolveTheme_swaps_to_the_first_light_preset_in_light_mode() {
        val selected = defaultThemes().first { it.id == 1L } // dark "Onyx Neon"
        val resolved = resolveTheme(selected, defaultThemes(), auto = true, dark = false)
        assertEquals(7L, resolved.id)
        assertEquals("Frost Light", resolved.name)
    }

    @Test
    fun resolveTheme_swaps_to_the_first_dark_preset_in_dark_mode() {
        val selected = defaultThemes().first { it.id == 8L } // light "Dawn Light"
        val resolved = resolveTheme(selected, defaultThemes(), auto = true, dark = true)
        assertEquals(1L, resolved.id)
        assertEquals("Onyx Neon", resolved.name)
    }

    @Test
    fun resolveTheme_keeps_a_selection_that_already_matches_the_mode() {
        val light = defaultThemes().first { it.id == 7L }
        assertSame(light, resolveTheme(light, defaultThemes(), auto = true, dark = false))
        val dark = defaultThemes().first { it.id == 6L }
        assertSame(dark, resolveTheme(dark, defaultThemes(), auto = true, dark = true))
    }

    @Test
    fun resolveTheme_falls_back_to_selection_when_pool_has_no_match() {
        val darkOnly = defaultThemes().filter { it.id in 1L..6L }
        val selected = darkOnly.first()
        // System light but no light preset available → keep the user's choice.
        assertSame(selected, resolveTheme(selected, darkOnly, auto = true, dark = false))
    }

    // --------------------------------------------------- controller plumbing

    @Test
    fun controller_refreshTheme_applies_light_preset_when_system_is_light() {
        val onyx = defaultThemes().first { it.id == 1L }
        controller.userTheme.value = onyx
        controller.themes.value = defaultThemes()
        controller.autoTheme.value = true
        controller.systemDark.value = false
        controller.refreshTheme()

        assertEquals("Frost Light", controller.theme.value.name)
        assertTrue(controller.theme.value.surfaceTop == androidx.compose.ui.graphics.Color(0xFFF4F6FB))
    }

    @Test
    fun controller_refreshTheme_restores_user_theme_when_auto_turned_off() {
        val onyx = defaultThemes().first { it.id == 1L }
        controller.userTheme.value = onyx
        controller.themes.value = defaultThemes()
        controller.autoTheme.value = true
        controller.systemDark.value = false
        controller.refreshTheme()
        assertEquals("Frost Light", controller.theme.value.name)

        controller.autoTheme.value = false
        controller.refreshTheme()
        assertEquals("Onyx Neon", controller.theme.value.name)
    }

    @Test
    fun controller_refreshTheme_with_unknown_mode_keeps_user_theme() {
        val onyx = defaultThemes().first { it.id == 1L }
        controller.userTheme.value = onyx
        controller.themes.value = defaultThemes()
        controller.autoTheme.value = true
        controller.systemDark.value = null
        controller.refreshTheme()
        assertEquals("Onyx Neon", controller.theme.value.name)
    }

    @Test
    fun controller_powerSave_defaults_to_off_and_is_settable() {
        assertFalse(controller.powerSave.value)
        controller.powerSave.value = true
        assertTrue(controller.powerSave.value)
    }

    // --------------------------------------------------- seeding upgrade

    @Test
    fun ensureSeeded_restores_missing_factory_themes_on_older_installs() = runBlocking {
        val repo = AppGraph.repo
        repo.ensureSeeded() // fresh → all 8 present
        assertEquals(8, repo.themeDao.all().count { it.builtin })

        // Simulate an install from before the light presets existed.
        repo.themeDao.deleteById(7L)
        repo.themeDao.deleteById(8L)
        assertNull(repo.themeDao.byId(7L))

        repo.ensureSeeded() // upgrade path must re-insert only the missing ones

        assertNotNull(repo.themeDao.byId(7L))
        assertNotNull(repo.themeDao.byId(8L))
        val names = repo.themeDao.all().map { it.name }
        assertTrue(names.contains("Frost Light"))
        assertTrue(names.contains("Dawn Light"))
    }

    // --------------------------------------------------- cursor slide

    @Test
    fun cursorDrag_accumulates_pixels_into_discrete_steps() {
        val drag = CursorDrag(stepPx = 10f)
        assertEquals(0, drag.onDelta(9f))    // below threshold → no move yet
        assertEquals(1, drag.onDelta(1f))    // 9+1 = one full step
        assertEquals(0, drag.onDelta(5f))    // carry = 5, not enough
        assertEquals(3, drag.onDelta(25f))   // carry 30 → three steps
    }

    @Test
    fun cursorDrag_reports_negative_steps_for_leftward_drags() {
        val drag = CursorDrag(stepPx = 10f)
        assertEquals(-1, drag.onDelta(-10f))
        assertEquals(-2, drag.onDelta(-25f)) // carry -25 → -2 steps, carry -5
    }

    @Test
    fun cursorDrag_reset_drops_leftover_carry() {
        val drag = CursorDrag(stepPx = 10f)
        drag.onDelta(9f)
        drag.reset()
        assertEquals(0, drag.onDelta(1f)) // would have fired before the reset
    }

    @Test
    fun cursorDrag_with_zero_step_never_moves() {
        val drag = CursorDrag(stepPx = 0f)
        assertEquals(0, drag.onDelta(1000f))
        assertEquals(0, drag.onDelta(-1000f))
    }

    // --------------------------------------------------- GIF download gate

    @Test
    fun gifHttp_accepts_real_gif_payloads_and_unknown_types() {
        val bytes = ByteArray(128) { it.toByte() }
        assertTrue(GifHttp.isAcceptable("image/gif", bytes))
        assertTrue(GifHttp.isAcceptable("image/gif; charset=binary", bytes))
        assertTrue(GifHttp.isAcceptable("application/octet-stream", bytes))
        assertTrue("missing header → CDN quirk, still allowed", GifHttp.isAcceptable(null, bytes))
    }

    @Test
    fun gifHttp_rejects_html_error_pages_and_json() {
        val bytes = ByteArray(128)
        assertFalse(GifHttp.isAcceptable("text/html", bytes))
        assertFalse(GifHttp.isAcceptable("text/plain", bytes))
        assertFalse(GifHttp.isAcceptable("application/json", bytes))
    }

    @Test
    fun gifHttp_rejects_empty_and_oversize_payloads() {
        val bytes = ByteArray(128)
        assertFalse(GifHttp.isAcceptable("image/gif", ByteArray(0)))
        assertFalse(GifHttp.isAcceptable("image/gif", null))
        assertFalse(GifHttp.isAcceptable("image/gif", bytes, maxBytes = 10))
        assertTrue(GifHttp.isAcceptable("image/gif", bytes, maxBytes = 129))
    }

    @Test
    fun gifHttp_size_cap_is_sane() {
        assertEquals(15L * 1024 * 1024, GifHttp.MAX_BYTES)
        assertEquals(10_000, GifHttp.CONNECT_TIMEOUT_MS)
        assertEquals(20_000, GifHttp.READ_TIMEOUT_MS)
    }

    // --------------------------------------------------- settings key

    @Test
    fun auto_theme_setting_key_is_stable() {
        assertEquals("auto_theme", OnyxRepository.KEY_AUTO_THEME)
    }
}
