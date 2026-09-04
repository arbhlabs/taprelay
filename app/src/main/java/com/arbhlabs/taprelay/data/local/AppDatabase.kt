package com.arbhlabs.taprelay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arbhlabs.taprelay.data.local.dao.ControllerMappingDao
import com.arbhlabs.taprelay.data.local.dao.PlaceTriggerDao
import com.arbhlabs.taprelay.data.local.dao.TagDao
import com.arbhlabs.taprelay.data.local.dao.TapLogDao
import com.arbhlabs.taprelay.data.local.entity.ControllerMappingEntity
import com.arbhlabs.taprelay.data.local.entity.PlaceTriggerEntity
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity

@Database(
    entities = [
        TagEntity::class,
        TapLogEntity::class,
        ControllerMappingEntity::class,
        PlaceTriggerEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun tapLogDao(): TapLogDao
    abstract fun controllerMappingDao(): ControllerMappingDao
    abstract fun placeTriggerDao(): PlaceTriggerDao

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

        /** 0.1.1: Sensibo & Climate dedicated fan speed level. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN fanLevel TEXT")
            }
        }

        /** 0.1.1: Android Bluetooth & USB Game Controller Mappings. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS controller_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        controllerDescriptor TEXT NOT NULL,
                        controllerName TEXT NOT NULL,
                        inputKey TEXT NOT NULL,
                        inputLabel TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_controller_mappings_descriptor_key ON controller_mappings(controllerDescriptor, inputKey)")
            }
        }

        /** 0.1.2: places that drive an item when you arrive or leave. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS place_triggers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        radiusMeters REAL NOT NULL,
                        transition TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        leaveTagId TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        lastFiredAt INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * 0.1.4: an item can log to LastDose instead of driving a device. Every column is
         * nullable and targetType keeps its 'DEVICE' default, so existing tags are untouched.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN lastDoseItemId INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN lastDoseItemName TEXT")
                db.execSQL("ALTER TABLE tags ADD COLUMN lastDoseAmount TEXT")
                db.execSQL("ALTER TABLE tags ADD COLUMN lastDoseUnit TEXT")
            }
        }

        /**
         * 0.1.1: per-item and per-trigger activation mode. Both columns are nullable so every
         * tag and mapping written before this release keeps its old behaviour (execute).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN activationMode TEXT")
                db.execSQL("ALTER TABLE controller_mappings ADD COLUMN activationMode TEXT")
            }
        }
    }
}
