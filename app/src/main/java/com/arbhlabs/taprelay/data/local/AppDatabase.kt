package com.arbhlabs.taprelay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arbhlabs.taprelay.data.local.dao.TagDao
import com.arbhlabs.taprelay.data.local.dao.TapLogDao
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity

@Database(entities = [TagEntity::class, TapLogEntity::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun tapLogDao(): TapLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN targetType TEXT NOT NULL DEFAULT 'DEVICE'")
            }
        }

        /** 0.0.5: a tag can set a brightness or a colour, not just a power state. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN brightnessPercent INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN colorRgb INTEGER")
            }
        }

        /** 0.0.5: one tag can drive several lights at once. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN additionalTargets TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** 0.1.1: Context-aware time conditions and diagnostic tap history. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN timeConditionEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tags ADD COLUMN startHour INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN startMinute INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN endHour INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN endMinute INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN offActionType TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tap_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tagId TEXT NOT NULL,
                        tagName TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        actionDescription TEXT NOT NULL,
                        success INTEGER NOT NULL,
                        errorMessage TEXT,
                        durationMs INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
