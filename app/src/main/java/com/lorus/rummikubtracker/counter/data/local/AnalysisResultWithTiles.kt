package com.lorus.rummikubtracker.counter.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class AnalysisResultWithTiles(
    @Embedded
    val result: AnalysisResultEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "resultId"
    )
    val tiles: List<DetectedTileEntity>
)
