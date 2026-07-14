package org.lorus.rummiq.domain.usecase

import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.domain.model.Round
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
        // Persist the round and advance the game counters atomically so a failure can't
        // leave the round saved but the game state (round/beginner/maxExtensions) unadvanced.
        gameRepository.runInTransaction {
            gameRepository.saveRound(round)
            val nextRound = game.currentRound + 1
            // Rotate beginner by exactly 1 from the previous round's beginner
            val nextBeginnerIndex = if (game.players.isEmpty()) 0
                else (game.roundBeginnerIndex + 1) % game.players.size

            // Handle extension replenishment — increase maxExtensions for all players
            val newMaxExtensions = if (game.extensionReplenishRounds > 0 && nextRound % game.extensionReplenishRounds == 0) {
                gameRepository.incrementAllMaxExtensions(game.id)
                game.maxExtensions + 1
            } else {
                game.maxExtensions
            }

            gameRepository.updateGame(
                game.copy(
                    currentRound = nextRound,
                    currentPlayerIndex = nextBeginnerIndex,
                    roundBeginnerIndex = nextBeginnerIndex,
                    maxExtensions = newMaxExtensions
                )
            )
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

    suspend fun incrementAllMaxExtensions(gameId: Long) {
        gameRepository.incrementAllMaxExtensions(gameId)
    }
}

object ScoreValidator {
    fun validate(scores: Map<String, Int>, playerNames: List<String>, winnerName: String): ValidationResult {
        // All players must have a score
        val missingPlayers = playerNames.filter { it !in scores }
        if (missingPlayers.isNotEmpty()) {
            return ValidationResult(false, "all_scores_required")
        }

        // Winner must have the lowest score
        val winnerScore = scores[winnerName] ?: return ValidationResult(false, "all_scores_required")
        val lowestScore = scores.values.minOrNull() ?: return ValidationResult(false, "all_scores_required")
        if (winnerScore != lowestScore) {
            return ValidationResult(false, "winner_lowest_score_required")
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
