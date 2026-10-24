package com.onyx.keyboard.data.repository

import android.content.Context
import com.onyx.keyboard.data.crypto.OnyxCrypto
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
    private val themeDao: ThemeDao = db.themeDao()
    private val shortcutDao: ShortcutDao = db.shortcutDao()
    private val clipDao: ClipDao = db.clipDao()
    private val statsDao = db.statsDao()
    val settings: SettingsDao = db.settingsDao()

    companion object {
        const val KEY_CURRENT_PROFILE = "current_profile_id"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_PARTICLES = "particles"
        const val KEY_AUTOCORRECT = "autocorrect"
        const val KEY_LEARNING = "learning"
        const val KEY_GIF_NET = "gif_net"
        const val MAX_CLIPS = 30

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
        profileDao.delete(victim)
    }

    // ---------------------------------------------------------------- themes

    fun observeThemes(): Flow<List<ThemeEntity>> = themeDao.observeAll()

    suspend fun themeById(id: Long): ThemeEntity? = themeDao.byId(id)

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
        all.forEach { wordDao.deleteAllForProfile(it.id) }
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
        val recent = clipDao.recent()
        // Skip duplicates of the last entries (cheap exact check on decrypted head)
        val dup = recent.any { OnyxCrypto.decrypt(it.contentEnc) == text }
        if (dup) return
        clipDao.insert(
            ClipEntity(contentEnc = OnyxCrypto.encrypt(text))
        )
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

    /** Idempotent: inserts default themes + a starter profile on first run. */
    suspend fun ensureSeeded() {
        if (themeDao.count() == 0) {
            themeDao.insertAll(defaultThemes())
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

/** Factory defaults — six glass/neon looks seeded into Room so users can add their own later. */
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
)
