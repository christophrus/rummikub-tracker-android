package org.lorus.rummiq.domain.engine

import app.cash.turbine.test
import org.lorus.rummiq.domain.model.TimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the actual countdown behaviour (wall-clock based) with virtual time.
 * The engine's dispatcher shares the test scheduler and its time source reads
 * the scheduler's virtual clock, so delays and "wall time" advance together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineCountdownTest {

    private fun TestScope.createEngine(): TimerEngine {
        return TimerEngine().also {
            it.countdownDispatcher = StandardTestDispatcher(testScheduler)
            it.timeSource = { testScheduler.currentTime }
        }
    }

    @Test
    fun `countdown decrements remaining time from the clock`() = runTest {
        val e = createEngine()
        e.configure(60_000)
        e.start()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(50_000L, e.remainingMs.value)
        assertEquals(TimerState.RUNNING, e.timerState.value)
        e.destroy() // stop the countdown so runTest can reach quiescence
    }

    @Test
    fun `countdown reaches zero, stops and emits TIME_UP`() = runTest {
        val e = createEngine()
        e.configure(2_000)
        e.events.test {
            e.start()
            advanceTimeBy(2_500)
            runCurrent()
            // 2s duration is inside the tick-sound window (TICK_START_SECONDS = 10)
            assertEquals(TimerEvent.TICK_SOUND, awaitItem()) // at 2s remaining
            assertEquals(TimerEvent.TICK_SOUND, awaitItem()) // at 1s remaining
            assertEquals(TimerEvent.TIME_UP, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(TimerState.STOPPED, e.timerState.value)
        assertEquals(0L, e.remainingMs.value)
        e.destroy()
    }

    @Test
    fun `extend pushes the wall-clock deadline of a running countdown`() = runTest {
        val e = createEngine()
        e.configure(5_000)
        e.start()
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(3_000L, e.remainingMs.value)

        e.extend() // +30s (Config.EXTENSION_DURATION_MS)
        assertEquals(33_000L, e.remainingMs.value)

        advanceTimeBy(1_000)
        runCurrent()
        // Still running, and the countdown respects the pushed deadline.
        assertEquals(TimerState.RUNNING, e.timerState.value)
        assertEquals(32_000L, e.remainingMs.value)
        e.destroy()
    }

    @Test
    fun `pause freezes remaining time and resume continues from it`() = runTest {
        val e = createEngine()
        e.configure(60_000)
        e.start()
        advanceTimeBy(5_000)
        runCurrent()
        e.pause()
        val frozen = e.remainingMs.value
        advanceTimeBy(20_000) // time passes while paused
        runCurrent()
        assertEquals(frozen, e.remainingMs.value)

        e.resume()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(frozen - 5_000, e.remainingMs.value)
        e.destroy()
    }

    @Test
    fun `remaining time self-corrects after a clock jump (background catch-up)`() = runTest {
        var clock = 0L
        val e = TimerEngine().also {
            it.countdownDispatcher = StandardTestDispatcher(testScheduler)
            it.timeSource = { clock }
        }
        e.configure(60_000)
        e.start()
        runCurrent()
        assertEquals(60_000L, e.remainingMs.value)

        // Wall clock jumps forward (device slept / app backgrounded) while ticks stalled.
        clock = 45_000
        advanceTimeBy(400)
        runCurrent()
        assertEquals(15_000L, e.remainingMs.value)
        assertEquals(TimerState.RUNNING, e.timerState.value)
        // Critical: the manual clock never advances further, so without stopping the
        // engine the countdown loop would never terminate and runTest would hang.
        e.destroy()
    }

    @Test
    fun `restart after natural expiry restores the full duration and effective total`() = runTest {
        val e = createEngine()
        e.configure(2_000)
        e.start()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(TimerState.STOPPED, e.timerState.value)

        e.start()
        runCurrent()
        assertEquals(2_000L, e.remainingMs.value)
        assertEquals(2_000L, e.effectiveTotalMs.value)
        assertEquals(TimerState.RUNNING, e.timerState.value)
        e.destroy()
    }
}
