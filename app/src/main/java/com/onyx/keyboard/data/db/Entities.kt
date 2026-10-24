package com.onyx.keyboard.data.db

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
