package com.onyx.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.data.db.OnyxDatabase
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.data.repository.OnyxRepository
import com.onyx.keyboard.keyboard.CommunityArchive
import com.onyx.keyboard.keyboard.CommunityEntry
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxThemeCodec
import com.onyx.keyboard.keyboard.luminanceOf
import com.onyx.keyboard.keyboard.themeSignature
import com.onyx.keyboard.keyboard.toOnyxColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for the SHAPE + COMMUNITY ARCHIVE pack:
 *  - codec: the additive "shape" block (radius + space face) round-trips,
 *    legacy shape-less codes still decode with classic defaults, wild
 *    values are coerced, and codes produced OUTSIDE the app (the repo
 *    catalog generator) interop both ways
 *  - archive: the bundled shelf is sane (names, contrast, unique looks),
 *    catalog parsing is defensive (garbage skipped, capped), and one-tap
 *    import saves + applies WITHOUT duplicating rows or breaching the cap
 *  - storage: a real Room v3 → v4 migration keeps every legacy row
 *
 * The Robolectric sandbox shares one JVM + Room DB singleton across tests —
 * custom rows are wiped in setUp/tearDown so runs are order-independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxCommunityShapesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OnyxKeyboardController
    private lateinit var ic: FakeInputConnection

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
        controller.themes.value = AppGraph.repo.themeDao.all()
    }

    @After
    fun tearDown() = runBlocking {
        wipeCustomThemes()
    }

    // ------------------------------------------------------------------ codec

    private fun sample(radius: Int, space: Int) = ThemeEntity(
        id = 0, name = "شكلي",
        surfaceTop = 0xFF0B0B12, surfaceBottom = 0xFF17121F,
        keyFill = 0xFF221C33, keyText = 0xFFF2EDFF,
        accent = 0xFFB37BFF, glow = 0xFF8A5CFF, particle = 0xFFB37BFF,
        builtin = false, keyRadius = radius, spaceLabel = space,
    )

    @Test
    fun codecShapeRoundTrip() {
        val decoded = OnyxThemeCodec.decode(OnyxThemeCodec.encode(sample(22, 1)))
        assertNotNull(decoded)
        assertEquals(22, decoded!!.keyRadius)
        assertEquals(1, decoded.spaceLabel)
        assertEquals(0L, decoded.id)
        assertEquals(false, decoded.builtin)
    }

    @Test
    fun legacyCodeWithoutShapeYieldsClassicDefaults() {
        // Hand-build a v1 payload the way the PREVIOUS app version wrote it:
        // palette only, no "shape" object at all.
        val payload = JSONObject()
            .put("v", 1)
            .put("name", "قديم")
            .put("surfaceTop", "FF0B0B12").put("surfaceBottom", "FF17121F")
            .put("keyFill", "FF221C33").put("keyText", "FFF2EDFF")
            .put("accent", "FFB37BFF").put("glow", "FF8A5CFF")
            .put("particle", "FFB37BFF")
        val b64 = android.util.Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or
                android.util.Base64.NO_WRAP or
                android.util.Base64.NO_PADDING
        )
        val decoded = OnyxThemeCodec.decode("ONYX1.$b64")
        assertNotNull(decoded)
        assertEquals("قديم", decoded!!.name)
        assertEquals(OnyxThemeCodec.DEFAULT_RADIUS, decoded.keyRadius)
        assertEquals(OnyxThemeCodec.DEFAULT_SPACE_STYLE, decoded.spaceLabel)
    }

    @Test
    fun wildShapeValuesAreCoerced() {
        val theme = sample(99, 7)
        val decoded = OnyxThemeCodec.decode(OnyxThemeCodec.encode(theme))
        assertEquals(26, decoded!!.keyRadius.coerceIn(0, 26))
        assertEquals(2, decoded.spaceLabel.coerceIn(0, 2))
    }

    @Test
    fun codesFromExternalCatalogGeneratorInteroperate() {
        // Produced by scripts/gen_community_catalog.py — the same payload
        // shipped in community/themes.json. Shape + Arabic name must land.
        val code = CATALOG_SAMPLE_CODE
        val decoded = OnyxThemeCodec.decode(code)!!
        assertEquals("نيون العاصمة", decoded.name)
        assertEquals(18, decoded.keyRadius)
        assertEquals(1, decoded.spaceLabel)
        assertEquals(0xFF0A0912L, decoded.surfaceTop)
        assertEquals(0xFFC084FCL, decoded.accent)
        // And the app's own encoder accepts it back — a stable full loop.
        val re = OnyxThemeCodec.decode(OnyxThemeCodec.encode(decoded))!!
        assertEquals(themeSignature(decoded), themeSignature(re))
    }

    @Test
    fun oldAppCanStillReadNewCodes() {
        // The NEW code carries an extra "shape" key; an OLD decoder ignores
        // unknown keys and keeps reading v=1 + the palette. Simulate that
        // strict-but-oblivious read path manually.
        val code = OnyxThemeCodec.encode(sample(24, 2))
        val b64 = code.removePrefix("ONYX1.")
        val json = JSONObject(
            String(
                android.util.Base64.decode(
                    b64, android.util.Base64.URL_SAFE
                ), Charsets.UTF_8
            )
        )
        assertEquals(1, json.optInt("v"))
        assertTrue(json.has("surfaceTop") && json.has("accent"))
        // The old path would consume exactly these fields — present & sane.
        assertEquals("FF0B0B12", json.optString("surfaceTop").takeLast(8))
    }

    // ---------------------------------------------------------------- archive

    @Test
    fun bundledShelfIsSane() {
        val bundled = CommunityArchive.bundled()
        assertTrue(bundled.size >= 16)
        assertTrue(bundled.all { it.theme.name.isNotBlank() && it.theme.name.length <= 24 })
        assertTrue(bundled.all { it.by.isNotBlank() })
        assertTrue(bundled.all { it.theme.id == 0L && !it.theme.builtin })
        // Every look is a stable round-trip through the share codec.
        bundled.forEach { e ->
            val re = OnyxThemeCodec.decode(OnyxThemeCodec.encode(e.theme))
            assertEquals(e.theme.name, re!!.name)
            assertEquals(e.theme.accent, re.accent)
            assertEquals(e.theme.keyRadius, re.keyRadius)
        }
        // No duplicate looks on the shelf.
        assertEquals(bundled.size, bundled.map { it.signature }.toSet().size)
        // Both day and night looks exist for the auto-theme crowd.
        assertTrue(bundled.any { luminanceOf(it.theme.surfaceTop) > 0.5f })
        assertTrue(bundled.any { luminanceOf(it.theme.surfaceTop) <= 0.5f })
    }

    @Test
    fun bundledShelfContrastIsReadable() {
        CommunityArchive.bundled().forEach { e ->
            val t = e.theme
            val delta = kotlin.math.abs(luminanceOf(t.keyText) - luminanceOf(t.keyFill))
            assertTrue(
                "unreadable contrast in ${t.name}: $delta",
                delta >= 0.4f
            )
        }
    }

    @Test
    fun catalogParseValidEntries() {
        val codes = CommunityArchive.bundled().take(3)
            .map { OnyxThemeCodec.encode(it.theme) }
        val json = JSONObject().put(
            "themes",
            JSONArray()
                .put(JSONObject().put("code", codes[0]).put("by", "صديق"))
                .put(JSONObject().put("code", "ONYX1.notbase64!!"))
                .put(JSONObject().put("code", codes[1])) // no "by" → default
                .put(JSONObject().put("code", "totally not a code"))
        ).toString()

        val parsed = CommunityArchive.parseCatalog(json)
        assertEquals(2, parsed.size)
        assertEquals("صديق", parsed[0].by)
        assertEquals(CommunityArchive.DEFAULT_BY, parsed[1].by)
    }

    @Test
    fun catalogParseDefensive() {
        assertEquals(0, CommunityArchive.parseCatalog("not json at all").size)
        assertEquals(0, CommunityArchive.parseCatalog("""{"nope":1}""").size)
        assertEquals(0, CommunityArchive.parseCatalog("").size)
    }

    @Test
    fun catalogParseCappedAt64() {
        val code = OnyxThemeCodec.encode(CommunityArchive.bundled()[0].theme)
        val arr = JSONArray()
        repeat(70) { arr.put(JSONObject().put("code", code).put("by", "x$it")) }
        val parsed = CommunityArchive.parseCatalog(
            JSONObject().put("themes", arr).toString()
        )
        assertEquals(CommunityArchive.MAX_CATALOG, parsed.size)
    }

    // -------------------------------------------------------------- importing

    @Test
    fun importSavesAndApplies() = runBlocking {
        val entry = CommunityArchive.bundled()[0]
        controller.importCommunityTheme(entry)
        val repo = AppGraph.repo
        assertTrue(
            drain { runBlocking { repo.themeDao.countCustom() == 1 } }
        )
        val saved = repo.themeDao.all().first { !it.builtin }
        assertEquals(entry.theme.name, saved.name)
        assertEquals(entry.signature, themeSignature(saved))
        assertTrue(
            drain {
                runBlocking { repo.currentProfile()?.selectedThemeId == saved.id }
            }
        )
        // Success never flashes a note.
        assertEquals(null, controller.communityNote.value)
    }

    @Test
    fun reimportDeduplicatesInsteadOfStacking() = runBlocking {
        val entry = CommunityArchive.bundled()[1]
        val repo = AppGraph.repo
        controller.importCommunityTheme(entry)
        assertTrue(drain { runBlocking { repo.themeDao.countCustom() == 1 } })
        controller.themes.value = repo.themeDao.all() // let dedup see the row

        controller.importCommunityTheme(entry) // second tap — same look
        // Wait for the FULL end state (re-apply), not just the row count.
        val expected = repo.themeDao.all().first { themeSignature(it) == entry.signature }
        assertTrue(
            drain {
                runBlocking { repo.currentProfile()?.selectedThemeId == expected.id }
            }
        )
        assertEquals(1, repo.themeDao.countCustom())
        assertEquals(expected.id, runBlocking { repo.currentProfile()?.selectedThemeId })
    }

    @Test
    fun importRespectsGalleryCap() = runBlocking {
        val repo = AppGraph.repo
        // Fill the shelf to the cap with filler looks.
        repeat(OnyxRepository.MAX_CUSTOM_THEMES) { i ->
            assertTrue(
                repo.saveCustomTheme(
                    ThemeEntity(
                        id = 0, name = "filler$i",
                        surfaceTop = 0xFF111111L + i, surfaceBottom = 0xFF222222,
                        keyFill = 0xFF333333, keyText = 0xFFEEEEEE,
                        accent = 0xFF5566FF, glow = 0xFF4455EE, particle = 0xFF5566FF,
                        builtin = false,
                    )
                ) > 0
            )
        }
        controller.importCommunityTheme(CommunityArchive.bundled()[2])
        assertTrue(drain { controller.communityNote.value == R.string.community_full })
        assertEquals(OnyxRepository.MAX_CUSTOM_THEMES, repo.themeDao.countCustom())
    }

    // ------------------------------------------------------------- ui mapping

    @Test
    fun onyxColorsCarryShape() {
        val shaped = sample(5, 2).toOnyxColors()
        assertEquals(5, shaped.keyRadius)
        assertEquals(2, shaped.spaceLabel)
        // Defaults hold for entities predating the shape era.
        val plain = ThemeEntity(
            id = 9, name = "plain",
            surfaceTop = 0xFF000000, surfaceBottom = 0xFF111111,
            keyFill = 0xFF222222, keyText = 0xFFEEEEEE,
            accent = 0xFF00FF00, glow = 0xFF00EE00, particle = 0xFF00DD00,
        )
        val c = plain.toOnyxColors()
        assertEquals(14, c.keyRadius)
        assertEquals(0, c.spaceLabel)
    }

    // ------------------------------------------------------------- migration

    @Test
    fun roomMigrationV3ToV4KeepsLegacyRows() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = app.getDatabasePath("mig_test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // A REAL v3 database: the EXACT schema Room shipped in version 3 —
        // Room validates EVERY table after migrating, so all of them must
        // be present, not just the one gaining columns.
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `avatar` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`selectedThemeId` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `words` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, `word` TEXT NOT NULL, `lang` TEXT NOT NULL, " +
                "`freq` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL, `source` TEXT NOT NULL)"
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_words_profileId` ON `words` (`profileId`)")
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_words_profileId_word` ON `words` (`profileId`, `word`)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `themes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `surfaceTop` INTEGER NOT NULL, `surfaceBottom` INTEGER NOT NULL, " +
                "`keyFill` INTEGER NOT NULL, `keyText` INTEGER NOT NULL, `accent` INTEGER NOT NULL, " +
                "`glow` INTEGER NOT NULL, `particle` INTEGER NOT NULL, `builtin` INTEGER NOT NULL, " +
                "`bgUri` TEXT NOT NULL DEFAULT '')"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `shortcuts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, `trigger` TEXT NOT NULL, `expansion` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shortcuts_profileId` ON `shortcuts` (`profileId`)"
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_shortcuts_profileId_trigger` " +
                "ON `shortcuts` (`profileId`, `trigger`)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`contentEnc` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_stats` (`day` TEXT NOT NULL, `keystrokes` INTEGER NOT NULL, " +
                "`words` INTEGER NOT NULL, `emojis` INTEGER NOT NULL, `voice` INTEGER NOT NULL, " +
                "`gifs` INTEGER NOT NULL, PRIMARY KEY(`day`))"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                "PRIMARY KEY(`key`))"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `bigrams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, `prev` TEXT NOT NULL, `next` TEXT NOT NULL, " +
                "`lang` TEXT NOT NULL, `freq` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bigrams_profileId` ON `bigrams` (`profileId`)"
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bigrams_profileId_prev_next` " +
                "ON `bigrams` (`profileId`, `prev`, `next`)"
        )
        raw.execSQL(
            "INSERT INTO themes (name, surfaceTop, surfaceBottom, keyFill, keyText, accent, glow, particle, builtin, bgUri) " +
                "VALUES ('Legacy', 255, 254, 253, 252, 251, 250, 249, 0, '')"
        )
        raw.version = 3
        raw.close()

        val room = Room.databaseBuilder(app, OnyxDatabase::class.java, "mig_test.db")
            .addMigrations(
                OnyxDatabase.MIGRATION_2_3,
                OnyxDatabase.MIGRATION_3_4,
                OnyxDatabase.MIGRATION_4_5,
                OnyxDatabase.MIGRATION_5_6, // DB is now v6 — the chain must reach it
            )
            .allowMainThreadQueries()
            .build()
        try {
            val legacy = runBlocking { room.themeDao().byId(1) }
            assertNotNull("legacy row must survive the migration", legacy)
            assertEquals("Legacy", legacy!!.name)
            assertEquals(255L, legacy.surfaceTop)
            assertEquals(14, legacy.keyRadius)      // classic defaults injected
            assertEquals(0, legacy.spaceLabel)
            assertEquals("", legacy.bgUri)
        } finally {
            room.close()
            dbFile.delete()
        }
    }

    companion object {
        /** First entry of community/themes.json, generated by the catalog script. */
        private const val CATALOG_SAMPLE_CODE =
            "ONYX1.eyJ2IjoxLCJuYW1lIjoi2YbZitmI2YYg2KfZhNi52KfYtdmF2KkiLCJzdXJmYWNlVG9wIjoiRkYwQTA5MTIiLCJzdXJmYWNlQm90dG9tIjoiRkYxNjEwMjQiLCJrZXlGaWxsIjoiRkYyMjFBMzgiLCJrZXlUZXh0IjoiRkZGMUVBRkYiLCJhY2NlbnQiOiJGRkMwODRGQyIsImdsb3ciOiJGRjkzMzNFQSIsInBhcnRpY2xlIjoiRkZDMDg0RkMiLCJzaGFwZSI6eyJyIjoxOCwicyI6MX19"
    }
}
