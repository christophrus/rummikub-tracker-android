package org.lorus.rummiq.counter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AnalysisResultEntity::class, DetectedTileEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rummiq_counter.db"
                )
                    // No exported schema history exists for v1/v2, so allow a one-time recreate
                    // only from those old versions. Any future change (v3+) must ship a real
                    // Migration — no silent data wipe.
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
