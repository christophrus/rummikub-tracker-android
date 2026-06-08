package com.lorus.rummikubtracker.data.repository

import com.lorus.rummikubtracker.data.local.dao.*
import com.lorus.rummikubtracker.data.local.entity.*
import com.lorus.rummikubtracker.domain.model.Game
import com.lorus.rummikubtracker.domain.model.Player
import com.lorus.rummikubtracker.domain.model.Round
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val roundDao: RoundDao,
    private val roundScoreDao: RoundScoreDao,
    private val gamePlayerDao: GamePlayerDao
) {
    fun getActiveGame(): Flow<Game?> = gameDao.getActiveGameFlow().map { entity ->
        entity?.let { gameEntityToGame(it) }
    }

    suspend fun getActiveGameOnce(): Game? {
        val entity = gameDao.getActiveGame() ?: return null
        return gameEntityToGame(entity)
    }

    fun getGameById(gameId: Long): Flow<Game?> = gameDao.getGameByIdFlow(gameId).map { entity ->
        entity?.let { gameEntityToGame(it) }
    }

    fun getCompletedGames(): Flow<List<Game>> = gameDao.getCompletedGames().map { entities ->
        entities.map { gameEntityToGame(it) }
    }

    suspend fun createGame(game: Game): Long {
        val entity = GameEntity(
            name = game.name,
            status = game.status,
            startTime = game.startTime,
            timerDuration = game.timerDuration,
            originalTimerDuration = game.originalTimerDuration,
            maxExtensions = game.maxExtensions,
            extensionReplenishRounds = game.extensionReplenishRounds,
            ttsLanguage = game.ttsLanguage,
            currentPlayerIndex = game.currentPlayerIndex,
            currentRound = game.currentRound
        )
        val gameId = gameDao.insertGame(entity)

        val gamePlayers = game.players.mapIndexed { index, player ->
            GamePlayerEntity(
                gameId = gameId,
                playerName = player.name,
                playerOrder = index,
                maxExtensions = game.maxExtensions,
                extensionsUsed = 0
            )
        }
        gamePlayerDao.insertGamePlayers(gamePlayers)
        return gameId
    }

    suspend fun updateGame(game: Game) {
        val entity = GameEntity(
            id = game.id,
            name = game.name,
            status = game.status,
            startTime = game.startTime,
            endTime = game.endTime,
            winner = game.winner,
            timerDuration = game.timerDuration,
            originalTimerDuration = game.originalTimerDuration,
            maxExtensions = game.maxExtensions,
            extensionReplenishRounds = game.extensionReplenishRounds,
            ttsLanguage = game.ttsLanguage,
            currentPlayerIndex = game.currentPlayerIndex,
            currentRound = game.currentRound
        )
        gameDao.updateGame(entity)
    }

    suspend fun deleteGame(gameId: Long) {
        gameDao.deleteGameById(gameId)
    }

    suspend fun saveRound(round: Round): Long {
        val entity = RoundEntity(
            gameId = round.gameId,
            roundNumber = round.roundNumber,
            timestamp = round.timestamp,
            winnerPlayerName = round.winnerPlayerName
        )
        val roundId = roundDao.insertRound(entity)

        val scores = round.scores.map { (playerName, score) ->
            RoundScoreEntity(roundId = roundId, playerName = playerName, score = score)
        }
        roundScoreDao.insertScores(scores)
        return roundId
    }

    suspend fun updateRound(round: Round) {
        val entity = RoundEntity(
            id = round.id,
            gameId = round.gameId,
            roundNumber = round.roundNumber,
            timestamp = round.timestamp,
            winnerPlayerName = round.winnerPlayerName
        )
        roundDao.updateRound(entity)

        roundScoreDao.deleteScoresForRound(round.id)
        val scores = round.scores.map { (playerName, score) ->
            RoundScoreEntity(roundId = round.id, playerName = playerName, score = score)
        }
        roundScoreDao.insertScores(scores)
    }

    suspend fun incrementExtensionsUsed(gameId: Long, playerName: String): Int {
        return gamePlayerDao.incrementExtensionsUsed(gameId, playerName)
    }

    suspend fun resetAllExtensions(gameId: Long) {
        gamePlayerDao.resetAllExtensions(gameId)
    }

    private suspend fun gameEntityToGame(entity: GameEntity): Game {
        val gamePlayers = gamePlayerDao.getPlayersForGameOnce(entity.id)
        val rounds = roundDao.getRoundsForGameOnce(entity.id)
        val roundsWithScores = rounds.map { round ->
            val scores = roundScoreDao.getScoresForRoundOnce(round.id)
            Round(
                id = round.id,
                gameId = round.gameId,
                roundNumber = round.roundNumber,
                timestamp = round.timestamp,
                scores = scores.associate { it.playerName to it.score },
                winnerPlayerName = round.winnerPlayerName
            )
        }
        return Game(
            id = entity.id,
            name = entity.name,
            status = entity.status,
            startTime = entity.startTime,
            endTime = entity.endTime,
            winner = entity.winner,
            timerDuration = entity.timerDuration,
            originalTimerDuration = entity.originalTimerDuration,
            maxExtensions = entity.maxExtensions,
            extensionReplenishRounds = entity.extensionReplenishRounds,
            ttsLanguage = entity.ttsLanguage,
            currentPlayerIndex = entity.currentPlayerIndex,
            currentRound = entity.currentRound,
            players = gamePlayers.map {
                Player(name = it.playerName, order = it.playerOrder, maxExtensions = it.maxExtensions, extensionsUsed = it.extensionsUsed)
            },
            rounds = roundsWithScores
        )
    }
}
