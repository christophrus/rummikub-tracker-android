package org.lorus.rummiq.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the game-tracker DB migrations preserve data across v1 → v2 → v3.
 * Requires a connected device/emulator (instrumented test).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To3_preservesData() {
        // Create the DB at v1 and insert a game + roster row using the v1 schema.
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO games (id, name, status, startTime, timerDuration, originalTimerDuration, " +
                    "maxExtensions, extensionReplenishRounds, ttsLanguage, currentPlayerIndex, currentRound, extensionsUsed) " +
                    "VALUES (1, 'Testspiel', 'active', 0, 60000, 60000, 3, 0, 'de', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO game_players (gameId, playerName, playerOrder, maxExtensions, extensionsUsed) " +
                    "VALUES (1, 'Alice', 0, 3, 0)"
            )
            close()
        }

        // Run both migrations and let Room validate the resulting schema equals v3.
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        // Original data survived.
        db.query("SELECT name FROM games WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Testspiel", c.getString(0))
        }
        // v2 → v3 column exists with its default.
        db.query("SELECT roundBeginnerIndex FROM games WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        // v1 → v2 column exists and is nullable (null for the pre-existing row).
        db.query("SELECT imagePath FROM game_players WHERE gameId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
        db.close()
    }
}
