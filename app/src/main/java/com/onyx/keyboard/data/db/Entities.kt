package com.onyx.keyboard.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String = "O",
    val createdAt: Long = System.currentTimeMillis(),
    val selectedThemeId: Long = 1L,
)

@Entity(
    tableName = "words",
    indices = [Index("profileId"), Index(value = ["profileId", "word"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val word: String,
    val lang: String = "ar",          // "ar" | "en"
    val freq: Int = 1,
    val lastUsed: Long = System.currentTimeMillis(),
    val source: String = "learned",   // "learned" | "custom"
)

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val surfaceTop: Long,    // ARGB
    val surfaceBottom: Long,
    val keyFill: Long,
    val keyText: Long,
    val accent: Long,
    val glow: Long,
    val particle: Long,
    val builtin: Boolean = true,
    /**
     * Absolute path of an optional background photo (copied into the app's
     * private storage at pick time — no external storage access at render).
     * Empty = pure-gradient look. v3 schema; NOT part of shared theme codes.
     */
    @ColumnInfo(defaultValue = "")
    val bgUri: String = "",
    /**
     * Corner radius of every key (including the space bar) in dp.
     * 0 = sharp squares, 14 = the classic neon-glass look, 26 = pill keys.
     * v4 schema; travels INSIDE shared theme codes (additive "shape" object).
     */
    @ColumnInfo(defaultValue = "14")
    val keyRadius: Int = 14,
    /**
     * Space-bar face style: 0 = icon (classic), 1 = the word «مسافة»,
     * 2 = silent blank bar. v4 schema; travels inside shared codes too.
     */
    @ColumnInfo(defaultValue = "0")
    val spaceLabel: Int = 0,
)

@Entity(
    tableName = "shortcuts",
    indices = [Index("profileId"), Index(value = ["profileId", "trigger"], unique = true)]
)
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val trigger: String,
    val expansion: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Clipboard history — content is AES/GCM encrypted at rest via OnyxCrypto. */
@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentEnc: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

/** Daily usage statistics (device-local only). */
@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val day: String,       // ISO yyyy-MM-dd
    val keystrokes: Long = 0,
    val words: Long = 0,
    val emojis: Long = 0,
    val voice: Long = 0,
    val gifs: Long = 0,
)

/**
 * Learned word PAIRS (bigrams) — the memory behind next-word prediction.
 * When the user commits "hello" followed by "world", the pair
 * (prev="hello", next="world") is bumped; the next time "hello" is the
 * context, "world" surfaces as a prediction. Fully on-device, per profile.
 */
@Entity(
    tableName = "bigrams",
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "prev", "next"], unique = true)
    ]
)
data class BigramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val prev: String,
    val next: String,
    val lang: String = "ar",          // "ar" | "en"
    val freq: Int = 1,
    val lastUsed: Long = System.currentTimeMillis(),
)

/** Simple key/value settings store persisted in Room. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class Totals(
    val keystrokes: Long = 0,
    val words: Long = 0,
)
