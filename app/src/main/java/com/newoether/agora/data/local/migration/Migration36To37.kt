package com.newoether.agora.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds per-conversation voice profile selection. */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE conversations ADD COLUMN voiceProfileId TEXT DEFAULT NULL"
        )
    }
}
