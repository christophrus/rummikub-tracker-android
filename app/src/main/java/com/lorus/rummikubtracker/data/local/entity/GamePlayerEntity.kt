package com.lorus.rummikubtracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "game_players",
    primaryKeys = ["gameId", "playerName"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class GamePlayerEntity(
    val gameId: Long,
    val playerName: String,
    val playerOrder: Int,
    val maxExtensions: Int = 3,
    val extensionsUsed: Int = 0,
    val imagePath: String? = null
)
