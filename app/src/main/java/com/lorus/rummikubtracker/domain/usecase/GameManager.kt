package com.lorus.rummikubtracker.domain.usecase

import com.lorus.rummikubtracker.data.repository.GameRepository
import com.lorus.rummikubtracker.domain.model.Game
import com.lorus.rummikubtracker.domain.model.Round
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameManager @Inject constructor(
    private val gameRepository: GameRepository
) {
    suspend fun startGame(game: Game): Long {
        return gameRepository.createGame(game.copy(status = "active"))
    }

    suspend fun saveRound(game: Game, round: Round) {
        gameRepository.saveRound(round)
        val nextRound = game.currentRound + 1
        val nextPlayerIndex = (game.currentPlayerIndex + 1) % game.players.size
        gameRepository.updateGame(
            game.copy(
                currentRound = nextRound,
                currentPlayerIndex = nextPlayerIndex
            )
        )

        // Handle extension replenishment
        if (game.extensionReplenishRounds > 0 && nextRound % game.extensionReplenishRounds == 0) {
            gameRepository.resetAllExtensions(game.id)
        }
    }

    suspend fun endGame(game: Game): Game {
        val winner = WinnerCalculator.calculateWinner(game)
        val completedGame = game.copy(
            status = "completed",
            endTime = System.currentTimeMillis(),
            winner = winner
        )
        gameRepository.updateGame(completedGame)
        return completedGame
    }

    suspend fun cancelGame(game: Game) {
        gameRepository.deleteGame(game.id)
    }

    suspend fun deleteGame(gameId: Long) {
        gameRepository.deleteGame(gameId)
    }

    suspend fun incrementExtensions(gameId: Long, playerName: String): Boolean {
        val result = gameRepository.incrementExtensionsUsed(gameId, playerName)
        return result > 0
    }

    suspend fun resetAllExtensions(gameId: Long) {
        gameRepository.resetAllExtensions(gameId)
    }
}

object ScoreValidator {
    fun validate(scores: Map<String, Int>, playerNames: List<String>): ValidationResult {
        // All players must have a score
        val missingPlayers = playerNames.filter { it !in scores }
        if (missingPlayers.isNotEmpty()) {
            return ValidationResult(false, "All scores must be entered")
        }

        // Exactly one player must have score 0
        val zeroScorePlayers = scores.filter { it.value == 0 }
        if (zeroScorePlayers.size != 1) {
            return ValidationResult(false, "Exactly one player must have score 0")
        }

        return ValidationResult(true, null)
    }
}

object WinnerCalculator {
    fun calculateWinner(game: Game): String? {
        if (game.rounds.isEmpty()) return null
        return game.players.minByOrNull { player ->
            game.getPlayerTotal(player.name)
        }?.name
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)
