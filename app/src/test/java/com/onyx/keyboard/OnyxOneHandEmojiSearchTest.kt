package com.onyx.keyboard

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.keyboard.ArabicSearch
import com.onyx.keyboard.keyboard.EmojiCatalog
import com.onyx.keyboard.keyboard.OneHand
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.KeyboardPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ONE-HANDED MODE + ARABIC EMOJI SEARCH (versionCode 12):
 *  - OneHand enum parsing + flip + controller persistence (settings table).
 *  - ArabicSearch normalization (hamza carriers, taa marbuta, maqsura,
 *    diacritics, tatweel, latin case, whitespace) + AND token matching.
 *  - EmojiCatalog: keyword integrity (every emoji searchable, no dupes),
 *    Arabic queries finding the right shelves, caps and stable order.
 *  - Emoji search capture flow: query keystrokes land in the internal
 *    capture (never the host field), Enter/paste are fenced, closing the
 *    page releases the capture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxOneHandEmojiSearchTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

    class FakeInputConnection(context: Context) : BaseInputConnection(View(context), false) {
        val committed = mutableListOf<String>()
        val composing = mutableListOf<String>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed += text?.toString().orEmpty()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing += text?.toString().orEmpty()
            return true
        }
    }

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

    // ------------------------------------------------------- OneHand enum

    @Test
    fun oneHand_parses_valid_ids_and_falls_back_to_off() {
        assertEquals(OneHand.OFF, OneHand.from("off"))
        assertEquals(OneHand.LEFT, OneHand.from("left"))
        assertEquals(OneHand.RIGHT, OneHand.from("right"))
        assertEquals(OneHand.LEFT, OneHand.from(" LEFT "))
        assertEquals(OneHand.RIGHT, OneHand.from("Right"))
        assertEquals(OneHand.OFF, OneHand.from(null))
        assertEquals(OneHand.OFF, OneHand.from(""))
        assertEquals(OneHand.OFF, OneHand.from("middle"))
        assertEquals(OneHand.OFF, OneHand.from("center"))
    }

    @Test
    fun oneHand_flip_swaps_sides_and_keeps_off() {
        assertEquals(OneHand.RIGHT, OneHand.LEFT.flipped)
        assertEquals(OneHand.LEFT, OneHand.RIGHT.flipped)
        assertEquals(OneHand.OFF, OneHand.OFF.flipped)
    }

    @Test
    fun setOneHand_updates_flow_and_persists_the_setting() {
        controller.setOneHand(OneHand.RIGHT)
        assertEquals(OneHand.RIGHT, controller.oneHand.value)
        assertTrue(
            drain {
                runBlocking {
                    AppGraph.repo.setting("one_hand", "off") == "right"
                }
            }
        )
        controller.setOneHand(OneHand.LEFT)
        assertEquals(OneHand.LEFT, controller.oneHand.value)
        assertTrue(
            drain {
                runBlocking {
                    AppGraph.repo.setting("one_hand", "off") == "left"
                }
            }
        )
    }

    // -------------------------------------------------- ArabicSearch core

    @Test
    fun normalize_unifies_hamza_carriers() {
        assertEquals(ArabicSearch.normalize("احب"), ArabicSearch.normalize("أحب"))
        assertEquals(ArabicSearch.normalize("احب"), ArabicSearch.normalize("إحبّ"))
        assertEquals(ArabicSearch.normalize("احب"), ArabicSearch.normalize("آحب"))
        assertEquals("احب", ArabicSearch.normalize("أَحْـبُ"))
    }

    @Test
    fun normalize_folds_taa_marbuta_and_maqsura() {
        assertEquals("شجره", ArabicSearch.normalize("شجرة"))
        assertEquals("علي", ArabicSearch.normalize("على"))
        assertEquals("علي", ArabicSearch.normalize("على  ")) // + trailing spaces
    }

    @Test
    fun normalize_strips_diacritics_and_tatweel() {
        assertEquals("مرحبا", ArabicSearch.normalize("مَرْحَبًا"))
        // Tatweel (decorative stretching) between the alefs must vanish —
        // letter COUNT is preserved, only the stretching marks go.
        assertEquals("عااالي", ArabicSearch.normalize("عاـاالي"))
        assertEquals("عاالي", ArabicSearch.normalize("عا\u0640الي"))
        assertEquals("عالي", ArabicSearch.normalize("ع\u0640الي"))
    }

    @Test
    fun normalize_lowercases_latin_and_collapses_spaces() {
        assertEquals("heart cat", ArabicSearch.normalize("  HEART   Cat "))
        assertEquals("a b", ArabicSearch.normalize("a\u00A0\u00A0b"))
    }

    @Test
    fun matches_is_and_semantics_across_tokens() {
        // "قلب احمر" must hit the red heart keywords, not the blue one.
        val red = listOf("قلب", "حب", "أحمر")
        val blue = listOf("قلب", "أزرق")
        val tokens = ArabicSearch.tokens("قلب احمر")
        assertTrue(ArabicSearch.matches(red, tokens))
        assertFalse(ArabicSearch.matches(blue, tokens))
    }

    @Test
    fun single_char_keywords_match_only_exactly() {
        val kws = listOf("ا", "نار")
        assertTrue(ArabicSearch.matches(kws, listOf("ا")))
        assertFalse(ArabicSearch.matches(kws, listOf("اب")))
        // multi-char keywords still do containment both ways
        assertTrue(ArabicSearch.matches(kws, listOf("نار")))
        assertTrue(ArabicSearch.matches(kws, listOf("ناري")))
    }

    @Test
    fun matches_rejects_empty_queries_and_empty_keywords() {
        assertFalse(ArabicSearch.matches(listOf("قلب"), emptyList()))
        assertFalse(ArabicSearch.matches(emptyList(), listOf("قلب")))
    }

    // -------------------------------------------------- EmojiCatalog search

    @Test
    fun search_finds_smiles_by_arabic_prefix() {
        val hits = EmojiCatalog.search("ابتسام")
        assertTrue(hits.isNotEmpty())
        assertTrue("😀 must be found by «ابتسام»", "😀" in hits)
        assertTrue("😊 must be found by «ابتسام»", "😊" in hits)
    }

    @Test
    fun search_survives_hamza_and_diacritic_variants() {
        val plain = EmojiCatalog.search("حب")
        val fancy = EmojiCatalog.search("حُـبّ")
        assertNotEquals(emptyList<String>(), plain)
        assertEquals(plain, fancy)
    }

    @Test
    fun search_multi_token_is_and_scoped() {
        val both = EmojiCatalog.search("قلب احمر")
        assertTrue("❤️" in both)
        assertFalse("💙" in both)
        val onlyBlue = EmojiCatalog.search("قلب ازرق")
        assertTrue("💙" in onlyBlue)
        assertFalse("❤️" in onlyBlue)
    }

    @Test
    fun search_hits_every_major_shelf() {
        assertTrue("🐱" in EmojiCatalog.search("قط"))
        assertTrue("😂" in EmojiCatalog.search("ضحك"))
        assertTrue("👍" in EmojiCatalog.search("اعجاب"))
        assertTrue("🍕" in EmojiCatalog.search("بيتزا"))
        assertTrue("⚽" in EmojiCatalog.search("كرة"))
        assertTrue("✈️" in EmojiCatalog.search("طائرة"))
        assertTrue("📱" in EmojiCatalog.search("هاتف"))
        assertTrue("✅" in EmojiCatalog.search("صح"))
    }

    @Test
    fun search_finds_people_and_flags_shelves() {
        assertTrue("🧕" in EmojiCatalog.search("حجاب"))
        assertTrue("👶" in EmojiCatalog.search("طفل"))
        assertTrue("🇸🇦" in EmojiCatalog.search("علم السعودية"))
        assertTrue("🇪🇬" in EmojiCatalog.search("مصر"))
        // the shelf label itself surfaces the whole pack
        assertTrue(EmojiCatalog.search("اعلام").size >= 20)
    }

    @Test
    fun search_caps_results_and_stays_stable() {
        val capped = EmojiCatalog.search("ا") // nearly every keyword contains alef
        assertEquals(80, capped.size)
        assertEquals(capped, EmojiCatalog.search("ا"))
        assertTrue(EmojiCatalog.search("", 80).isEmpty())
        assertTrue(EmojiCatalog.search("   ", 80).isEmpty())
        assertTrue(EmojiCatalog.search("زدجكزدجخ").isEmpty())
    }

    @Test
    fun search_category_labels_surface_whole_shelves() {
        assertTrue(EmojiCatalog.search("طعام").size >= 20)
        assertTrue(EmojiCatalog.search("حيوانات").size >= 20)
        assertTrue(EmojiCatalog.search("قلوب").size >= 10)
    }

    // -------------------------------------------------- catalog integrity

    @Test
    fun catalog_keeps_the_nine_original_shelves_plus_two_new() {
        val labels = EmojiCatalog.categories.map { it.label }
        listOf(
            "وجوه", "قلوب", "إيماءات", "حيوانات", "طعام",
            "أنشطة", "سفر", "أشياء", "رموز", "أشخاص", "أعلام"
        ).forEach { expected ->
            assertTrue("shelf «$expected» missing", expected in labels)
        }
        assertEquals(11, labels.size)
    }

    @Test
    fun catalog_has_no_duplicate_emoji_across_shelves() {
        val all = EmojiCatalog.all
        assertEquals(all.size, all.toSet().size)
        assertTrue("catalog should be rich (got ${all.size})", all.size >= 350)
    }

    @Test
    fun every_emoji_is_searchable_by_at_least_one_keyword() {
        EmojiCatalog.categories.forEach { cat ->
            assertTrue("shelf ${cat.label} lacks label keywords", cat.labelKw.isNotEmpty())
            cat.entries.forEach { entry ->
                assertTrue(
                    "emoji ${entry.e} in ${cat.label} has no keywords",
                    entry.kw.any { it.isNotBlank() }
                )
                // the entry's own emoji must be reachable through search
                val reachable = EmojiCatalog.search(entry.kw.first(), limit = Int.MAX_VALUE)
                assertTrue(
                    "emoji ${entry.e} not reachable via its own keyword",
                    entry.e in reachable
                )
            }
        }
    }

    // --------------------------------------------- one-hand persistence

    @Test
    fun oneHand_default_is_off_and_survives_repo_round_trip() {
        assertEquals(OneHand.OFF, controller.oneHand.value)
        controller.setOneHand(OneHand.LEFT)
        assertTrue(
            drain {
                runBlocking {
                    OneHand.from(AppGraph.repo.setting("one_hand", "off")) == OneHand.LEFT
                }
            }
        )
    }

    // ------------------------------------------ emoji search capture flow

    @Test
    fun emojiSearch_routes_keystrokes_into_capture_not_the_host_field() {
        controller.beginEmojiSearch()
        assertTrue(controller.emojiSearch.value)
        assertEquals("", controller.internalCapture.value)

        controller.onChar("ب")
        controller.onChar("ح")
        assertEquals("بح", controller.internalCapture.value)
        // NOTHING leaked into the host field or the composing buffer.
        assertTrue(ic.committed.isEmpty())
        assertTrue(ic.composing.isEmpty())
        assertEquals("", controller.buffer.value)

        controller.onSpace()
        controller.onChar("ا")
        assertEquals("بح ا", controller.internalCapture.value)

        controller.onBackspace()
        assertEquals("بح ", controller.internalCapture.value)

        // Host-side fences while capture is active:
        controller.commitEnter()
        controller.pasteText("injected")
        assertTrue(ic.committed.isEmpty())
        assertTrue(ic.composing.isEmpty())
    }

    @Test
    fun emojiSearch_end_releases_capture_and_clear_freshens_the_query() {
        controller.beginEmojiSearch()
        controller.onChar("ق")
        assertEquals("ق", controller.internalCapture.value)

        controller.clearEmojiSearch()
        assertEquals("", controller.internalCapture.value)
        assertTrue(controller.emojiSearch.value)

        controller.onChar("ط")
        controller.endEmojiSearch()
        assertFalse(controller.emojiSearch.value)
        assertEquals(null, controller.internalCapture.value)

        // Typing AFTER the search ended goes back to the host field.
        controller.onChar("خ")
        assertEquals("خ", controller.buffer.value)
        assertTrue(ic.composing.isNotEmpty())
    }

    @Test
    fun leaving_the_emoji_page_ends_the_search() {
        controller.setPage(KeyboardPage.EMOJI)
        controller.beginEmojiSearch()
        controller.onChar("س")
        assertTrue(controller.emojiSearch.value)

        controller.setPage(KeyboardPage.LETTERS)
        assertFalse(controller.emojiSearch.value)
        assertEquals(null, controller.internalCapture.value)

        // and a brand-new field starts clean (no dangling capture).
        controller.onChar("ل")
        assertEquals("ل", controller.buffer.value)
    }

    @Test
    fun insertEmoji_during_search_commits_and_keeps_the_search_open() {
        controller.beginEmojiSearch()
        controller.onChar("ق")
        controller.insertEmoji("😀")
        assertTrue("😀" in ic.committed)
        assertTrue(controller.emojiSearch.value)
        assertEquals("ق", controller.internalCapture.value)
        assertTrue("😀" in controller.recentEmojis.value)
    }

    @Test
    fun beginEmojiSearch_is_idempotent_and_clears_stale_strip_state() {
        controller.onChar("س") // composing state in the host field
        assertTrue(controller.buffer.value.isNotEmpty())
        controller.beginEmojiSearch()
        assertEquals("", controller.buffer.value)
        assertTrue(controller.suggestions.value.isEmpty())
        assertEquals("", controller.internalCapture.value)

        controller.beginEmojiSearch() // second call must not eat the query
        assertEquals("", controller.internalCapture.value)
    }
}
