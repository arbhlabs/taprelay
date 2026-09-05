package com.arbhlabs.taprelay

import androidx.room.migration.Migration
import com.arbhlabs.taprelay.data.local.AppDatabase
import com.arbhlabs.taprelay.data.local.TAPRELAY_SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariant that matters more than any single migration: there is an unbroken path from
 * version 1 to whatever `@Database` currently declares.
 *
 * TapRelay never uses `fallbackToDestructiveMigration`, so a gap here does not quietly drop a
 * table - it throws on first launch after an update, on a phone whose owner has their whole house
 * mapped to stickers. This test is what stops a schema change shipping without its migration.
 */
class MigrationChainTest {

    private val migrations: List<Migration> = listOf(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11
    )

    /** The same constant `@Database(version = ...)` is declared with. */
    private val declaredVersion: Int get() = TAPRELAY_SCHEMA_VERSION

    @Test fun every_step_from_one_to_the_declared_version_has_a_migration() {
        val byStart = migrations.associateBy { it.startVersion }
        for (version in 1 until declaredVersion) {
            val step = byStart[version]
            assertTrue(
                "No migration from schema $version to ${version + 1}. A tag database written by " +
                    "an older TapRelay would fail to open.",
                step != null
            )
            assertEquals(
                "Migration from $version must land on ${version + 1}, not ${step!!.endVersion}",
                version + 1,
                step.endVersion
            )
        }
    }

    @Test fun the_chain_ends_exactly_at_the_declared_version() {
        assertEquals(declaredVersion, migrations.maxOf { it.endVersion })
    }

    /** A duplicate start version means one of two migrations silently never runs. */
    @Test fun no_two_migrations_start_at_the_same_version() {
        assertEquals(migrations.size, migrations.map { it.startVersion }.toSet().size)
    }

    @Test fun the_0_2_1_migration_goes_from_ten_to_eleven() {
        assertEquals(10, AppDatabase.MIGRATION_10_11.startVersion)
        assertEquals(11, AppDatabase.MIGRATION_10_11.endVersion)
    }
}
