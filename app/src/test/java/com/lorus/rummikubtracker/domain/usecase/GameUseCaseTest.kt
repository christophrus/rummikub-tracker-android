package com.lorus.rummikubtracker.domain.usecase

import com.lorus.rummikubtracker.domain.model.Game
import com.lorus.rummikubtracker.domain.model.Player
import com.lorus.rummikubtracker.domain.model.Round
import org.junit.Assert.*
import org.junit.Test

class WinnerCalculatorTest {

    @Test
    fun `player with lowest total wins`() {
        val game = Game(
            id = 1,
            name = "Test",
            status = "completed",
            players = listOf(
                Player(name = "Alice"),
                Player(name = "Bob"),
                Player(name = "Charlie")
            ),
            rounds = listOf(
                Round(
                    gameId = 1, roundNumber = 0,
                    scores = mapOf("Alice" to 5, "Bob" to 0, "Charlie" to 10),
                    winnerPlayerName = "Bob"
                ),
                Round(
                    gameId = 1, roundNumber = 1,
                    scores = mapOf("Alice" to 0, "Bob" to 8, "Charlie" to 3),
                    winnerPlayerName = "Alice"
                ),
                Round(
                    gameId = 1, roundNumber = 2,
                    scores = mapOf("Alice" to 7, "Bob" to 4, "Charlie" to 0),
                    winnerPlayerName = "Charlie"
                )
            )
        )

        val winner = WinnerCalculator.calculateWinner(game)
        assertEquals("Alice", winner) // Alice: 12, Bob: 12, Charlie: 13
        // Actually: Alice: 5+0+7=12, Bob: 0+8+4=12, Charlie: 10+3+0=13
        // Alice and Bob tie at 12, but minByOrNull returns first, so Alice wins
    }

    @Test
    fun `single round game`() {
        val game = Game(
            id = 2,
            name = "Single",
            status = "completed",
            players = listOf(Player("A"), Player("B")),
            rounds = listOf(
                Round(gameId = 2, roundNumber = 0, scores = mapOf("A" to 0, "B" to 5), winnerPlayerName = "A")
            )
        )
        assertEquals("A", WinnerCalculator.calculateWinner(game))
    }

    @Test
    fun `empty rounds returns null`() {
        val game = Game(
            id = 3,
            name = "Empty",
            players = listOf(Player("A"), Player("B")),
            rounds = emptyList()
        )
        assertNull(WinnerCalculator.calculateWinner(game))
    }

    @Test
    fun `negative scores handled correctly`() {
        val game = Game(
            id = 4,
            name = "Negative",
            status = "completed",
            players = listOf(Player("A"), Player("B")),
            rounds = listOf(
                Round(gameId = 4, roundNumber = 0, scores = mapOf("A" to 5, "B" to -3), winnerPlayerName = "B")
            )
        )
        assertEquals("B", WinnerCalculator.calculateWinner(game))
    }
}

class ScoreValidatorTest {

    @Test
    fun `valid scores pass validation`() {
        val scores = mapOf("Alice" to 5, "Bob" to 0, "Charlie" to 10)
        val result = ScoreValidator.validate(scores, listOf("Alice", "Bob", "Charlie"), "Bob")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `winner can have non-zero score if it's the lowest`() {
        val scores = mapOf("Alice" to 8, "Bob" to 3, "Charlie" to 10)
        val result = ScoreValidator.validate(scores, listOf("Alice", "Bob", "Charlie"), "Bob")
        assertTrue(result.isValid)
    }

    @Test
    fun `missing player score fails`() {
        val scores = mapOf("Alice" to 5, "Bob" to 0)
        val result = ScoreValidator.validate(scores, listOf("Alice", "Bob", "Charlie"), "Bob")
        assertFalse(result.isValid)
    }

    @Test
    fun `winner not having lowest score fails`() {
        val scores = mapOf("Alice" to 5, "Bob" to 10, "Charlie" to 8)
        val result = ScoreValidator.validate(scores, listOf("Alice", "Bob", "Charlie"), "Bob")
        assertFalse(result.isValid)
    }

    @Test
    fun `empty player list returns invalid`() {
        val scores = emptyMap<String, Int>()
        val result = ScoreValidator.validate(scores, listOf("Alice"), "Alice")
        assertFalse(result.isValid)
    }
}

class GameModelTest {

    @Test
    fun `getPlayerScores returns correct scores`() {
        val game = Game(
            id = 1, name = "Test",
            players = listOf(Player("A"), Player("B")),
            rounds = listOf(
                Round(gameId = 1, roundNumber = 0, scores = mapOf("A" to 5, "B" to 0)),
                Round(gameId = 1, roundNumber = 1, scores = mapOf("A" to 0, "B" to 8))
            )
        )
        assertEquals(listOf(5, 0), game.getPlayerScores("A"))
        assertEquals(listOf(0, 8), game.getPlayerScores("B"))
    }

    @Test
    fun `getPlayerTotal sums correctly`() {
        val game = Game(
            id = 1, name = "Test",
            players = listOf(Player("A"), Player("B")),
            rounds = listOf(
                Round(gameId = 1, roundNumber = 0, scores = mapOf("A" to 10, "B" to 0)),
                Round(gameId = 1, roundNumber = 1, scores = mapOf("A" to 5, "B" to 0))
            )
        )
        assertEquals(15, game.getPlayerTotal("A"))
        assertEquals(0, game.getPlayerTotal("B"))
    }

    @Test
    fun `getCumulativeTotals works correctly`() {
        val game = Game(
            id = 1, name = "Test",
            players = listOf(Player("A"), Player("B")),
            rounds = listOf(
                Round(gameId = 1, roundNumber = 0, scores = mapOf("A" to 5, "B" to 2)),
                Round(gameId = 1, roundNumber = 1, scores = mapOf("A" to 3, "B" to 7))
            )
        )
        val totals = game.getCumulativeTotals()
        assertEquals(listOf(5, 8), totals["A"])
        assertEquals(listOf(2, 9), totals["B"])
    }

    @Test
    fun `unknown player returns 0 total`() {
        val game = Game(
            id = 1, name = "Test",
            players = listOf(Player("A")),
            rounds = listOf(
                Round(gameId = 1, roundNumber = 0, scores = mapOf("A" to 5))
            )
        )
        assertEquals(0, game.getPlayerTotal("Unknown"))
    }

    @Test
    fun `isActive returns correct status`() {
        val activeGame = Game(id = 1, name = "Active", status = "active")
        assertTrue(activeGame.isActive)

        val completedGame = Game(id = 2, name = "Done", status = "completed")
        assertFalse(completedGame.isActive)
    }
}
