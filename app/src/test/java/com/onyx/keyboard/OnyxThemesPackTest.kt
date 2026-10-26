package com.onyx.keyboard

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.data.repository.OnyxRepository
import com.onyx.keyboard.data.repository.defaultThemes
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.isLight
import com.onyx.keyboard.keyboard.luminanceOf
import com.onyx.keyboard.keyboard.resolveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for the THEMES PACK: 20 new factory presets (ids 9-28, 13 dark +
 * 7 light), the idempotent seeding upgrade path, auto day/night resolution
 * over the enlarged pool, palette readability sanity, and theme switching
 * persistence. Reuses the shared AppGraph harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxThemesPackTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private var pid: Long = 0

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

    @Before
    fun setUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        AppGraph.repo.ensureSeeded()
        pid = AppGraph.repo.addProfile("themes-test", "T")
        AppGraph.repo.setCurrentProfile(pid)
        controller = OnyxKeyboardController(app, scope, AppGraph.repo)
    }

    @After
    fun tearDown() = runBlocking {
        scope.cancel()
        AppGraph.repo.deleteProfile(pid)
    }

    // ------------------------------------------------------------ preset catalog

    @Test
    fun default_themes_has_28_presets_unique_names_contiguous_ids() {
        val themes = defaultThemes()
        assertEquals(28, themes.size)
        assertEquals((1L..28L).toSet(), themes.map { it.id }.toSet())
        assertEquals("preset names must be unique", themes.size, themes.map { it.name }.toSet().size)
        assertTrue(themes.all { it.builtin })
    }

    @Test
    fun themes_pack_splits_19_dark_and_9_light_presets() {
        val themes = defaultThemes()
        val light = themes.filter { it.isLight() }
        assertEquals(9, light.size)
        assertEquals(19, themes.size - light.size)
        // The auto day/night pair from the polish pack is untouched.
        assertEquals("Frost Light", themes.first { it.id == 7L }.name)
        assertEquals("Dawn Light", themes.first { it.id == 8L }.name)
    }

    @Test
    fun every_preset_has_readable_key_contrast_and_visible_accent() {
        defaultThemes().forEach { t ->
            val keyContrast = kotlin.math.abs(luminanceOf(t.keyText) - luminanceOf(t.keyFill))
            assertTrue(
                "${t.name}: key text/key fill contrast too low ($keyContrast)",
                keyContrast > 0.35f
            )
            val accentPop = kotlin.math.abs(luminanceOf(t.accent) - luminanceOf(t.surfaceTop))
            assertTrue(
                "${t.name}: accent invisible on surface ($accentPop)",
                accentPop > 0.15f
            )
        }
    }

    // ------------------------------------------------------------ seeding upgrade

    @Test
    fun ensure_seeded_backfills_missing_factory_themes_idempotently() = runBlocking {
        val repo = AppGraph.repo
        assertEquals(28, repo.themeDao.count())
        // Idempotent second run.
        repo.ensureSeeded()
        assertEquals(28, repo.themeDao.count())
        // Simulated upgrade: an older install is missing the pack rows.
        repo.themeDao.deleteById(9L)
        repo.themeDao.deleteById(22L)
        assertEquals(26, repo.themeDao.count())
        repo.ensureSeeded()
        assertEquals(28, repo.themeDao.count())
        assertEquals("Aurora Borealis", repo.themeDao.byId(9L)?.name)
        assertEquals("Mint Paper", repo.themeDao.byId(22L)?.name)
    }

    // ------------------------------------------------------------ auto day/night

    @Test
    fun resolve_theme_auto_falls_back_to_first_matching_lightness() {
        val pool = defaultThemes()
        // Dark selected + day mode → first light preset.
        val day = resolveTheme(
            selected = pool.first { it.id == 1L }, pool = pool, auto = true, dark = false
        )
        assertTrue(day.isLight())
        // Light selected + night mode → first dark preset.
        val night = resolveTheme(
            selected = pool.first { it.id == 7L }, pool = pool, auto = true, dark = true
        )
        assertTrue(!night.isLight())
        // Auto off → keep the user's choice as-is.
        val manual = resolveTheme(
            selected = pool.first { it.id == 7L }, pool = pool, auto = false, dark = true
        )
        assertTrue(manual.isLight())
    }

    // ------------------------------------------------------------ switching

    @Test
    fun switch_theme_persists_selection_to_profile() = runBlocking {
        controller.switchTheme(11L) // Blood Moon
        assertTrue(
            drain { runBlocking { AppGraph.repo.currentProfile()?.selectedThemeId == 11L } }
        )
    }
}
