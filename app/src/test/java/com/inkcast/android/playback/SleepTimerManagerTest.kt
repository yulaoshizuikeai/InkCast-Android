package com.inkcast.android.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SleepTimerManager.cancelTimer()
    }

    @After
    fun tearDown() {
        SleepTimerManager.cancelTimer()
        Dispatchers.resetMain()
    }

    @Test
    fun testSleepTimerOptions() {
        assertEquals(0, SleepTimerOption.OFF.minutes)
        assertEquals(15, SleepTimerOption.MINUTES_15.minutes)
        assertEquals(30, SleepTimerOption.MINUTES_30.minutes)
        assertEquals(45, SleepTimerOption.MINUTES_45.minutes)
        assertEquals(60, SleepTimerOption.MINUTES_60.minutes)
        assertEquals(-1, SleepTimerOption.END_OF_EPISODE.minutes)
    }

    @Test
    fun testSetTimerEndOfEpisode() {
        var paused = false
        SleepTimerManager.registerPauseAction {
            paused = true
        }

        SleepTimerManager.setTimer(SleepTimerOption.END_OF_EPISODE)
        val state = SleepTimerManager.timerState.value
        assertTrue(state.isActive)
        assertEquals(SleepTimerOption.END_OF_EPISODE, state.selectedOption)
        assertEquals("播完本集", state.formattedTime)

        // Trigger episode ended
        SleepTimerManager.onEpisodeEnded()
        assertTrue(paused)
        assertFalse(SleepTimerManager.timerState.value.isActive)
    }

    @Test
    fun testSetTimerTimedOption() {
        SleepTimerManager.setTimer(SleepTimerOption.MINUTES_15)
        val state = SleepTimerManager.timerState.value
        assertTrue(state.isActive)
        assertEquals(SleepTimerOption.MINUTES_15, state.selectedOption)
        assertEquals(900L, state.remainingSeconds)
        assertEquals("15:00", state.formattedTime)

        SleepTimerManager.cancelTimer()
        assertFalse(SleepTimerManager.timerState.value.isActive)
        assertEquals(SleepTimerOption.OFF, SleepTimerManager.timerState.value.selectedOption)
    }

    @Test
    fun testTimedOptionCountdownAndExpiration() {
        var paused = false
        val pauseAction = { paused = true }
        SleepTimerManager.registerPauseAction(pauseAction)

        SleepTimerManager.setTimer(SleepTimerOption.MINUTES_15)
        assertTrue(SleepTimerManager.timerState.value.isActive)
        assertEquals(900L, SleepTimerManager.timerState.value.remainingSeconds)

        // Advance 1 second
        testDispatcher.scheduler.advanceTimeBy(1000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(899L, SleepTimerManager.timerState.value.remainingSeconds)
        assertEquals("14:59", SleepTimerManager.timerState.value.formattedTime)
        assertFalse(paused)

        // Advance remaining 899 seconds
        testDispatcher.scheduler.advanceTimeBy(899_000L)
        testDispatcher.scheduler.runCurrent()

        assertTrue("Pause action should be triggered upon expiration", paused)
        assertFalse(SleepTimerManager.timerState.value.isActive)
        assertEquals(SleepTimerOption.OFF, SleepTimerManager.timerState.value.selectedOption)

        SleepTimerManager.unregisterPauseAction(pauseAction)
    }

    @Test
    fun testMultiplePauseActionsAndUnregister() {
        var action1Called = false
        var action2Called = false
        val action1 = { action1Called = true }
        val action2 = { action2Called = true }

        SleepTimerManager.registerPauseAction(action1)
        SleepTimerManager.registerPauseAction(action2)

        // Unregister action 1
        SleepTimerManager.unregisterPauseAction(action1)

        SleepTimerManager.setTimer(SleepTimerOption.END_OF_EPISODE)
        SleepTimerManager.onEpisodeEnded()

        assertFalse("Unregistered action should not be called", action1Called)
        assertTrue("Registered action should be called", action2Called)

        // Clean up
        SleepTimerManager.unregisterPauseAction(action2)
    }

    @Test
    fun testPauseActionExceptionIsolation() {
        var actionSuccessCalled = false
        val throwingAction = { throw RuntimeException("Simulated action failure") }
        val successAction = { actionSuccessCalled = true }

        SleepTimerManager.registerPauseAction(throwingAction)
        SleepTimerManager.registerPauseAction(successAction)

        SleepTimerManager.setTimer(SleepTimerOption.END_OF_EPISODE)
        SleepTimerManager.onEpisodeEnded()

        assertTrue("Subsequent action should still execute even if prior action threw", actionSuccessCalled)

        // Clean up
        SleepTimerManager.unregisterPauseAction(throwingAction)
        SleepTimerManager.unregisterPauseAction(successAction)
    }
}
