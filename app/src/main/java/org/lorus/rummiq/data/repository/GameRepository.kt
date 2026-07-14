package org.lorus.rummiq.data.repository

import androidx.room.withTransaction
import org.lorus.rummiq.data.local.AppDatabase
import org.lorus.rummiq.data.local.dao.*
import org.lorus.rummiq.data.local.entity.*
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.domain.model.Player
import org.lorus.rummiq.domain.model.Round
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val db: AppDatabase,
    private val gameDao: GameDao,
    private val roundDao: RoundDao,
    private val roundScoreDao: RoundScoreDao,
    private val gamePlayerDao: GamePlayerDao
) {
    /** Runs [block] in a single DB transaction. Nesting is supported by Room. */
    suspend fun <T> runInTransaction(block: suspend () -> T): T = db.withTransaction(block)

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

    suspend fun createGame(game: Game): Long = db.withTransaction {
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
            roundBeginnerIndex = game.roundBeginnerIndex,
            currentRound = game.currentRound
        )
        val gameId = gameDao.insertGame(entity)

        val gamePlayers = game.players.mapIndexed { index, player ->
            GamePlayerEntity(
                gameId = gameId,
                playerName = player.name,
                playerOrder = index,
                maxExtensions = game.maxExtensions,
                extensionsUsed = 0,
                imagePath = player.imagePath
            )
        }
        gamePlayerDao.insertGamePlayers(gamePlayers)
        gameId
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
            roundBeginnerIndex = game.roundBeginnerIndex,
            currentRound = game.currentRound
        )
        gameDao.updateGame(entity)
    }

    suspend fun deleteGame(gameId: Long) {
        gameDao.deleteGameById(gameId)
    }

    suspend fun saveRound(round: Round): Long = db.withTransaction {
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
        roundId
    }

    suspend fun updateRound(round: Round) = db.withTransaction {
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

    suspend fun incrementAllMaxExtensions(gameId: Long) {
        gamePlayerDao.incrementAllMaxExtensions(gameId)
    }

    suspend fun updateRoundScore(roundId: Long, playerName: String, score: Int) {
        roundScoreDao.updateScore(roundId, playerName, score)
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
            roundBeginnerIndex = entity.roundBeginnerIndex,
            currentRound = entity.currentRound,
            players = gamePlayers.map {
                Player(name = it.playerName, imagePath = it.imagePath, order = it.playerOrder, maxExtensions = it.maxExtensions, extensionsUsed = it.extensionsUsed)
            },
            rounds = roundsWithScores
        )
    }
}
