package com.onyx.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.data.crypto.OnyxCrypto
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for the IMPROVED CLIPBOARD pack:
 *  - repo: re-copy moves an old clip to the top (whole-history dedupe,
 *    pin preserved), cap trims unpinned only, clear wipes everything
 *  - watcher: capture-off records nothing, capture-on saves + surfaces the
 *    quick chip, pre-existing clipboard is adopted on wake-up
 *  - controller: pasteText commits + consumes the chip, copyClip restores
 *    the system clipboard
 *
 * Async effects run on the controller scope (Main.immediate) + Room's own
 * executor; [drain] pumps Robolectric's paused main looper so posted
 * continuations actually execute before assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxClipboardImprovementsTest {

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

    private suspend fun clips(): List<String> =
        AppGraph.repo.observeClips().first().map { AppGraph.repo.decryptClip(it) }

    /** Distinct createdAt per row — observeAll orders by createdAt DESC. */
    private fun pause() = Thread.sleep(6)

    @Before
    fun setUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        AppGraph.repo.ensureSeeded()
        AppGraph.repo.clearClips()
        controller = OnyxKeyboardController(app, scope, AppGraph.repo)
        ic = FakeInputConnection(ApplicationProvider.getApplicationContext())
        controller.attach(ic)
        controller.lang.value = Lang.EN
        systemClipboard =
            app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @After
    fun tearDown() = runBlocking {
        scope.cancel()
        AppGraph.repo.clearClips()
    }

    // ------------------------------------------------------------ repo: dedupe

    @Test
    fun addClip_moves_recopied_clip_to_top_keeping_pin() = runBlocking {
        AppGraph.repo.addClip("alpha"); pause()
        AppGraph.repo.addClip("beta"); pause()
        val alpha = AppGraph.repo.observeClips().first().first { AppGraph.repo.decryptClip(it) == "alpha" }
        AppGraph.repo.setClipPinned(alpha.id, true)
        pause()
        AppGraph.repo.addClip("alpha")

        val list = AppGraph.repo.observeClips().first()
        assertEquals("no duplicate row should be created", 2, list.size)
        assertEquals("re-copied clip floats to the top", "alpha", AppGraph.repo.decryptClip(list.first()))
        assertTrue("pin survives the move-to-top", list.first().pinned)
    }

    @Test
    fun addClip_dedupes_across_whole_history_not_just_recent_five() = runBlocking {
        repeat(7) { n ->
            AppGraph.repo.addClip("clip-$n"); pause()
        }
        // clip-0 is 7 entries back — the old recent(5) window missed it.
        AppGraph.repo.addClip("clip-0")
        assertEquals(7, AppGraph.repo.observeClips().first().size)
        assertEquals("clip-0", clips().first())
    }

    // ------------------------------------------------------------ repo: cap + clear

    @Test
    fun addClip_trims_unpinned_beyond_cap_but_keeps_pinned() = runBlocking {
        AppGraph.repo.addClip("keepme")
        val keep = AppGraph.repo.observeClips().first().first()
        AppGraph.repo.setClipPinned(keep.id, true)
        repeat(31) { n ->
            AppGraph.repo.addClip("filler-$n"); pause()
        }

        val list = AppGraph.repo.observeClips().first()
        // trim caps UNPINNED rows at MAX_CLIPS; the pinned row sits on top of them.
        assertEquals(OnyxRepository.MAX_CLIPS + 1, list.size)
        assertEquals("pinned clip floats to the top", "keepme", clips().first())
        assertTrue("newest unpinned clip kept", clips().contains("filler-30"))
    }

    @Test
    fun clear_clips_wipes_history() = runBlocking {
        AppGraph.repo.addClip("one"); pause()
        AppGraph.repo.addClip("two"); pause()
        AppGraph.repo.addClip("three")
        assertTrue(clips().isNotEmpty())

        controller.clearClips()
        assertTrue(drain { runBlocking { AppGraph.repo.observeClips().first().isEmpty() } })
    }

    // ------------------------------------------------------------ watcher capture

    @Test
    fun capture_disabled_watcher_records_nothing() = runBlocking {
        controller.clipsEnabled.value = false
        controller.startClipboardWatching()

        systemClipboard.setPrimaryClip(ClipData.newPlainText("t", "secret"))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(drain { true }) // let any (wrong) IO work surface
        assertTrue("history must stay empty when capture is off", clips().isEmpty())
        assertNull("quick chip must not surface content when capture is off", controller.quickClip.value)
    }

    @Test
    fun capture_enabled_watcher_adopts_preexisting_clip_and_captures_new_ones() = runBlocking {
        // Pre-existing clipboard before the keyboard wakes up.
        systemClipboard.setPrimaryClip(ClipData.newPlainText("t", "copied earlier"))
        controller.startClipboardWatching()

        assertTrue(drain { runBlocking { clips() == listOf("copied earlier") } })
        assertEquals("copied earlier", controller.quickClip.value)

        // A copy made while the keyboard is open.
        systemClipboard.setPrimaryClip(ClipData.newPlainText("t", "fresh copy"))
        assertTrue(drain { runBlocking { clips().first() == "fresh copy" } })
        assertEquals("fresh copy", controller.quickClip.value)
        assertEquals(2, AppGraph.repo.observeClips().first().size)
    }

    // ------------------------------------------------------------ crypto fallback

    @Test
    fun crypto_round_trips_even_when_keystore_unavailable() {
        val stored = OnyxCrypto.encrypt("secret value")
        assertEquals("secret value", OnyxCrypto.decrypt(stored))
        assertEquals("", OnyxCrypto.decrypt(""))
    }

    // ------------------------------------------------------------ controller actions

    @Test
    fun paste_text_commits_and_consumes_quick_chip() = runBlocking {
        controller.quickClip.value = "paste me"
        controller.pasteText("paste me")

        assertEquals("paste me", ic.committed.last())
        assertNull("chip consumed so the same content is not pasted twice", controller.quickClip.value)
    }

    @Test
    fun paste_text_ignores_empty_input() = runBlocking {
        controller.quickClip.value = "keep"
        controller.pasteText("")
        assertTrue(ic.committed.isEmpty())
        assertEquals("keep", controller.quickClip.value)
    }

    @Test
    fun copy_clip_restores_system_clipboard() = runBlocking {
        AppGraph.repo.addClip("old text")
        val entity = AppGraph.repo.observeClips().first().first()

        controller.copyClip(entity)
        assertTrue(
            drain {
                val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
                text == "old text"
            }
        )
    }
}
