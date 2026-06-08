package com.lorus.rummikubtracker.counter.model

data class DetectedTile(
    val number: Int?,       // 1–13 or null for Joker
    val confidence: Float,  // 0.0–1.0
    val isJoker: Boolean,
    val x: Int,             // Bounding box x
    val y: Int,             // Bounding box y
    val width: Int,         // Bounding box width
    val height: Int         // Bounding box height
)
