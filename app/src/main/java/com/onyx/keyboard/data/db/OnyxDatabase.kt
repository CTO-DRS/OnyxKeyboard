package com.onyx.keyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        WordEntity::class,
        ThemeEntity::class,
        ShortcutEntity::class,
        ClipEntity::class,
        DailyStatsEntity::class,
        SettingsEntity::class,
        BigramEntity::class,
    ],
    // v2: added the "bigrams" table (learned word pairs for next-word
    // prediction). fallbackToDestructiveMigration handles anything older.
    // v3: added themes.bgUri (custom background photo path) — proper
    // migration so users upgrading from v2 keep every row (clips, custom
    // themes, learned words are NOT wiped).
    version = 3,
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
    abstract fun bigramDao(): BigramDao

    companion object {
        @Volatile
        private var instance: OnyxDatabase? = null

        /** v2 → v3: themes gained the optional bgUri column. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE themes ADD COLUMN bgUri TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): OnyxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OnyxDatabase::class.java,
                    "onyx.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
