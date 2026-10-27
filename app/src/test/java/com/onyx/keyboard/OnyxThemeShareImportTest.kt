package com.onyx.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.KeyboardPage
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxThemeCodec
import com.onyx.keyboard.data.db.ThemeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for the SHARE + BACKGROUND-IMAGE pack:
 *  - codec: ONYX1. codes round-trip the palette, reject garbage / foreign
 *    versions, sanitize names and survive chat-app wrappers
 *  - import: one tap turns a clipboard code into a saved + applied theme;
 *    junk clips write nothing
 *  - export: the draft lands on the clipboard AND as a .onyx-theme file
 *  - background: picker hand-off updates the live draft (and only the
 *    draft), deleting a theme reclaims its photo file
 *
 * The Robolectric sandbox shares one JVM + Room DB singleton across tests —
 * custom rows are wiped in setUp/tearDown so runs are order-independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxThemeShareImportTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection
    private lateinit var systemClipboard: ClipboardManager

    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
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
        systemClipboard =
            app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @After
    fun tearDown() = runBlocking {
        wipeCustomThemes()
        scope.cancel()
    }

    private fun sample(name: String = "Desert Night") = ThemeEntity(
        name = name,
        surfaceTop = 0xFF14100A, surfaceBottom = 0xFF201810,
        keyFill = 0xFF2E2418, keyText = 0xFFFFF3E0,
        accent = 0xFFFFB300, glow = 0xFFFF6B4A, particle = 0xFFFFB300,
    )

    private fun clipText(): String =
        systemClipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()

    private fun setClip(text: String) {
        systemClipboard.setPrimaryClip(ClipData.newPlainText("test", text))
    }

    // ------------------------------------------------------------ codec

    @Test
    fun codec_round_trip_preserves_the_palette() {
        val code = OnyxThemeCodec.encode(sample())
        assertTrue(code.startsWith("ONYX1."))
        assertTrue("one line, no whitespace", !code.contains(' '))

        val back = OnyxThemeCodec.decode(code)
        assertNotNull(back)
        val t = sample()
        assertEquals(t.name, back!!.name)
        assertEquals(t.surfaceTop, back.surfaceTop)
        assertEquals(t.surfaceBottom, back.surfaceBottom)
        assertEquals(t.keyFill, back.keyFill)
        assertEquals(t.keyText, back.keyText)
        assertEquals(t.accent, back.accent)
        assertEquals(t.glow, back.glow)
        assertEquals(t.particle, back.particle)
        // Identity and device-private bits never travel.
        assertEquals(0L, back.id)
        assertFalse(back.builtin)
        assertEquals("", back.bgUri)
    }

    @Test
    fun codec_rejects_garbage_and_foreign_versions() {
        assertNull(OnyxThemeCodec.decode(""))
        assertNull(OnyxThemeCodec.decode("hello, check this out"))
        assertNull(OnyxThemeCodec.decode("ONYX1.!!!not-base64!!!"))
        assertNull(OnyxThemeCodec.decode("ONYX1."))

        val noVersion = android.util.Base64.encodeToString(
            """{"name":"x","surfaceTop":"FF101014"}""".toByteArray(),
            android.util.Base64.URL_SAFE
        )
        assertNull(OnyxThemeCodec.decode("ONYX1.$noVersion"))
    }

    @Test
    fun codec_survives_chat_wrappers_and_sanitizes_names() {
        val longName = sample("x".repeat(60))
        val code = OnyxThemeCodec.encode(longName)
        val wrapped = "هاي شوف هذا الثيم الجديد 🔥\n$code\nجرّبه وقلّي رأيك"

        val back = OnyxThemeCodec.decode(wrapped)
        assertNotNull("prefix found inside forwarded text", back)
        assertEquals(24, back!!.name.length)
        assertEquals(0xFF14100A, back.surfaceTop)
    }

    @Test
    fun codec_is_canonical() {
        val once = OnyxThemeCodec.encode(sample())
        val twice = OnyxThemeCodec.encode(OnyxThemeCodec.decode(once)!!)
        assertEquals(once, twice)
    }

    // ------------------------------------------------------------ import

    @Test
    fun import_from_clipboard_creates_and_applies() {
        setClip(OnyxThemeCodec.encode(sample()))
        assertTrue(controller.importFromClipboard())

        // Wait for the FULL outcome — the profile must have moved OFF the
        // builtin (draining on "row exists" alone races applyTheme).
        drain {
            runBlocking {
                AppGraph.repo.currentProfile()?.selectedThemeId != 1L
            }
        }
        runBlocking {
            val custom = AppGraph.repo.observeThemes().first().first { !it.builtin }
            assertEquals("Desert Night", custom.name)
            assertFalse(custom.builtin)
            assertEquals(custom.id, AppGraph.repo.currentProfile()?.selectedThemeId)
        }
    }

    @Test
    fun import_rejects_junk_and_writes_nothing() {
        setClip("totally not a theme code")
        assertFalse(controller.importFromClipboard())

        runBlocking {
            assertEquals(0, AppGraph.repo.observeThemes().first().count { !it.builtin })
            assertEquals(1L, AppGraph.repo.currentProfile()?.selectedThemeId)
        }
        // Editor state untouched by failed imports.
        assertNull(controller.editingTheme.value)
    }

    // ------------------------------------------------------------ export

    @Test
    fun export_copies_code_and_writes_the_share_file() = runBlocking {
        controller.openThemeEditor(null, "My Export")
        controller.exportEditingTheme()

        val code = clipText()
        assertTrue("code on the system clipboard", code.startsWith("ONYX1."))
        val files = File(ApplicationProvider.getApplicationContext<OnyxApp>().cacheDir, "shared")
            .listFiles { f -> f.name.endsWith(".onyx-theme") }
            .orEmpty()
        assertTrue("share file written", files.isNotEmpty())
        assertEquals(code, files.first().readText())
    }

    // ------------------------------------------------------------ background photo

    @Test
    fun picker_handoff_updates_the_live_draft() {
        controller.openThemeEditor(null, "Photo Theme")
        val before = controller.editingTheme.value

        controller.onBackgroundPicked("file:/data/user/0/com.onyx.keyboard/files/theme_bg/bg_1.img")

        assertEquals(
            "/data/user/0/com.onyx.keyboard/files/theme_bg/bg_1.img",
            controller.editingTheme.value?.bgUri
        )
        // Palette untouched, editor stays open.
        assertEquals(before?.accent, controller.editingTheme.value?.accent)
        assertEquals(KeyboardPage.THEME_EDITOR, controller.page.value)
    }

    @Test
    fun picker_handoff_outside_the_editor_is_a_noop() {
        controller.onBackgroundPicked("file:/somewhere/bg.img")
        assertNull(controller.editingTheme.value)
        runBlocking {
            assertEquals(0, AppGraph.repo.observeThemes().first().count { !it.builtin })
        }
    }

    @Test
    fun removing_the_photo_of_a_new_draft_reclaims_the_file() {
        val f = File(
            ApplicationProvider.getApplicationContext<OnyxApp>().cacheDir,
            "bg_orphan_probe.img"
        ).apply { writeText("img") }
        assertTrue(f.exists())

        controller.openThemeEditor(null, "Draft")
        controller.onBackgroundPicked("file:${f.absolutePath}")
        controller.removeBackgroundImage()

        assertEquals("", controller.editingTheme.value?.bgUri)
        assertFalse("unreferenced pick is reclaimed", f.exists())
    }

    @Test
    fun deleting_a_theme_reclaims_its_photo_file() = runBlocking {
        val f = File(
            ApplicationProvider.getApplicationContext<OnyxApp>().cacheDir,
            "bg_delete_probe.img"
        ).apply { writeText("img") }
        assertTrue(f.exists())

        val id = AppGraph.repo.saveCustomTheme(sample("victim").copy(bgUri = f.absolutePath))
        AppGraph.repo.applyTheme(id)
        controller.userTheme.value = AppGraph.repo.themeById(id)
        controller.openThemeEditor(AppGraph.repo.themeById(id), "")

        controller.deleteEditingThemeNow()
        drain { runBlocking { AppGraph.repo.currentProfile()?.selectedThemeId == 1L } }

        assertNull(AppGraph.repo.themeById(id))
        assertFalse("photo reclaimed with its theme", f.exists())
        assertEquals(KeyboardPage.THEMES, controller.page.value)
    }
}
