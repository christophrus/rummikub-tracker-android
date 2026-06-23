package org.lorus.rummiq.data.repository

import org.lorus.rummiq.data.local.dao.PlayerDao
import org.lorus.rummiq.data.local.entity.PlayerEntity
import org.lorus.rummiq.domain.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val playerDao: PlayerDao
) {
    fun getAllPlayers(): Flow<List<Player>> = playerDao.getAllPlayers().map { entities ->
        entities.map { Player(name = it.name, imagePath = it.imagePath) }
    }

    suspend fun savePlayer(player: Player) {
        playerDao.insertPlayer(PlayerEntity(name = player.name, imagePath = player.imagePath))
    }

    suspend fun deletePlayer(name: String) {
        playerDao.deletePlayerByName(name)
    }

    suspend fun deleteAllPlayers() {
        playerDao.deleteAllPlayers()
    }
}
