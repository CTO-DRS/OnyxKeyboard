package com.onyx.keyboard

import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * F10 (PRD) — الخطوط المستوردة: مستودع الخطوط كاملًا على قاعدة حقيقية —
 * الإضافة مع سقف MAX_FONTS، الترتيب الأحدث أولًا، قصّ الاسم إلى 40 محرفًا،
 * وحذف الخط المحدّد يُلغي تحديده أولًا ثم يزيل الصف والملف من التخزين الخاص.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxFontsTest {

    private lateinit var filesDir: File

    @Before
    fun setUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OnyxApp>()
        AppGraph.init(app)
        val repo = AppGraph.repo
        // Zero the table + the selection pointer so every test starts clean.
        repo.observeFonts().first().forEach { repo.deleteFont(it) }
        repo.putSetting(OnyxRepository.KEY_SELECTED_FONT, "0")
        filesDir = app.filesDir
    }

    private fun fakeFont(i: Int): String =
        File(filesDir, "onyx_test_font_$i.ttf").apply { writeBytes(ByteArray(64)) }.absolutePath

    @Test
    fun `addFont persists, trims long names, orders newest-first and caps`() = runBlocking {
        val repo = AppGraph.repo
        // A very long display name is clipped to 40 characters (keycap safety).
        val longName = "خط عربي تجريبي بطول كبير جدًا 1234567890 ABCDEFGHIJ"
        assertTrue(longName.length > 40)
        val firstId = repo.addFont(longName, fakeFont(0))
        assertTrue(firstId > 0L)
        assertEquals(40, repo.fontById(firstId)!!.name.length)

        // Fill the shelf with DISTINCT timestamps so the DESC order is exact.
        val ids = mutableListOf(firstId)
        for (i in 1 until OnyxRepository.MAX_FONTS) {
            Thread.sleep(5) // guarantee a strictly larger importedAt
            ids += repo.addFont("خط $i", fakeFont(i))
        }
        assertTrue(ids.all { it > 0L })
        assertEquals(OnyxRepository.MAX_FONTS, repo.observeFonts().first().size)

        // Newest first — the last inserted row sits on top.
        assertEquals(ids.last(), repo.observeFonts().first().first().id)

        // The cap is hard: the (MAX_FONTS+1)-th import is refused.
        assertEquals(-1L, repo.addFont("فائض", fakeFont(99)))
        assertEquals(OnyxRepository.MAX_FONTS, repo.observeFonts().first().size)
    }

    @Test
    fun `deleting the selected font deselects it and removes its file`() = runBlocking {
        val repo = AppGraph.repo
        val pathA = fakeFont(1)
        val pathB = fakeFont(2)
        val idA = repo.addFont("الخط المحذوف", pathA)
        val idB = repo.addFont("الخط الباقي", pathB)
        assertNotNull(repo.fontById(idA))

        // Select A, then delete the NON-selected font: the pointer is untouched.
        repo.selectFont(idA)
        assertEquals(idA.toString(), repo.setting(OnyxRepository.KEY_SELECTED_FONT, "0"))
        repo.deleteFont(repo.fontById(idB)!!)
        assertEquals(idA.toString(), repo.setting(OnyxRepository.KEY_SELECTED_FONT, "0"))
        assertNull(repo.fontById(idB))
        assertFalse(File(pathB).exists())

        // Deleting the SELECTED font resets the pointer FIRST, then row + file go.
        repo.deleteFont(repo.fontById(idA)!!)
        assertEquals("0", repo.setting(OnyxRepository.KEY_SELECTED_FONT, "0"))
        assertNull(repo.fontById(idA))
        assertFalse("the imported font file must be reclaimed", File(pathA).exists())
        assertTrue(repo.observeFonts().first().isEmpty())
    }
}
