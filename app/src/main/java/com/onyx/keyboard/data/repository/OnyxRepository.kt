package com.onyx.keyboard.data.repository

import android.content.Context
import com.onyx.keyboard.data.crypto.OnyxCrypto
import com.onyx.keyboard.data.db.BigramDao
import com.onyx.keyboard.data.db.BigramEntity
import com.onyx.keyboard.data.db.ClipDao
import com.onyx.keyboard.data.db.ClipEntity
import com.onyx.keyboard.data.db.DailyStatsEntity
import com.onyx.keyboard.data.db.OnyxDatabase
import com.onyx.keyboard.data.db.ProfileDao
import com.onyx.keyboard.data.db.ProfileEntity
import com.onyx.keyboard.data.db.SettingsDao
import com.onyx.keyboard.data.db.SettingsEntity
import com.onyx.keyboard.data.db.ShortcutDao
import com.onyx.keyboard.data.db.ShortcutEntity
import com.onyx.keyboard.data.db.ThemeDao
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.data.db.Totals
import com.onyx.keyboard.data.db.WordDao
import com.onyx.keyboard.data.db.WordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Single source of truth for everything Onyx persists on this device.
 * Sensitive columns (clipboard content) pass through OnyxCrypto (Keystore AES/GCM).
 */
class OnyxRepository private constructor(context: Context) {

    private val db: OnyxDatabase = OnyxDatabase.get(context)
    private val profileDao: ProfileDao = db.profileDao()
    private val wordDao: WordDao = db.wordDao()
    private val shortcutDao: ShortcutDao = db.shortcutDao()
    private val clipDao: ClipDao = db.clipDao()
    private val bigramDao: BigramDao = db.bigramDao()
    private val statsDao = db.statsDao()
    val settings: SettingsDao = db.settingsDao()

    /** Same-module access (tests + future admin UI) to verify theme seeding. */
    internal val themeDao: ThemeDao = db.themeDao()

    companion object {
        const val KEY_CURRENT_PROFILE = "current_profile_id"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_PARTICLES = "particles"
        const val KEY_AUTOCORRECT = "autocorrect"
        const val KEY_LEARNING = "learning"
        const val KEY_GIF_NET = "gif_net"
        /** Next-word prediction bar (bigram-driven) — user preference. */
        const val KEY_PREDICTIONS = "predictions"
        const val KEY_NUMBER_ROW = "number_row"
        const val KEY_RECENT_EMOJI = "recent_emoji"
        /** Follow the system day/night mode and swap between light/dark presets. */
        const val KEY_AUTO_THEME = "auto_theme"
        /** Privacy switch: when off, copies are never saved to the clip history. */
        const val KEY_CLIP_CAPTURE = "clip_capture"
        const val MAX_CLIPS = 30
        /** Upper bound on user-created themes — keeps the gallery sane. */
        const val MAX_CUSTOM_THEMES = 30
        /** Pending background-photo hand-off from the picker trampoline. */
        const val KEY_PENDING_BG_PICK = "pending_bg_pick"

        @Volatile
        private var instance: OnyxRepository? = null

        fun get(context: Context): OnyxRepository =
            instance ?: synchronized(this) {
                instance ?: OnyxRepository(context).also { instance = it }
            }
    }

    // ---------------------------------------------------------------- profiles

    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    suspend fun currentProfile(): ProfileEntity? {
        val idStr = settings.get(KEY_CURRENT_PROFILE)
        val id = idStr?.toLongOrNull()
        val all = profileDao.all()
        return all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }

    fun observeCurrentProfile(): Flow<ProfileEntity?> =
        combine(settings.observe(KEY_CURRENT_PROFILE), profileDao.observeAll()) { raw, all ->
            val id = raw?.toLongOrNull()
            all.firstOrNull { it.id == id } ?: all.firstOrNull()
        }

    suspend fun setCurrentProfile(id: Long) {
        settings.put(SettingsEntity(KEY_CURRENT_PROFILE, id.toString()))
    }

