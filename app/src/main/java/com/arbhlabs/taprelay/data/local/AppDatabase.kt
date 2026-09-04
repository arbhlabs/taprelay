package com.arbhlabs.taprelay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arbhlabs.taprelay.data.local.dao.TagDao
import com.arbhlabs.taprelay.data.local.entity.TagEntity

@Database(entities = [TagEntity::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao

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
    }
}
