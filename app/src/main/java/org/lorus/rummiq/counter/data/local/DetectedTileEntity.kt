package org.lorus.rummiq.counter.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detected_tiles",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["resultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("resultId")]
)
data class DetectedTileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val resultId: Long,
    val number: Int?,
    val confidence: Float,
    val isJoker: Boolean,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
