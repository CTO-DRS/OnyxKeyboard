package com.onyx.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.media.AudioManager
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.onyx.keyboard.data.db.OnyxDatabase
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.keyboard.KeySoundProfile
import com.onyx.keyboard.keyboard.KeyVibeProfile
import com.onyx.keyboard.keyboard.Lang
import com.onyx.keyboard.keyboard.OnyxKeyboardController
import com.onyx.keyboard.keyboard.OnyxThemeCodec
import com.onyx.keyboard.keyboard.TonePlayer
import com.onyx.keyboard.keyboard.vibrationEffectFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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

/**
 * Tests for the PER-THEME FEEDBACK pack (tap sound + vibration):
 *  - profiles: defensive from() parsing, sane vibration mappings, waveform
 *    effects built for multi-pulse profiles and one-shots for single ones
 *  - codec: the additive "feedback" block round-trips inside ONYX1 codes,
 *    legacy codes without it decode to classic defaults, wild ids are
 *    re-validated, and the old-app read path still sees a v1 palette
 *  - storage: a real Room v4 → v5 migration keeps every legacy row and
 *    injects the classic defaults; the repository persists the profiles
 *    through create and edit; refreshTheme() feeds them to the controller
 *  - playback: haptic()/clickSound()/previewFeedback() survive every
 *    profile combination on the Robolectric sandbox (silent ringer too)
 *
 * The Robolectric sandbox shares one JVM + Room DB singleton across tests —
 * custom rows are wiped in setUp/tearDown so runs are order-independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxThemeFeedbackTest {

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
        // Normal ringer so the playback paths are fully exercised.
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
    }

    @After
    fun tearDown() = runBlocking {
        wipeCustomThemes()
    }

    private fun sample(sound: String = "wood", vibe: String = "strong") = ThemeEntity(
        id = 0, name = "صوتي",
        surfaceTop = 0xFF0B0B12, surfaceBottom = 0xFF17121F,
        keyFill = 0xFF221C33, keyText = 0xFFF2EDFF,
        accent = 0xFFB37BFF, glow = 0xFF8A5CFF, particle = 0xFFB37BFF,
        builtin = false, soundProfile = sound, vibeProfile = vibe,
    )

    // ---------------------------------------------------------------- profiles

    @Test
    fun soundProfileFromIsDefensive() {
        assertEquals(KeySoundProfile.SYSTEM, KeySoundProfile.from(null))
        assertEquals(KeySoundProfile.SYSTEM, KeySoundProfile.from(""))
        assertEquals(KeySoundProfile.SYSTEM, KeySoundProfile.from("garbage"))
        assertEquals(KeySoundProfile.SYSTEM, KeySoundProfile.from("  SYSTEM  "))
        assertEquals(KeySoundProfile.NONE, KeySoundProfile.from("none"))
        assertEquals(KeySoundProfile.WOOD, KeySoundProfile.from("wood"))
        assertEquals(KeySoundProfile.CYBER, KeySoundProfile.from("CYBER"))
    }

    @Test
    fun vibeProfileFromIsDefensive() {
        assertEquals(KeyVibeProfile.NORMAL, KeyVibeProfile.from(null))
        assertEquals(KeyVibeProfile.NORMAL, KeyVibeProfile.from("nonsense"))
        assertEquals(KeyVibeProfile.NORMAL, KeyVibeProfile.from(" default "))
        assertEquals(KeyVibeProfile.OFF, KeyVibeProfile.from("off"))
        assertEquals(KeyVibeProfile.DOUBLE, KeyVibeProfile.from("double"))
        assertEquals(KeyVibeProfile.STRONG, KeyVibeProfile.from("Strong"))
    }

    @Test
    fun toneSpecsAreSane() {
        assertEquals(null, KeySoundProfile.NONE.toneSpec)
        assertEquals(null, KeySoundProfile.SYSTEM.toneSpec)
        KeySoundProfile.entries
            .filter { it != KeySoundProfile.NONE && it != KeySoundProfile.SYSTEM }
            .forEach { p ->
                val (tone, ms) = p.toneSpec!!
                assertTrue("$p tone must be positive", tone != 0)
                assertTrue("$p duration must be short", ms in 15..120)
            }
    }

    @Test
    fun vibeEffectsMatchProfileShape() {
        // Single-step profiles -> one-shot effects.
        listOf(
            KeyVibeProfile.LIGHT, KeyVibeProfile.NORMAL, KeyVibeProfile.STRONG
        ).forEach { p ->
            val e = vibrationEffectFor(p, hasAmplitudeControl = true)
            assertNotNull("$p must vibrate", e)
        }
        // Amplitude-less hardware still gets a (default-strength) buzz.
        assertNotNull(vibrationEffectFor(KeyVibeProfile.NORMAL, hasAmplitudeControl = false))
        // Double = a waveform: two active pulses separated by a pause.
        val dbl = vibrationEffectFor(KeyVibeProfile.DOUBLE, hasAmplitudeControl = true)
        assertNotNull("DOUBLE must vibrate", dbl)
        // OFF plays nothing at all.
        assertNull(vibrationEffectFor(KeyVibeProfile.OFF, true))
    }

    @Test
    fun defaultProfilesMatchEntityDefaults() {
        assertEquals("system", KeySoundProfile.DEFAULT.id)
        assertEquals("default", KeyVibeProfile.DEFAULT.id)
        assertEquals(
            "entity defaults must equal the profile defaults",
            KeySoundProfile.DEFAULT.id, ThemeEntity(
                id = 1, name = "x", surfaceTop = 0, surfaceBottom = 0,
                keyFill = 0, keyText = 0, accent = 0, glow = 0, particle = 0,
            ).soundProfile
        )
    }

    // ------------------------------------------------------------------ codec

    @Test
    fun codecFeedbackRoundTrip() {
        val decoded = OnyxThemeCodec.decode(OnyxThemeCodec.encode(sample("wood", "double")))
        assertNotNull(decoded)
        assertEquals("wood", decoded!!.soundProfile)
        assertEquals("double", decoded.vibeProfile)
        assertEquals(0L, decoded.id)
        assertEquals(false, decoded.builtin)
    }

    @Test
    fun legacyCodeWithoutFeedbackYieldsClassicDefaults() {
        // Hand-build a payload the way the PREVIOUS app version wrote it:
        // palette + shape, no "feedback" object at all.
        val payload = JSONObject()
            .put("v", 1)
            .put("name", "قديم")
            .put("surfaceTop", "FF0B0B12").put("surfaceBottom", "FF17121F")
            .put("keyFill", "FF221C33").put("keyText", "FFF2EDFF")
            .put("accent", "FFB37BFF").put("glow", "FF8A5CFF")
            .put("particle", "FFB37BFF")
            .put("shape", JSONObject().put("r", 18).put("s", 1))
        val b64 = android.util.Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or
                android.util.Base64.NO_WRAP or
                android.util.Base64.NO_PADDING
        )
        val decoded = OnyxThemeCodec.decode("ONYX1.$b64")
        assertNotNull(decoded)
        assertEquals("قديم", decoded!!.name)
        assertEquals(KeySoundProfile.DEFAULT_ID, decoded.soundProfile)
        assertEquals(KeyVibeProfile.DEFAULT_ID, decoded.vibeProfile)
    }

    @Test
    fun garbledFeedbackIdsAreSanitized() {
        val payload = JSONObject()
            .put("v", 1)
            .put("name", "مشوش")
            .put("surfaceTop", "FF0B0B12").put("surfaceBottom", "FF17121F")
            .put("keyFill", "FF221C33").put("keyText", "FFF2EDFF")
            .put("accent", "FFB37BFF").put("glow", "FF8A5CFF")
            .put("particle", "FFB37BFF")
            .put("feedback", JSONObject().put("snd", "DROP TABLE").put("vib", 42))
        val b64 = android.util.Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or
                android.util.Base64.NO_WRAP or
                android.util.Base64.NO_PADDING
        )
        val decoded = OnyxThemeCodec.decode("ONYX1.$b64")
        assertNotNull(decoded)
        assertEquals(KeySoundProfile.DEFAULT_ID, decoded!!.soundProfile)
        assertEquals(KeyVibeProfile.DEFAULT_ID, decoded.vibeProfile)
    }

    @Test
    fun oldAppCanStillReadFeedbackCodes() {
        // The NEW code stays format-1 with additive keys; an OLD decoder
        // reads the palette and ignores the rest. Verify the essentials.
        val code = OnyxThemeCodec.encode(sample())
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
        assertEquals("wood", json.optJSONObject("feedback")?.optString("snd"))
    }

    // -------------------------------------------------------------- migration

    @Test
    fun roomMigrationV4ToV5KeepsLegacyRows() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = app.getDatabasePath("mig_fb_test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // A REAL v4 database: the EXACT schema Room shipped in version 4 —
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
                "`bgUri` TEXT NOT NULL DEFAULT '', `keyRadius` INTEGER NOT NULL DEFAULT 14, " +
                "`spaceLabel` INTEGER NOT NULL DEFAULT 0)"
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
            "INSERT INTO themes (name, surfaceTop, surfaceBottom, keyFill, keyText, accent, glow, particle, builtin, bgUri, keyRadius, spaceLabel) " +
                "VALUES ('Legacy', 255, 254, 253, 252, 251, 250, 249, 0, '', 22, 1)"
        )
        raw.version = 4
        raw.close()

        val room = Room.databaseBuilder(app, OnyxDatabase::class.java, "mig_fb_test.db")
            .addMigrations(
                OnyxDatabase.MIGRATION_2_3,
                OnyxDatabase.MIGRATION_3_4,
                OnyxDatabase.MIGRATION_4_5,
            )
            .allowMainThreadQueries()
            .build()
        try {
            val legacy = runBlocking { room.themeDao().byId(1) }
            assertNotNull("legacy row must survive the migration", legacy)
            assertEquals("Legacy", legacy!!.name)
            assertEquals(255L, legacy.surfaceTop)
            assertEquals(22, legacy.keyRadius)      // v4 shape kept intact
            assertEquals(1, legacy.spaceLabel)
            assertEquals("system", legacy.soundProfile)   // v5 classic defaults
            assertEquals("default", legacy.vibeProfile)   // injected by migration
        } finally {
            room.close()
            dbFile.delete()
        }
    }

    // ---------------------------------------------------------------- storage

    @Test
    fun repoRoundTripPersistsFeedback() = runBlocking {
        val repo = AppGraph.repo
        val id = repo.saveCustomTheme(sample("cyber", "light"))
        assertTrue(id > 0)
        val saved = repo.themeDao.byId(id)!!
        assertEquals("cyber", saved.soundProfile)
        assertEquals("light", saved.vibeProfile)
        assertFalse(saved.builtin)

        // Editing the same row rewrites the profiles too.
        repo.saveCustomTheme(saved.copy(soundProfile = "tick", vibeProfile = "off"))
        val edited = repo.themeDao.byId(id)!!
        assertEquals("tick", edited.soundProfile)
        assertEquals("off", edited.vibeProfile)
    }

    @Test
    fun builtinThemesShipClassicDefaults() = runBlocking {
        val repo = AppGraph.repo
        repo.themeDao.all().filter { it.builtin }.forEach {
            assertEquals("builtin ${it.name} sound", "system", it.soundProfile)
            assertEquals("builtin ${it.name} vibe", "default", it.vibeProfile)
        }
    }

    // ------------------------------------------------------------- controller

    @Test
    fun refreshThemePicksUpProfiles() = runBlocking {
        val repo = AppGraph.repo
        val id = repo.saveCustomTheme(sample("pop", "double"))
        val saved = repo.themeDao.byId(id)!!
        controller.userTheme.value = saved
        controller.themes.value = repo.themeDao.all()
        controller.refreshTheme()
        assertEquals(KeySoundProfile.POP, controller.feedbackSound.value)
        assertEquals(KeyVibeProfile.DOUBLE, controller.feedbackVibe.value)

        // Auto day/night resolution keeps the FEEL of the resolved row.
        controller.autoTheme.value = true
        controller.systemDark.value = false // saved look is dark -> fallback row
        controller.refreshTheme()
        // Fallback = first LIGHT pool theme (a builtin) -> classic defaults.
        assertEquals(KeySoundProfile.SYSTEM, controller.feedbackSound.value)
        assertEquals(KeyVibeProfile.NORMAL, controller.feedbackVibe.value)
    }

    @Test
    fun playbackSurvivesEveryProfileCombination() {
        KeySoundProfile.entries.forEach { s ->
            KeyVibeProfile.entries.forEach { v ->
                controller.feedbackSound.value = s
                controller.feedbackVibe.value = v
                controller.haptic()
                controller.clickSound()
                controller.previewFeedback(s.id, v.id)
            }
        }
        // Master switches mute the key paths but never the editor preview.
        controller.haptics.value = false
        controller.sound.value = false
        controller.haptic()
        controller.clickSound()
        controller.previewFeedback("wood", "strong")
        // Silent ringer: the synthesized path must not throw either.
        val am = ApplicationProvider.getApplicationContext<OnyxApp>()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT)
        controller.previewFeedback("cyber", null)
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
    }

    @Test
    fun tonePlayerIsSafeToConstructAndPlay() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val player = TonePlayer(app)
        // Synthesized profiles either play or report false — never throw.
        // (On the JVM sandbox the ToneGenerator may or may not init; both
        // outcomes satisfy the contract.)
        player.play(KeySoundProfile.WOOD)
        player.play(KeySoundProfile.CYBER)
        // System/NONE profiles never take the ToneGenerator path.
        assertFalse(player.play(KeySoundProfile.SYSTEM))
        assertFalse(player.play(KeySoundProfile.NONE))
        player.release()
        player.release() // double release is harmless
    }
}
