package org.lorus.rummiq.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for the game-tracker database ("rummiq.db").
 *
 * Derived from the exported schema history (app/schemas/…):
 *  - v1 → v2: `imagePath` column added to `game_players`.
 *  - v2 → v3: `roundBeginnerIndex` column added to `games`.
 *
 * Both are additive `ALTER TABLE … ADD COLUMN`, so existing rows are preserved. The NOT NULL
 * column gets a DEFAULT for existing rows; Room does not flag the default because the entity
 * declares none (default validation only runs when the entity itself specifies one).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_players ADD COLUMN imagePath TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN roundBeginnerIndex INTEGER NOT NULL DEFAULT 0")
    }
}

/** All tracker-DB migrations, ordered. Extend this list for future schema versions. */
val TRACKER_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
