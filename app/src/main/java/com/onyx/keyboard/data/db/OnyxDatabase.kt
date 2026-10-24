package com.onyx.keyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        WordEntity::class,
        ThemeEntity::class,
        ShortcutEntity::class,
        ClipEntity::class,
        DailyStatsEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class OnyxDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun wordDao(): WordDao
    abstract fun themeDao(): ThemeDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun clipDao(): ClipDao
    abstract fun statsDao(): StatsDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: OnyxDatabase? = null

        fun get(context: Context): OnyxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OnyxDatabase::class.java,
                    "onyx.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
