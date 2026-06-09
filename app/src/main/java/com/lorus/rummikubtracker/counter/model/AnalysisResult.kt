package com.lorus.rummikubtracker.counter.model

data class AnalysisResult(
    val tiles: List<DetectedTile>,
    val totalScore: Int,
    val tileCount: Int,
    val processingTimeMs: Long,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)
