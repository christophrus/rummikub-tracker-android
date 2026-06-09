package com.lorus.rummikubtracker.counter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_results")
data class AnalysisResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val totalScore: Int,
    val tileCount: Int,
    val processingTimeMs: Long,
    val imagePath: String?,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)
