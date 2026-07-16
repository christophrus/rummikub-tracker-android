package org.lorus.rummiq.domain.usecase

import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.domain.model.Player
import org.lorus.rummiq.domain.model.Round
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameManagerTest {

    private val repo = mockk<GameRepository>(relaxed = true)
    private val manager = GameManager(repo)

    private fun players(vararg names: String) = names.map { Player(name = it) }

    private fun game(
        players: List<Player>,
        currentRound: Int = 0,
        beginner: Int = 0,
        maxExt: Int = 3,
        replenish: Int = 0
    ) = Game(
        id = 1,
        name = "g",
        players = players,
        currentRound = currentRound,
        roundBeginnerIndex = beginner,
        currentPlayerIndex = beginner,
        maxExtensions = maxExt,
        extensionReplenishRounds = replenish
    )

    private fun round() = Round(gameId = 1, roundNumber = 0, scores = emptyMap())

    @Before
    fun passThroughTransaction() {
        // Execute the transactional block directly — the DB transaction itself is Room's concern.
        coEvery { repo.runInTransaction(any<suspend () -> Unit>()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
    }

    @Test
    fun `saveRound advances round and rotates beginner by one`() = runTest {
        manager.saveRound(game(players("A", "B", "C")), round())

        val updated = slot<Game>()
        coVerify { repo.updateGame(capture(updated)) }
        assertEquals(1, updated.captured.currentRound)
        assertEquals(1, updated.captured.roundBeginnerIndex)
        assertEquals(1, updated.captured.currentPlayerIndex)
        assertEquals(3, updated.captured.maxExtensions)
        coVerify(exactly = 0) { repo.incrementAllMaxExtensions(any()) }
    }

    @Test
    fun `beginner rotation wraps around to the first player`() = runTest {
        manager.saveRound(game(players("A", "B", "C"), beginner = 2), round())

        val updated = slot<Game>()
        coVerify { repo.updateGame(capture(updated)) }
        assertEquals(0, updated.captured.roundBeginnerIndex)
        assertEquals(0, updated.captured.currentPlayerIndex)
    }

    @Test
    fun `extension replenishment triggers on the configured cadence`() = runTest {
        // replenish every 2 rounds; finishing round 2 (currentRound 1 -> nextRound 2) triggers it
        manager.saveRound(game(players("A", "B"), currentRound = 1, replenish = 2), round())

        coVerify(exactly = 1) { repo.incrementAllMaxExtensions(1) }
        val updated = slot<Game>()
        coVerify { repo.updateGame(capture(updated)) }
        assertEquals(4, updated.captured.maxExtensions)
    }

    @Test
    fun `no replenishment between cadence rounds`() = runTest {
        // replenish every 3 rounds; nextRound = 2 -> no trigger
        manager.saveRound(game(players("A", "B"), currentRound = 1, replenish = 3), round())

        coVerify(exactly = 0) { repo.incrementAllMaxExtensions(any()) }
        val updated = slot<Game>()
        coVerify { repo.updateGame(capture(updated)) }
        assertEquals(3, updated.captured.maxExtensions)
    }

    @Test
    fun `saveRound with empty player list does not crash`() = runTest {
        manager.saveRound(game(emptyList()), round())

        val updated = slot<Game>()
        coVerify { repo.updateGame(capture(updated)) }
        assertEquals(0, updated.captured.roundBeginnerIndex)
        assertEquals(1, updated.captured.currentRound)
    }
}
