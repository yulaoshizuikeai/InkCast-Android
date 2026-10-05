package com.inkcast.android.playback

import androidx.compose.runtime.Immutable
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SleepTimerOption(val minutes: Int, val label: String) {
    OFF(0, "关闭定时"),
    MINUTES_15(15, "15 分钟"),
    MINUTES_30(30, "30 分钟"),
    MINUTES_45(45, "45 分钟"),
    MINUTES_60(60, "60 分钟"),
    END_OF_EPISODE(-1, "播完当前单集")
}

@Immutable
data class SleepTimerState(
    val isActive: Boolean = false,
    val selectedOption: SleepTimerOption = SleepTimerOption.OFF,
    val remainingSeconds: Long = 0L,
    val formattedTime: String = ""
)

/**
 * Centralized Sleep Timer Manager.
 * Controls auto-shutdown/auto-pause of audio playback after a preset duration or at the end of the current episode.
 * Survives UI recreation and works seamlessly during background playback.
 */
object SleepTimerManager {

    private const val TAG = "SleepTimerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var timerJob: Job? = null

    private val pauseActions = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    /**
     * Register the callback to invoke when timer expires (e.g. player.pause()).
     */
    fun registerPauseAction(action: () -> Unit) {
        pauseActions.add(action)
    }

    /**
     * Unregister the callback to prevent leaks or unwanted execution.
     */
    fun unregisterPauseAction(action: () -> Unit) {
        pauseActions.remove(action)
    }

    /**
     * Set sleep timer option.
     */
    fun setTimer(option: SleepTimerOption) {
        timerJob?.cancel()
        timerJob = null

        when (option) {
            SleepTimerOption.OFF -> {
                Log.d(TAG, "Sleep timer turned off")
                _timerState.value = SleepTimerState()
            }
            SleepTimerOption.END_OF_EPISODE -> {
                Log.d(TAG, "Sleep timer set to: End of current episode")
                _timerState.value = SleepTimerState(
                    isActive = true,
                    selectedOption = option,
                    remainingSeconds = 0L,
                    formattedTime = "播完本集"
                )
            }
            else -> {
                val totalSeconds = option.minutes * 60L
                Log.d(TAG, "Sleep timer set to: ${option.minutes} minutes ($totalSeconds seconds)")
                _timerState.value = SleepTimerState(
                    isActive = true,
                    selectedOption = option,
                    remainingSeconds = totalSeconds,
                    formattedTime = formatRemaining(totalSeconds)
                )

                timerJob = scope.launch {
                    var remaining = totalSeconds
                    while (isActive && remaining > 0) {
                        delay(1000L)
                        remaining--
                        _timerState.value = _timerState.value.copy(
                            remainingSeconds = remaining,
                            formattedTime = formatRemaining(remaining)
                        )
                    }

                    if (isActive && remaining <= 0) {
                        Log.i(TAG, "Sleep timer expired! Triggering pause action.")
                        pauseActions.forEach { action ->
                            try {
                                action.invoke()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error executing pause action", e)
                            }
                        }
                        _timerState.value = SleepTimerState()
                    }
                }
            }
        }
    }

    /**
     * Called by playback service or controller when an episode reaches the end.
     */
    fun onEpisodeEnded() {
        if (_timerState.value.selectedOption == SleepTimerOption.END_OF_EPISODE) {
            Log.i(TAG, "Episode ended: triggering sleep timer stop.")
            pauseActions.forEach { action ->
                try {
                    action.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing pause action", e)
                }
            }
            cancelTimer()
        }
    }

    /**
     * Cancel active timer.
     */
    fun cancelTimer() {
        setTimer(SleepTimerOption.OFF)
    }

    private fun formatRemaining(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
