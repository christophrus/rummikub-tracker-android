package org.lorus.rummiq.domain.engine

import org.lorus.rummiq.domain.model.TimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createEngine(): TimerEngine {
        return TimerEngine().also { it.countdownDispatcher = testDispatcher }
    }

    @Test
    fun `initial state is STOPPED`() {
        val e = createEngine()
        e.configure(60_000)
        assertEquals(TimerState.STOPPED, e.timerState.value)
        assertEquals(60_000L, e.remainingMs.value)
        assertEquals(60_000L, e.effectiveTotalMs.value)
    }

    @Test
    fun `start transitions to RUNNING`() = runTest {
        val e = createEngine()
        e.configure(60_000)
        e.start()
        advanceUntilIdle()
        assertEquals(TimerState.RUNNING, e.timerState.value)
    }

    @Test
    fun `pause transitions to PAUSED`() = runTest {
        val e = createEngine()
        e.configure(60_000)
        e.start()
        advanceUntilIdle()
        e.pause()
        assertEquals(TimerState.PAUSED, e.timerState.value)
    }

    @Test
    fun `reset goes to STOPPED and restores duration`() {
        val e = createEngine()
        e.configure(60_000)
        e.start()
        e.reset()
        assertEquals(TimerState.STOPPED, e.timerState.value)
        assertEquals(60_000L, e.remainingMs.value)
        assertEquals(60_000L, e.effectiveTotalMs.value)
    }

    @Test
    fun `extend adds extension duration`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 3)
        e.start()
        advanceUntilIdle()
        val before = e.remainingMs.value
        assertTrue(e.extend())
        assertEquals(before + 30_000, e.remainingMs.value)
    }

    @Test
    fun `extend updates effectiveTotal to remaining`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 3)
        e.start()
        advanceUntilIdle()
        e.extend()
        assertEquals(e.remainingMs.value, e.effectiveTotalMs.value)
    }

    @Test
    fun `extend fails when maxExtensions reached`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 1)
        e.start()
        advanceUntilIdle()
        e.extend()
        assertFalse(e.extend())
    }

    @Test
    fun `extend fails when timer is stopped`() {
        val e = createEngine()
        e.configure(60_000, maxExt = 3)
        assertFalse(e.extend())
    }

    @Test
    fun `extensionsUsed tracks correctly`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 5)
        assertEquals(0, e.extensionsUsed.value)
        e.start()
        advanceUntilIdle()
        e.extend()
        assertEquals(1, e.extensionsUsed.value)
        e.extend()
        assertEquals(2, e.extensionsUsed.value)
    }

    @Test
    fun `resetExtensions clears extension count`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 5)
        e.start()
        advanceUntilIdle()
        e.extend()
        e.extend()
        e.resetExtensions()
        assertEquals(0, e.extensionsUsed.value)
    }

    @Test
    fun `getRemainingExtensions returns correct count`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 3)
        e.start()
        advanceUntilIdle()
        assertEquals(3, e.getRemainingExtensions())
        e.extend()
        assertEquals(2, e.getRemainingExtensions())
    }

    @Test
    fun `toggle cycles through states`() = runTest {
        val e = createEngine()
        e.configure(60_000)
        assertEquals(TimerState.STOPPED, e.timerState.value)
        e.toggle()
        advanceUntilIdle()
        assertEquals(TimerState.RUNNING, e.timerState.value)
        e.toggle()
        assertEquals(TimerState.PAUSED, e.timerState.value)
    }

    @Test
    fun `isRunning returns correct state`() = runTest {
        val e = createEngine()
        e.configure(60_000)
        assertFalse(e.isRunning())
        e.start()
        advanceUntilIdle()
        assertTrue(e.isRunning())
        e.pause()
        assertFalse(e.isRunning())
    }

    @Test
    fun `effectiveTotal starts equal to duration`() {
        val e = createEngine()
        e.configure(45_000)
        assertEquals(45_000L, e.effectiveTotalMs.value)
    }

    @Test
    fun `effectiveTotal resets with timer`() = runTest {
        val e = createEngine()
        e.configure(60_000, maxExt = 3)
        e.start()
        advanceUntilIdle()
        e.extend()
        assertTrue(e.effectiveTotalMs.value > 60_000L)
        e.reset()
        assertEquals(60_000L, e.effectiveTotalMs.value)
    }
}
