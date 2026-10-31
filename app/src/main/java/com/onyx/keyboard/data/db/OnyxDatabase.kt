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
        FontEntity::class,
    ],
    // v2: added the "bigrams" table (learned word pairs for next-word
    // prediction). fallbackToDestructiveMigration handles anything older.
    // v3: added themes.bgUri (custom background photo path) — proper
    // migration so users upgrading from v2 keep every row (clips, custom
    // themes, learned words are NOT wiped).
    // v4: added themes.keyRadius + themes.spaceLabel (key/space shape
    // settings edited in the theme editor and carried by share codes).
    // v5: added themes.soundProfile + themes.vibeProfile (per-theme key
    // tap sound + vibration, edited in the theme editor and carried by
    // share codes).
    // v6: added the fonts table (F10 — imported TTF/OTF faces managed by
    // the fonts panel; the settings row KEY_SELECTED_FONT points at one).
    version = 6,
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
    abstract fun fontDao(): FontDao

    companion object {
        @Volatile
        private var instance: OnyxDatabase? = null

        /** v2 → v3: themes gained the optional bgUri column. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE themes ADD COLUMN bgUri TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 → v4: themes gained the key/space shape columns. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE themes ADD COLUMN keyRadius INTEGER NOT NULL DEFAULT 14")
                db.execSQL("ALTER TABLE themes ADD COLUMN spaceLabel INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 → v5: themes gained the per-theme sound/vibration profiles. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE themes ADD COLUMN soundProfile TEXT NOT NULL DEFAULT 'system'")
                db.execSQL("ALTER TABLE themes ADD COLUMN vibeProfile TEXT NOT NULL DEFAULT 'default'")
            }
        }

        /** v5 → v6: the fonts table for imported TTF/OTF faces. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fonts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `path` TEXT NOT NULL, `importedAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): OnyxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OnyxDatabase::class.java,
                    "onyx.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
