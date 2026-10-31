package com.onyx.keyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE profileId = :pid ORDER BY freq DESC, lastUsed DESC LIMIT :limit")
    fun observeTop(pid: Long, limit: Int): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE profileId = :pid ORDER BY freq DESC, lastUsed DESC LIMIT 400")
    suspend fun top(pid: Long): List<WordEntity>

    @Query(
        "SELECT * FROM words WHERE profileId = :pid AND word LIKE :prefix || '%' " +
            "ORDER BY freq DESC, lastUsed DESC LIMIT :limit"
    )
    suspend fun prefixMatch(pid: Long, prefix: String, limit: Int): List<WordEntity>

    @Query(
        "SELECT * FROM words WHERE profileId = :pid AND source = 'custom' " +
            "ORDER BY lastUsed DESC"
    )
    fun observeCustom(pid: Long): Flow<List<WordEntity>>

    @Query(
        "UPDATE words SET freq = freq + 1, lastUsed = :now " +
            "WHERE profileId = :pid AND word = :word"
    )
    suspend fun bump(pid: Long, word: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntity): Long

    @Update
    suspend fun update(word: WordEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM words WHERE profileId = :pid")
    suspend fun deleteAllForProfile(pid: Long)

    @Query("SELECT COUNT(*) FROM words WHERE profileId = :pid")
    suspend fun countForProfile(pid: Long): Int

    /** F18 — full dump for the encrypted backup export. */
    @Query("SELECT * FROM words")
    suspend fun allForBackup(): List<WordEntity>
}

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes ORDER BY id")
    fun observeAll(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes ORDER BY id")
    suspend fun all(): List<ThemeEntity>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun byId(id: Long): ThemeEntity?

    @Query("SELECT COUNT(*) FROM themes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(themes: List<ThemeEntity>)

    /** Single-row insert; with an explicit id + REPLACE this becomes an update. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(theme: ThemeEntity): Long

    @Query("SELECT COUNT(*) FROM themes WHERE builtin = 0")
    suspend fun countCustom(): Int

    /** Returns 0 when [id] is a factory preset — builtins can never be removed. */
    @Query("DELETE FROM themes WHERE id = :id AND builtin = 0")
    suspend fun deleteCustomById(id: Long): Int

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts WHERE profileId = :pid ORDER BY createdAt DESC")
    fun observeForProfile(pid: Long): Flow<List<ShortcutEntity>>

    @Query("SELECT * FROM shortcuts WHERE profileId = :pid AND trigger = :trigger LIMIT 1")
    suspend fun find(pid: Long, trigger: String): ShortcutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shortcut: ShortcutEntity): Long

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** F18 — full dump for the encrypted backup export. */
    @Query("SELECT * FROM shortcuts")
    suspend fun allForBackup(): List<ShortcutEntity>

    /** F17 — one REPLACE per imported CSV row (dedupes by unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shortcuts: List<ShortcutEntity>)
}

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY createdAt DESC LIMIT 5")
    suspend fun recent(): List<ClipEntity>

    /** Full (cap-bounded) history for exact-duplicate detection on capture. */
    @Query("SELECT * FROM clips")
    suspend fun all(): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: ClipEntity): Long

    @Query("UPDATE clips SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clips")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM clips")
    suspend fun count(): Int

    @Query(
        "DELETE FROM clips WHERE id IN " +
            "(SELECT id FROM clips WHERE pinned = 0 ORDER BY createdAt DESC LIMIT -1 OFFSET :keep)"
    )
    suspend fun trim(keep: Int)
}

@Dao
interface StatsDao {
    /**
     * Ensures a zeroed row exists for [e.day]. Room's @Insert fills the
     * Kotlin defaults (0) for every omitted column — a raw
     * `INSERT OR IGNORE (day)` would silently violate the generated NOT
     * NULL constraints (no SQL DEFAULTs on this table) and record nothing.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureRow(e: DailyStatsEntity)

    @Query("UPDATE daily_stats SET keystrokes = keystrokes + :n WHERE `day` = :day")
    suspend fun addKeystrokes(day: String, n: Long)

    @Query("UPDATE daily_stats SET words = words + :n WHERE `day` = :day")
    suspend fun addWords(day: String, n: Long)

    @Query("UPDATE daily_stats SET emojis = emojis + :n WHERE `day` = :day")
    suspend fun addEmojis(day: String, n: Long)

    @Query("UPDATE daily_stats SET voice = voice + :n WHERE `day` = :day")
    suspend fun addVoice(day: String, n: Long)

    @Query("UPDATE daily_stats SET gifs = gifs + :n WHERE `day` = :day")
    suspend fun addGifs(day: String, n: Long)

    @Query("SELECT * FROM daily_stats WHERE `day` = :day")
    fun observeDay(day: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM daily_stats ORDER BY `day` DESC LIMIT 60")
    fun observeRecent(): Flow<List<DailyStatsEntity>>

    @Query(
        "SELECT COALESCE(SUM(keystrokes), 0) AS keystrokes, " +
            "COALESCE(SUM(words), 0) AS words FROM daily_stats"
    )
    suspend fun totals(): Totals

    /** Full wipe — sandbox order-independence for the test suite. */
    @Query("DELETE FROM daily_stats")
    suspend fun wipe()
}

@Dao
interface BigramDao {
    /** Most-frequent continuations of [prev] for this profile. */
    @Query(
        "SELECT * FROM bigrams WHERE profileId = :pid AND prev = :prev " +
            "ORDER BY freq DESC, lastUsed DESC LIMIT :limit"
    )
    suspend fun nextFor(pid: Long, prev: String, limit: Int): List<BigramEntity>

    @Query(
        "UPDATE bigrams SET freq = freq + 1, lastUsed = :now " +
            "WHERE profileId = :pid AND prev = :prev AND next = :next"
    )
    suspend fun bump(pid: Long, prev: String, next: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bigram: BigramEntity): Long

    @Query("DELETE FROM bigrams WHERE profileId = :pid")
    suspend fun deleteAllForProfile(pid: Long)

    /** F18 — full dump for the encrypted backup export. */
    @Query("SELECT * FROM bigrams")
    suspend fun allForBackup(): List<BigramEntity>
}

@Dao
interface FontDao {
    @Query("SELECT * FROM fonts ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<FontEntity>>

    @Query("SELECT * FROM fonts ORDER BY importedAt DESC")
    suspend fun all(): List<FontEntity>

    @Query("SELECT * FROM fonts WHERE id = :id")
    suspend fun byId(id: Long): FontEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(font: FontEntity): Long

    @Query("SELECT COUNT(*) FROM fonts")
    suspend fun count(): Int

    @Query("DELETE FROM fonts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    /** F18 — full dump for the encrypted backup export. */
    @Query("SELECT * FROM settings")
    suspend fun allForBackup(): List<SettingsEntity>
}
