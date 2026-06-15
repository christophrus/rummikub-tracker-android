package com.lorus.rummikubtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val status: String, // "active", "completed", "cancelled"
    val startTime: Long,
    val endTime: Long? = null,
    val winner: String? = null,
    val timerDuration: Int,
    val originalTimerDuration: Int,
    val maxExtensions: Int,
    val extensionReplenishRounds: Int = 0,
    val ttsLanguage: String = "en",
    val currentPlayerIndex: Int = 0,
    val roundBeginnerIndex: Int = 0,
    val currentRound: Int = 0,
    val extensionsUsed: Int = 0
)
