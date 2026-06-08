package com.lorus.rummikubtracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "round_scores",
    primaryKeys = ["roundId", "playerName"],
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roundId")]
)
data class RoundScoreEntity(
    val roundId: Long,
    val playerName: String,
    val score: Int
)
