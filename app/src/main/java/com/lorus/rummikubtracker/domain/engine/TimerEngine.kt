package com.lorus.rummikubtracker.domain.engine

import com.lorus.rummikubtracker.domain.model.Config
import com.lorus.rummikubtracker.domain.model.TimerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerEngine @Inject constructor() {

    // Visible for testing — allows injection of a test dispatcher
    internal var countdownDispatcher: CoroutineDispatcher = Dispatchers.Main

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState.STOPPED)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _extensionsUsed = MutableStateFlow(0)
    val extensionsUsed: StateFlow<Int> = _extensionsUsed.asStateFlow()

    /** Tracks the peak remaining time (original + extensions), used by the clock for the full-circle reference. */
    private val _effectiveTotalMs = MutableStateFlow(0L)
    val effectiveTotalMs: StateFlow<Long> = _effectiveTotalMs.asStateFlow()

    private var maxExtensions: Int = 3
    private var timerJob: Job? = null
    private var totalDuration: Long = 0L

    var onTick: ((Long) -> Unit)? = null
    var onTimeUp: (() -> Unit)? = null
    var onTickSound: (() -> Unit)? = null

    fun configure(durationMs: Long, maxExt: Int = 3, currentExtensionsUsed: Int = 0) {
        totalDuration = durationMs
        maxExtensions = maxExt
        _remainingMs.value = durationMs
        _effectiveTotalMs.value = durationMs
        _extensionsUsed.value = currentExtensionsUsed
        _timerState.value = TimerState.STOPPED
    }

    fun start() {
        if (_timerState.value == TimerState.RUNNING) return
        if (_remainingMs.value <= 0) {
            _remainingMs.value = totalDuration
        }
        _timerState.value = TimerState.RUNNING
        startCountdown()
    }

    fun pause() {
        _timerState.value = TimerState.PAUSED
        timerJob?.cancel()
    }

    fun resume() {
        if (_timerState.value != TimerState.PAUSED) return
        start()
    }

    fun toggle() {
        when (_timerState.value) {
            TimerState.RUNNING -> pause()
            TimerState.PAUSED -> resume()
            TimerState.STOPPED -> start()
        }
    }

    fun reset() {
        timerJob?.cancel()
        _remainingMs.value = totalDuration
        _effectiveTotalMs.value = totalDuration
        _timerState.value = TimerState.STOPPED
    }

    fun stop() {
        timerJob?.cancel()
        _timerState.value = TimerState.STOPPED
    }

    fun extend(): Boolean {
        if (_extensionsUsed.value >= maxExtensions) return false
        if (_timerState.value != TimerState.RUNNING && _timerState.value != TimerState.PAUSED) return false

        _extensionsUsed.value += 1
        _remainingMs.value += Config.EXTENSION_DURATION_MS
        // After extension, the clock should be fully filled
        _effectiveTotalMs.value = _remainingMs.value
        return true
    }

    fun resetExtensions() {
        _extensionsUsed.value = 0
    }

    fun setExtensionsUsed(count: Int) {
        _extensionsUsed.value = count.coerceIn(0, maxExtensions)
    }

    fun setMaxExtensions(max: Int) {
        maxExtensions = max.coerceIn(0, Config.MAX_EXTENSIONS_LIMIT)
        // Re-clamp extensionsUsed to new limit
        _extensionsUsed.value = _extensionsUsed.value.coerceIn(0, maxExtensions)
    }

    fun getRemainingExtensions(): Int = maxExtensions - _extensionsUsed.value

    fun isRunning(): Boolean = _timerState.value == TimerState.RUNNING
    fun isPaused(): Boolean = _timerState.value == TimerState.PAUSED

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = CoroutineScope(countdownDispatcher).launch {
            while (_remainingMs.value > 0 && _timerState.value == TimerState.RUNNING) {
                delay(1000)
                if (_timerState.value != TimerState.RUNNING) break
                _remainingMs.value -= 1000
                onTick?.invoke(_remainingMs.value)

                if (_remainingMs.value <= Config.TICK_START_SECONDS * 1000 && _remainingMs.value > 0) {
                    onTickSound?.invoke()
                }

                if (_remainingMs.value <= 0) {
                    _timerState.value = TimerState.STOPPED
                    onTimeUp?.invoke()
                    break
                }
            }
        }
    }

    fun destroy() {
        timerJob?.cancel()
    }
}
