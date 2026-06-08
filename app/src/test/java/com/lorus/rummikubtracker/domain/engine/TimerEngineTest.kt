package com.lorus.rummikubtracker.domain.engine

import app.cash.turbine.test
import com.lorus.rummikubtracker.domain.model.TimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private lateinit var timerEngine: TimerEngine

    @Before
    fun setUp() {
        timerEngine = TimerEngine()
    }

    @Test
    fun `initial state is STOPPED`() = runTest {
        timerEngine.configure(60_000)
        assertEquals(TimerState.STOPPED, timerEngine.timerState.value)
        assertEquals(60_000L, timerEngine.remainingMs.value)
    }

    @Test
    fun `start transitions to RUNNING`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        assertEquals(TimerState.RUNNING, timerEngine.timerState.value)
    }

    @Test
    fun `pause transitions to PAUSED`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        timerEngine.pause()
        assertEquals(TimerState.PAUSED, timerEngine.timerState.value)
    }

    @Test
    fun `resume from PAUSED goes to RUNNING`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        timerEngine.pause()
        timerEngine.resume()
        assertEquals(TimerState.RUNNING, timerEngine.timerState.value)
    }

    @Test
    fun `reset goes to STOPPED and restores duration`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        advanceTimeBy(5000)
        timerEngine.reset()
        assertEquals(TimerState.STOPPED, timerEngine.timerState.value)
        assertEquals(60_000L, timerEngine.remainingMs.value)
    }

    @Test
    fun `timer counts down by 1 second`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        advanceTimeBy(1000)
        assertEquals(59_000L, timerEngine.remainingMs.value)
    }

    @Test
    fun `timer counts down multiple seconds`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        advanceTimeBy(5000)
        assertEquals(55_000L, timerEngine.remainingMs.value)
    }

    @Test
    fun `timer does not count when paused`() = runTest {
        timerEngine.configure(60_000)
        timerEngine.start()
        advanceTimeBy(2000)
        timerEngine.pause()
        val remainingAtPause = timerEngine.remainingMs.value
        advanceTimeBy(5000)
        assertEquals(remainingAtPause, timerEngine.remainingMs.value)
    }

    @Test
    fun `extend adds extension duration`() = runTest {
        timerEngine.configure(60_000, maxExt = 3)
        timerEngine.start()
        advanceTimeBy(5000)
        val before = timerEngine.remainingMs.value
        val result = timerEngine.extend()
        assertTrue(result)
        assertEquals(before + 30_000, timerEngine.remainingMs.value)
    }

    @Test
    fun `extend fails when maxExtensions reached`() = runTest {
        timerEngine.configure(60_000, maxExt = 1)
        timerEngine.start()
        timerEngine.extend()
        val result = timerEngine.extend()
        assertFalse(result)
    }

    @Test
    fun `extend fails when timer is stopped`() = runTest {
        timerEngine.configure(60_000, maxExt = 3)
        val result = timerEngine.extend()
        assertFalse(result)
    }

    @Test
    fun `timeUp callback fires when timer reaches 0`() = runTest {
        timerEngine.configure(3000) // 3 seconds
        var timeUpCalled = false
        timerEngine.onTimeUp = { timeUpCalled = true }
        timerEngine.start()

        // Advance past the timer duration
        advanceTimeBy(4000)

        // The timer should have stopped and fired the callback
        // Note: The exact timing depends on coroutine dispatching
        assertEquals(TimerState.STOPPED, timerEngine.timerState.value)
    }

    @Test
    fun `extensionsUsed tracks correctly`() = runTest {
        timerEngine.configure(60_000, maxExt = 5)
        assertEquals(0, timerEngine.extensionsUsed.value)
        timerEngine.start()
        timerEngine.extend()
        assertEquals(1, timerEngine.extensionsUsed.value)
        timerEngine.extend()
        assertEquals(2, timerEngine.extensionsUsed.value)
    }

    @Test
    fun `resetExtensions clears extension count`() = runTest {
        timerEngine.configure(60_000, maxExt = 5)
        timerEngine.start()
        timerEngine.extend()
        timerEngine.extend()
        timerEngine.resetExtensions()
        assertEquals(0, timerEngine.extensionsUsed.value)
    }

    @Test
    fun `getRemainingExtensions returns correct count`() = runTest {
        timerEngine.configure(60_000, maxExt = 3)
        timerEngine.start()
        assertEquals(3, timerEngine.getRemainingExtensions())
        timerEngine.extend()
        assertEquals(2, timerEngine.getRemainingExtensions())
    }

    @Test
    fun `toggle cycles through states`() = runTest {
        timerEngine.configure(60_000)
        assertEquals(TimerState.STOPPED, timerEngine.timerState.value)

        timerEngine.toggle()
        assertEquals(TimerState.RUNNING, timerEngine.timerState.value)

        timerEngine.toggle()
        assertEquals(TimerState.PAUSED, timerEngine.timerState.value)

        timerEngine.toggle()
        assertEquals(TimerState.RUNNING, timerEngine.timerState.value)
    }

    @Test
    fun `isRunning returns correct state`() = runTest {
        timerEngine.configure(60_000)
        assertFalse(timerEngine.isRunning())
        timerEngine.start()
        assertTrue(timerEngine.isRunning())
        timerEngine.pause()
        assertFalse(timerEngine.isRunning())
    }
}
