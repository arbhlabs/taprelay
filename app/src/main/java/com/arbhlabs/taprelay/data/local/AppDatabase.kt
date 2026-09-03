package com.arbhlabs.taprelay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arbhlabs.taprelay.data.local.dao.TagDao
import com.arbhlabs.taprelay.data.local.entity.TagEntity

@Database(entities = [TagEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN targetType TEXT NOT NULL DEFAULT 'DEVICE'")
            }
        }
    }
}
