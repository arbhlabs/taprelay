package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration6To7Test {

    @Test
    fun migration_6_7_versions() {
        assertEquals(6, AppDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, AppDatabase.MIGRATION_6_7.endVersion)
    }
}