    suspend fun addProfile(name: String, avatar: String): Long =
        profileDao.insert(
            ProfileEntity(
                name = name.ifBlank { "Profile" }.trim(),
                avatar = avatar.take(2).ifBlank { "O" },
                selectedThemeId = currentProfile()?.selectedThemeId ?: 1L
            )
        )

    suspend fun updateProfile(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun applyTheme(themeId: Long) {
        val p = currentProfile() ?: return
        profileDao.update(p.copy(selectedThemeId = themeId))
    }

    suspend fun deleteProfile(id: Long) {
        if (profileDao.count() <= 1) return // keep at least one
        val victim = profileDao.byId(id) ?: return
        wordDao.deleteAllForProfile(id)
        bigramDao.deleteAllForProfile(id)
        profileDao.delete(victim)
    }

    // ---------------------------------------------------------------- themes

    fun observeThemes(): Flow<List<ThemeEntity>> = themeDao.observeAll()

    suspend fun themeById(id: Long): ThemeEntity? = themeDao.byId(id)

    /**
     * Creates (id = 0) or updates (id of an existing CUSTOM row) a user theme.
     * Factory presets are immutable: trying to save over a builtin id or past
     * [MAX_CUSTOM_THEMES] returns -1 and touches nothing.
     */
    suspend fun saveCustomTheme(theme: ThemeEntity): Long {
        val sanitized = theme.copy(builtin = false)
        if (sanitized.id != 0L) {
            val existing = themeDao.byId(sanitized.id) ?: return -1L
            if (existing.builtin) return -1L
            themeDao.insertAll(listOf(sanitized))
            return sanitized.id
        }
        if (themeDao.countCustom() >= MAX_CUSTOM_THEMES) return -1L
        return themeDao.insert(sanitized)
    }

    /** Deletes a user theme; factory presets can never be deleted. */
    suspend fun deleteCustomTheme(id: Long): Boolean = themeDao.deleteCustomById(id) > 0

    /** The live theme of the active profile, as consumed by the IME surface. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeKeyboardTheme(): Flow<ThemeEntity> =
        observeCurrentProfile().flatMapLatest { profile ->
            themeDao.observeAll().map { list ->
                list.firstOrNull { it.id == (profile?.selectedThemeId ?: -1L) }
                    ?: list.firstOrNull()
                    ?: defaultThemes().first()
            }
        }

    // ---------------------------------------------------------------- words

    fun observeTopWords(pid: Long, limit: Int = 8): Flow<List<WordEntity>> =
        wordDao.observeTop(pid, limit)

    fun observeCustomWords(pid: Long): Flow<List<WordEntity>> = wordDao.observeCustom(pid)

    suspend fun topWords(pid: Long): List<WordEntity> = wordDao.top(pid)

    suspend fun prefixMatch(pid: Long, prefix: String, limit: Int = 8): List<WordEntity> =
        wordDao.prefixMatch(pid, prefix, limit)

    suspend fun recordWord(pid: Long, word: String, lang: String) {
        val w = word.trim().lowercase()
        if (w.length < 2 || w.any { it.isDigit() }) return
        val now = System.currentTimeMillis()
        if (wordDao.bump(pid, w, now) == 0) {
            wordDao.insert(WordEntity(profileId = pid, word = w, lang = lang))
        }
    }

    suspend fun addCustomWord(pid: Long, word: String, lang: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        if (wordDao.bump(pid, w, System.currentTimeMillis()) == 0) {
            wordDao.insert(
                WordEntity(profileId = pid, word = w, lang = lang, source = "custom", freq = 60)
            )
        }
    }

    suspend fun deleteWord(word: WordEntity) = wordDao.deleteById(word.id)

    suspend fun wipeLearnedWords() {
        val all = profileDao.all()
        all.forEach {
            wordDao.deleteAllForProfile(it.id)
            bigramDao.deleteAllForProfile(it.id)
        }
    }

    // ---------------------------------------------------------------- bigrams

    /**
     * Records a word PAIR the user actually typed consecutively. This is the
     * fuel of next-word prediction: the pair is bumped when it already exists
     * so frequently-used continuations float to the top. Hygiene mirrors
     * [recordWord] — trimmed, lowercased, no digits, bounded length — plus
     * the pair must differ (self-continuations are noise).
     */
    suspend fun recordBigram(pid: Long, prev: String, next: String, lang: String) {
        val p = prev.trim().lowercase()
        val n = next.trim().lowercase()
        if (p.isEmpty() || n.isEmpty() || p == n) return
        if (p.length > 24 || n.length > 24) return
        if (p.any { it.isDigit() } || n.any { it.isDigit() }) return
        val now = System.currentTimeMillis()
        if (bigramDao.bump(pid, p, n, now) == 0) {
            bigramDao.insert(
                BigramEntity(profileId = pid, prev = p, next = n, lang = lang)
            )
        }
    }

    /** Best continuations of [prev], most frequent first. */
    suspend fun nextWords(pid: Long, prev: String, limit: Int = 5): List<String> {
        val p = prev.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return bigramDao.nextFor(pid, p, limit).map { it.next }
    }

    // ---------------------------------------------------------------- shortcuts

    fun observeShortcuts(pid: Long): Flow<List<ShortcutEntity>> =
        shortcutDao.observeForProfile(pid)

    suspend fun findShortcut(pid: Long, trigger: String): ShortcutEntity? =
        shortcutDao.find(pid, trigger.trim().lowercase())

    suspend fun addShortcut(pid: Long, trigger: String, expansion: String) {
        val t = trigger.trim().lowercase()
        val e = expansion.trim()
        if (t.isEmpty() || e.isEmpty()) return
        shortcutDao.insert(ShortcutEntity(profileId = pid, trigger = t, expansion = e))
    }

    suspend fun deleteShortcut(id: Long) = shortcutDao.deleteById(id)

    // ---------------------------------------------------------------- clips

    fun observeClips(): Flow<List<ClipEntity>> = clipDao.observeAll()

    suspend fun addClip(text: String) {
        if (text.isBlank()) return
        // Exact-duplicate check across the WHOLE (cap-bounded) history — the old
        // recent(5) window let a re-copied older clip slip in as a second row.
        // A re-copy now moves the clip to the top instead, preserving its pin.
        val dup = clipDao.all().firstOrNull { OnyxCrypto.decrypt(it.contentEnc) == text }
        if (dup != null) {
            clipDao.deleteById(dup.id)
            clipDao.insert(
                ClipEntity(contentEnc = OnyxCrypto.encrypt(text), pinned = dup.pinned)
            )
        } else {
            clipDao.insert(ClipEntity(contentEnc = OnyxCrypto.encrypt(text)))
        }
        clipDao.trim(MAX_CLIPS)
    }

    suspend fun decryptClip(clip: ClipEntity): String = OnyxCrypto.decrypt(clip.contentEnc)

    suspend fun setClipPinned(id: Long, pinned: Boolean) = clipDao.setPinned(id, pinned)

    suspend fun deleteClip(id: Long) = clipDao.deleteById(id)

    suspend fun clearClips() = clipDao.deleteAll()

    // ---------------------------------------------------------------- stats

    fun observeTodayStats(): Flow<DailyStatsEntity?> =
        statsDao.observeDay(today())

    fun observeRecentStats(): Flow<List<DailyStatsEntity>> = statsDao.observeRecent()

    suspend fun totals(): Totals = statsDao.totals()

    suspend fun bumpKeystrokes(n: Long = 1) {
        statsDao.ensureRow(today()); statsDao.addKeystrokes(today(), n)
    }

    suspend fun bumpWords(n: Long = 1) {
        statsDao.ensureRow(today()); statsDao.addWords(today(), n)
    }

    suspend fun bumpEmojis(n: Long = 1) {
        statsDao.ensureRow(today()); statsDao.addEmojis(today(), n)
    }

    suspend fun bumpVoice(n: Long = 1) {
        statsDao.ensureRow(today()); statsDao.addVoice(today(), n)
    }

    suspend fun bumpGifs(n: Long = 1) {
        statsDao.ensureRow(today()); statsDao.addGifs(today(), n)
    }

    private fun today(): String = LocalDate.now().toString()

    // ---------------------------------------------------------------- settings

    suspend fun setting(key: String, def: String): String =
        settings.get(key) ?: def

    fun observeSetting(key: String, def: String): Flow<String> =
        settings.observe(key).map { it ?: def }

    suspend fun putSetting(key: String, value: String) = settings.put(SettingsEntity(key, value))

    // ---------------------------------------------------------------- seeding

    /**
     * Idempotent: inserts default themes + a starter profile on first run.
     * Upgrade path: any FACTORY theme missing from the table (e.g. the light
     * presets added after an older install) is inserted too, without touching
     * themes the user may have customized.
     */
    suspend fun ensureSeeded() {
        val missing = defaultThemes().filter { themeDao.byId(it.id) == null }
        if (missing.isNotEmpty()) {
            themeDao.insertAll(missing)
        }
        if (profileDao.count() == 0) {
            val id = profileDao.insert(
                ProfileEntity(name = "أساسي", avatar = "ع", selectedThemeId = 1L)
            )
            if (settings.get(KEY_CURRENT_PROFILE) == null) {
                settings.put(SettingsEntity(KEY_CURRENT_PROFILE, id.toString()))
            }
        }
    }
}

/**
 * Factory defaults — 28 looks seeded into Room so users can add their own
 * later. Ids 1-6 are the original dark presets; 7-8 the first LIGHT presets
 * used by the auto day/night theme mode; the themes pack added ids 9-21
 * (dark) and 22-28 (light). ensureSeeded() back-fills any missing ids on
 * upgrade without touching user-customized rows.
 */
fun defaultThemes(): List<ThemeEntity> = listOf(
    ThemeEntity(
        id = 1, name = "Onyx Neon",
        surfaceTop = 0xFF0B0B12, surfaceBottom = 0xFF17121F,
        keyFill = 0xFF221C33, keyText = 0xFFF2EDFF,
        accent = 0xFFB37BFF, glow = 0xFF8A5CFF, particle = 0xFFB37BFF
    ),
    ThemeEntity(
        id = 2, name = "Cyber Hologram",
        surfaceTop = 0xFF050B14, surfaceBottom = 0xFF0B1A2A,
        keyFill = 0xFF10243B, keyText = 0xFFE3F6FF,
        accent = 0xFF3EE6FF, glow = 0xFF00B8D4, particle = 0xFF3EE6FF
    ),
    ThemeEntity(
        id = 3, name = "Emerald Pulse",
        surfaceTop = 0xFF06120D, surfaceBottom = 0xFF0C2118,
        keyFill = 0xFF123324, keyText = 0xFFE6FFF3,
        accent = 0xFF3DFFA8, glow = 0xFF00C877, particle = 0xFF3DFFA8
    ),
    ThemeEntity(
        id = 4, name = "Rose Circuit",
        surfaceTop = 0xFF140A11, surfaceBottom = 0xFF24101B,
        keyFill = 0xFF331826, keyText = 0xFFFFEAF4,
        accent = 0xFFFF5CA8, glow = 0xFFE91E63, particle = 0xFFFF5CA8
    ),
    ThemeEntity(
        id = 5, name = "Solar Flare",
        surfaceTop = 0xFF140D06, surfaceBottom = 0xFF261A0A,
        keyFill = 0xFF372611, keyText = 0xFFFFF3E0,
        accent = 0xFFFFB347, glow = 0xFFFF6D00, particle = 0xFFFFB347
    ),
    ThemeEntity(
        id = 6, name = "Midnight Glass",
        surfaceTop = 0xFF080B16, surfaceBottom = 0xFF111631,
        keyFill = 0xFF1A2140, keyText = 0xFFE8ECFF,
        accent = 0xFF7C9EFF, glow = 0xFF536DFE, particle = 0xFF7C9EFF
    ),
    ThemeEntity(
        id = 7, name = "Frost Light",
        surfaceTop = 0xFFF4F6FB, surfaceBottom = 0xFFE4E9F4,
        keyFill = 0xFFFFFFFF, keyText = 0xFF1B2130,
        accent = 0xFF6C4DFF, glow = 0xFF9E7BFF, particle = 0xFF8A6CFF
    ),
    ThemeEntity(
        id = 8, name = "Dawn Light",
        surfaceTop = 0xFFFDF4EC, surfaceBottom = 0xFFF6E7DC,
        keyFill = 0xFFFFFFFF, keyText = 0xFF33261C,
        accent = 0xFFE0653A, glow = 0xFFFF8A50, particle = 0xFFFF8A50
    ),
    // ---- Themes pack: 20 new presets (ids 9-28). 9-21 dark, 22-28 light. ----
    ThemeEntity(
        id = 9, name = "Aurora Borealis",
        surfaceTop = 0xFF04120F, surfaceBottom = 0xFF0A2420,
        keyFill = 0xFF10352E, keyText = 0xFFE7FFF8,
        accent = 0xFF45F0C0, glow = 0xFF00BFA0, particle = 0xFF45F0C0
    ),
    ThemeEntity(
        id = 10, name = "Deep Ocean",
        surfaceTop = 0xFF030A18, surfaceBottom = 0xFF071530,
        keyFill = 0xFF0C2044, keyText = 0xFFE4EEFF,
        accent = 0xFF4D9FFF, glow = 0xFF2962FF, particle = 0xFF4D9FFF
    ),
    ThemeEntity(
        id = 11, name = "Blood Moon",
        surfaceTop = 0xFF120507, surfaceBottom = 0xFF240A0E,
        keyFill = 0xFF38121A, keyText = 0xFFFFE9EC,
        accent = 0xFFFF4D5E, glow = 0xFFC62828, particle = 0xFFFF4D5E
    ),
    ThemeEntity(
        id = 12, name = "Royal Amethyst",
        surfaceTop = 0xFF0C0616, surfaceBottom = 0xFF170D2B,
        keyFill = 0xFF251445, keyText = 0xFFF3EAFF,
        accent = 0xFFB388FF, glow = 0xFF7C4DFF, particle = 0xFFB388FF
    ),
    ThemeEntity(
        id = 13, name = "Sahara Night",
        surfaceTop = 0xFF120E06, surfaceBottom = 0xFF241C0B,
        keyFill = 0xFF382B12, keyText = 0xFFFFF4DE,
        accent = 0xFFFFC857, glow = 0xFFE0A030, particle = 0xFFFFC857
    ),
    ThemeEntity(
        id = 14, name = "Graphite Mono",
        surfaceTop = 0xFF0B0B0B, surfaceBottom = 0xFF161616,
        keyFill = 0xFF242424, keyText = 0xFFF2F2F2,
        accent = 0xFFB0BEC5, glow = 0xFF78909C, particle = 0xFFB0BEC5
    ),
    ThemeEntity(
        id = 15, name = "Sakura Dusk",
        surfaceTop = 0xFF130A12, surfaceBottom = 0xFF231224,
        keyFill = 0xFF351A36, keyText = 0xFFFFEDF7,
        accent = 0xFFFF8AC2, glow = 0xFFD4559E, particle = 0xFFFF8AC2
    ),
    ThemeEntity(
        id = 16, name = "Neon Grid",
        surfaceTop = 0xFF050A05, surfaceBottom = 0xFF0A140A,
        keyFill = 0xFF12240F, keyText = 0xFFEFFFEA,
        accent = 0xFF8FFF3D, glow = 0xFF64DD17, particle = 0xFF8FFF3D
    ),
    ThemeEntity(
        id = 17, name = "Copper Rust",
        surfaceTop = 0xFF110B07, surfaceBottom = 0xFF21160C,
        keyFill = 0xFF33220F, keyText = 0xFFFFF0E2,
        accent = 0xFFE08D57, glow = 0xFFB96635, particle = 0xFFE08D57
    ),
    ThemeEntity(
        id = 18, name = "Arctic Steel",
        surfaceTop = 0xFF0A0F14, surfaceBottom = 0xFF141D26,
        keyFill = 0xFF1F2D3A, keyText = 0xFFE9F1F7,
        accent = 0xFF6FB7E8, glow = 0xFF4A90C2, particle = 0xFF6FB7E8
    ),
    ThemeEntity(
        id = 19, name = "Amber Dune",
        surfaceTop = 0xFF130F07, surfaceBottom = 0xFF26200D,
        keyFill = 0xFF3A3114, keyText = 0xFFFFF6DF,
        accent = 0xFFFFD166, glow = 0xFFE8B33A, particle = 0xFFFFD166
    ),
    ThemeEntity(
        id = 20, name = "Ultraviolet",
        surfaceTop = 0xFF0D0514, surfaceBottom = 0xFF1B0A28,
        keyFill = 0xFF2C1040, keyText = 0xFFF8EBFF,
        accent = 0xFFE254FF, glow = 0xFFAA00FF, particle = 0xFFE254FF
    ),
    ThemeEntity(
        id = 21, name = "Forest Canopy",
        surfaceTop = 0xFF060D06, surfaceBottom = 0xFF0D1C0C,
        keyFill = 0xFF163016, keyText = 0xFFECFFEC,
        accent = 0xFF7ED957, glow = 0xFF4CAF50, particle = 0xFF7ED957
    ),
    ThemeEntity(
        id = 22, name = "Mint Paper",
        surfaceTop = 0xFFEFFAF3, surfaceBottom = 0xFFDDF2E7,
        keyFill = 0xFFFFFFFF, keyText = 0xFF1D3328,
        accent = 0xFF00875A, glow = 0xFF4DB6A0, particle = 0xFF00A878
    ),
    ThemeEntity(
        id = 23, name = "Sky Cotton",
        surfaceTop = 0xFFEFF6FD, surfaceBottom = 0xFFDCEBFA,
        keyFill = 0xFFFFFFFF, keyText = 0xFF1B2A3A,
        accent = 0xFF1976D2, glow = 0xFF64B5F6, particle = 0xFF42A5F5
    ),
    ThemeEntity(
        id = 24, name = "Lavender Mist",
        surfaceTop = 0xFFF5F1FB, surfaceBottom = 0xFFE9E2F6,
        keyFill = 0xFFFFFFFF, keyText = 0xFF2B2140,
        accent = 0xFF673AB7, glow = 0xFFB39DDB, particle = 0xFF9575CD
    ),
    ThemeEntity(
        id = 25, name = "Rose Quartz",
        surfaceTop = 0xFFFBF0F3, surfaceBottom = 0xFFF6DEE6,
        keyFill = 0xFFFFFFFF, keyText = 0xFF3A2230,
        accent = 0xFFC2185B, glow = 0xFFF48FB1, particle = 0xFFEC6E9E
    ),
    ThemeEntity(
        id = 26, name = "Ivory Ink",
        surfaceTop = 0xFFFAF6EE, surfaceBottom = 0xFFEFE7D8,
        keyFill = 0xFFFFFFFF, keyText = 0xFF2E2A22,
        accent = 0xFF8C6D1F, glow = 0xFFC9A84C, particle = 0xFFA67C00
    ),
    ThemeEntity(
        id = 27, name = "Sage Light",
        surfaceTop = 0xFFF1F4EC, surfaceBottom = 0xFFE2E9D8,
        keyFill = 0xFFFFFFFF, keyText = 0xFF26301F,
        accent = 0xFF5E8A3C, glow = 0xFF9CCC65, particle = 0xFF7CB342
    ),
    ThemeEntity(
        id = 28, name = "Pearl Slate",
        surfaceTop = 0xFFF2F4F7, surfaceBottom = 0xFFE1E6EC,
        keyFill = 0xFFFFFFFF, keyText = 0xFF232B33,
        accent = 0xFF455A64, glow = 0xFF90A4AE, particle = 0xFF607D8B
    ),
)
