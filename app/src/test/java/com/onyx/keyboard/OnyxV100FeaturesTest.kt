package com.onyx.keyboard

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.AltChars
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.KeyboardLayouts
import com.onyx.keyboard.keyboard.OnyxKeyboardController
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the v1.0.0 feature pack:
 *  - long-press alternative characters (EN accents / AR hamza carriers)
 *  - Arabic tashkeel via space long-press
 *  - double-space -> ". "
 *  - number row layout + preference key
 *  - recent emojis (persisted, newest first, capped, deduped)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxV100FeaturesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val composing = mutableListOf<String>()
        val deletions = mutableListOf<Pair<Int, Int>>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing += text?.toString().orEmpty()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletions += beforeLength to afterLength
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

    // --------------------------------------------------- alt char catalog

    @Test
    fun latin_map_covers_the_common_accented_letters() {
        listOf("a", "e", "i", "o", "u", "s", "n", "c").forEach { letter ->
            assertNotNull("missing alternatives for '$letter'", AltChars.latin[letter])
        }
        assertTrue(AltChars.latin.getValue("e").contains("é"))
        assertTrue(AltChars.latin.getValue("a").contains("ä"))
        assertTrue(AltChars.latin.getValue("u").contains("ü"))
    }

    @Test
    fun every_latin_alternative_is_a_single_character() {
        AltChars.latin.values.flatten().forEach { alt ->
            assertTrue("alternative '$alt' must be one char", alt.length == 1)
        }
    }

    @Test
    fun arabic_map_offers_hamza_carriers_and_alef_forms() {
        assertEquals(listOf("أ", "إ", "آ", "ٱ"), AltChars.arabic.getValue("ا"))
        assertEquals(listOf("ة"), AltChars.arabic.getValue("ه"))
        assertEquals(listOf("ؤ"), AltChars.arabic.getValue("و"))
        assertTrue(AltChars.arabic.getValue("ي").contains("ئ"))
        assertTrue(AltChars.arabic.getValue("ي").contains("ى"))
    }

    @Test
    fun alternativesFor_respects_the_active_language() {
        assertTrue(AltChars.alternativesFor(Lang.EN, "a")!!.contains("á"))
        assertNull(AltChars.alternativesFor(Lang.EN, "ا"))
        assertTrue(AltChars.alternativesFor(Lang.AR, "ا")!!.contains("أ"))
        assertNull(AltChars.alternativesFor(Lang.AR, "q"))
    }

    @Test
    fun harakat_are_eight_combining_marks() {
        assertEquals(8, AltChars.harakat.size)
        AltChars.harakat.forEach { h ->
            assertTrue("'$h' is not a combining mark", h.length == 1 && h[0].code in 0x064B..0x0652)
        }
    }

    @Test
    fun stripHarakat_removes_diacritics_only() {
        assertEquals("محمد", AltChars.stripHarakat("مُحَمَّد"))
        assertEquals("hello", AltChars.stripHarakat("hello"))
        assertEquals("", AltChars.stripHarakat(""))
    }

    // --------------------------------------------------- alt popup logic

    @Test
    fun openAltPopup_fills_request_for_keys_with_alternatives() {
        controller.lang.value = Lang.EN
        controller.openAltPopup("e")
        val req = controller.altPopup.value
        assertNotNull(req)
        assertEquals("e", req!!.anchor)
        assertTrue(req.options.contains("é"))
    }

    @Test
    fun openAltPopup_ignores_keys_without_alternatives() {
        controller.openAltPopup("q")
        assertNull(controller.altPopup.value)
    }

    @Test
    fun space_popup_shows_harakat_only_in_arabic() {
        controller.lang.value = Lang.AR
        controller.openSpacePopup()
        assertEquals(AltChars.harakat, controller.altPopup.value?.options)

        controller.closeAltPopup()
        controller.lang.value = Lang.EN
        controller.openSpacePopup()
        assertNull("EN space long-press must stay a no-op", controller.altPopup.value)
    }

    @Test
    fun onAltChar_appends_without_case_transform_and_keeps_composing() {
        controller.lang.value = Lang.EN
        controller.shift.value = com.onyx.keyboard.keyboard.ShiftState.ON
        controller.onChar("c")
        controller.onAltChar("é")

        assertEquals("Cé", controller.buffer.value)
        assertEquals("Cé", ic.composing.last())
        assertNull("popup must close after picking", controller.altPopup.value)
    }

    @Test
    fun onAltChar_appends_haraka_to_the_current_word() {
        controller.lang.value = Lang.AR
        controller.onChar("م")
        controller.onAltChar("َ")

        assertEquals("مَ", controller.buffer.value)
        assertEquals("مَ", ic.composing.last())
    }

    // --------------------------------------------------- double-space -> period

    @Test
    fun double_space_replaces_space_with_period() {
        controller.lang.value = Lang.EN
        // First space with an empty buffer commits " " and arms the tracker.
        controller.onSpace()
        // Second space right after -> ". "
        controller.onSpace()

        assertEquals(listOf(1 to 0), ic.deletions)
        assertTrue(ic.committed.contains(". "))
    }

    @Test
    fun typing_between_spaces_disables_double_space_period() {
        controller.lang.value = Lang.EN
        controller.onSpace()          // " "
        controller.onChar("a")        // typing in between clears the tracker
        controller.onSpace()          // plain space after a word
        controller.onSpace()          // -> still becomes ". " (word just ended)

        // Only ONE double-space conversion must have happened.
        assertEquals(1, ic.deletions.count { it.first == 1 && it.second == 0 })
    }

    // --------------------------------------------------- number row

    @Test
    fun numberRow_is_digits_1_to_0() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), KeyboardLayouts.numberRow)
    }

    // --------------------------------------------------- recent emojis

    @Test
    fun insertEmoji_tracks_recent_emojis_newest_first_without_duplicates() {
        controller.insertEmoji("😀")
        controller.insertEmoji("😂")
        controller.insertEmoji("😀") // duplicate moves it back to the front

        assertEquals(listOf("😀", "😂"), controller.recentEmojis.value)
    }

    @Test
    fun recent_emojis_are_capped_at_24() {
        val base = 0x1F600
        repeat(30) { i ->
            val e = String(Character.toChars(base + i))
            controller.insertEmoji(e)
        }
        assertEquals(24, controller.recentEmojis.value.size)
    }

    @Test
    fun parseRecentEmojis_round_trips_and_skips_blanks() {
        val list = listOf("😀", "😂", "🚀")
        val raw = list.joinToString("\u0001")
        assertEquals(list, controller.parseRecentEmojis(raw))
        assertEquals(emptyList<String>(), controller.parseRecentEmojis(""))
        assertEquals(listOf("😀"), controller.parseRecentEmojis("\u0001😀\u0001"))
    }

    @Test
    fun parseRecentEmojis_round_trip_matches_recordRecentEmoji_output() {
        controller.insertEmoji("🎮")
        controller.insertEmoji("🎯")
        val persisted = controller.recentEmojis.value.joinToString("\u0001")
        assertEquals(controller.recentEmojis.value, controller.parseRecentEmojis(persisted))
    }
}
