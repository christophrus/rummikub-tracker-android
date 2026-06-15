package com.lorus.rummikubtracker.domain.model

data class Player(
    val name: String,
    val imagePath: String? = null,
    val order: Int = 0,
    val maxExtensions: Int = 3,
    val extensionsUsed: Int = 0
)

data class Round(
    val id: Long = 0,
    val gameId: Long,
    val roundNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val scores: Map<String, Int>,
    val winnerPlayerName: String? = null
)

data class Game(
    val id: Long = 0,
    val name: String,
    val status: String = "active",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val winner: String? = null,
    val timerDuration: Int = 60000,
    val originalTimerDuration: Int = 60000,
    val maxExtensions: Int = 3,
    val extensionReplenishRounds: Int = 0,
    val ttsLanguage: String = "en",
    val currentPlayerIndex: Int = 0,
    val roundBeginnerIndex: Int = 0,
    val currentRound: Int = 0,
    val players: List<Player> = emptyList(),
    val rounds: List<Round> = emptyList()
) {
    val isActive: Boolean get() = status == "active"

    fun getPlayerScores(playerName: String): List<Int> =
        rounds.mapNotNull { round -> round.scores[playerName] }

    fun getPlayerTotal(playerName: String): Int =
        getPlayerScores(playerName).sum()

    fun getCumulativeTotals(): Map<String, List<Int>> {
        val result = mutableMapOf<String, List<Int>>()
        players.forEach { player ->
            val scores = getPlayerScores(player.name)
            val cumulatives = mutableListOf<Int>()
            var sum = 0
            scores.forEach { score ->
                sum += score
                cumulatives.add(sum)
            }
            result[player.name] = cumulatives
        }
        return result
    }

    fun computeWinner(): String? {
        if (status != "completed") return null
        return players.minByOrNull { getPlayerTotal(it.name) }?.name
    }
}

enum class TimerState { RUNNING, PAUSED, STOPPED }

enum class GameStatus { SETUP, PLAYING, DECLARING_WINNER, ENDED }
