package org.lorus.rummiq.domain.engine

import android.os.SystemClock
import org.lorus.rummiq.domain.model.Config
import org.lorus.rummiq.domain.model.TimerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot timer events consumed by the UI. Emitted via a SharedFlow so listeners are lifecycle-scoped. */
enum class TimerEvent { TICK_SOUND, TIME_UP }

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

    /** Monotonic wall-clock deadline; the countdown derives remaining time from this instead of counting ticks. */
    @Volatile private var targetEndTime: Long = 0L

    /** Time source in milliseconds; injectable for testing. Defaults to the monotonic elapsed-realtime clock. */
    internal var timeSource: () -> Long = { SystemClock.elapsedRealtime() }

    /** Single managed scope for the countdown; created lazily so tests can inject the dispatcher first. */
    private var engineScope: CoroutineScope? = null
    private fun scope(): CoroutineScope =
        engineScope ?: CoroutineScope(SupervisorJob() + countdownDispatcher).also { engineScope = it }

    // Buffered so emissions from the countdown coroutine never suspend; replay=0 so a fresh
    // subscriber (e.g. after config change) doesn't replay a stale TIME_UP.
    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

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
            _effectiveTotalMs.value = totalDuration
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
        // Push the wall-clock deadline too so the running countdown keeps the added time.
        targetEndTime += Config.EXTENSION_DURATION_MS
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
        timerJob = scope().launch {
            // Anchor the deadline to the monotonic clock so drift and background pauses self-correct.
            targetEndTime = timeSource() + _remainingMs.value
            var lastShownSecond = -1L
            while (isActive && _timerState.value == TimerState.RUNNING) {
                val remainingRaw = (targetEndTime - timeSource()).coerceAtLeast(0L)
                // Round up to whole seconds so the mm:ss display never skips a second.
                val remainingSecond = (remainingRaw + 999L) / 1000L

                if (remainingSecond != lastShownSecond) {
                    lastShownSecond = remainingSecond
                    _remainingMs.value = remainingSecond * 1000L

                    if (remainingRaw > 0L && _remainingMs.value <= Config.TICK_START_SECONDS * 1000L) {
                        _events.tryEmit(TimerEvent.TICK_SOUND)
                    }
                }

                if (remainingRaw <= 0L) {
                    _remainingMs.value = 0L
                    _timerState.value = TimerState.STOPPED
                    _events.tryEmit(TimerEvent.TIME_UP)
                    break
                }

                // Sample well below one second so the display updates promptly on each second boundary.
                delay(200)
            }
        }
    }

    fun destroy() {
        timerJob?.cancel()
        engineScope?.cancel()
        engineScope = null
    }
}
