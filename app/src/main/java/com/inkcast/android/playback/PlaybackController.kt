package com.inkcast.android.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller bridge connecting Compose UI to the background MediaSessionService.
 * Enforces discrete stepped updates for E-ink screen friendliness.
 */
class PlaybackController(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackController"
        val SPEED_STEPS = listOf(1.0f, 1.2f, 1.5f, 2.0f)
    }

    private val prefsManager = PreferencesManager(context)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Stepped position in ms (only updates at 10-second intervals or upon seek/pause to minimize E-ink redraws)
    private val _steppedPositionMs = MutableStateFlow(0L)
    val steppedPositionMs: StateFlow<Long> = _steppedPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lowFrequencyPoller = object : Runnable {
        override fun run() {
            updateSteppedPosition(isManualSeekOrPause = false)
            if (_isPlaying.value) {
                // Poll every 10 seconds for E-ink stepped refresh
                mainHandler.postDelayed(this, 10000L)
            }
        }
    }

    init {
        _playbackSpeed.value = prefsManager.getSettings().playbackSpeed
        _currentEpisode.value = prefsManager.getCurrentEpisode()
        _currentEpisode.value?.let { ep ->
            val savedProgress = prefsManager.getProgress(ep.id)
            if (savedProgress != null) {
                _steppedPositionMs.value = (savedProgress.positionMs / 10000L) * 10000L
                _durationMs.value = savedProgress.durationMs
            }
        }
        initMediaController()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return

        _isPlaying.value = controller.isPlaying
        _playbackSpeed.value = controller.playbackParameters.speed
        _durationMs.value = controller.duration.coerceAtLeast(0L)
        updateSteppedPosition(isManualSeekOrPause = true)

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    mainHandler.removeCallbacks(lowFrequencyPoller)
                    mainHandler.post(lowFrequencyPoller)
                } else {
                    mainHandler.removeCallbacks(lowFrequencyPoller)
                    updateSteppedPosition(isManualSeekOrPause = true)
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                val currentSettings = prefsManager.getSettings()
                prefsManager.saveSettings(currentSettings.copy(playbackSpeed = playbackParameters.speed))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _durationMs.value = (controller.duration).coerceAtLeast(0L)
                updateSteppedPosition(isManualSeekOrPause = true)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateSteppedPosition(isManualSeekOrPause = true)
            }
        })
    }

    private fun updateSteppedPosition(isManualSeekOrPause: Boolean) {
        val controller = mediaController ?: return
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val dur = controller.duration.coerceAtLeast(0L)
        if (dur > 0) {
            _durationMs.value = dur
        }

        if (isManualSeekOrPause) {
            _steppedPositionMs.value = pos
        } else {
            // Round down to 10-second discrete steps to eliminate unnecessary E-ink screen refreshes
            _steppedPositionMs.value = (pos / 10000L) * 10000L
        }
    }

    fun playEpisode(episode: Episode) {
        val controller = mediaController ?: return
        _currentEpisode.value = episode
        prefsManager.saveCurrentEpisode(episode)

        // Read previous progress breakpoint
        val progress = prefsManager.getProgress(episode.id)
        val startPositionMs = progress?.positionMs ?: 0L

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.pubDate)
            .setDescription(episode.description)
            .build()

        val item = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.audioUrl)
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.setMediaItem(item, startPositionMs)
        controller.prepare()
        controller.play()

        updateSteppedPosition(isManualSeekOrPause = true)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            updateSteppedPosition(isManualSeekOrPause = true)
        } else {
            if (controller.mediaItemCount == 0) {
                // If controller has no media item, try playing currentEpisode
                _currentEpisode.value?.let { playEpisode(it) }
            } else {
                controller.play()
            }
        }
    }

    fun seekBack15() {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition - 15000L).coerceAtLeast(0L)
        controller.seekTo(newPos)
        updateSteppedPosition(isManualSeekOrPause = true)
    }

    fun seekForward30() {
        val controller = mediaController ?: return
        val dur = controller.duration
        val target = controller.currentPosition + 30000L
        val newPos = if (dur > 0) target.coerceAtMost(dur) else target
        controller.seekTo(newPos)
        updateSteppedPosition(isManualSeekOrPause = true)
    }

    fun cyclePlaybackSpeed() {
        val controller = mediaController ?: return
        val current = _playbackSpeed.value
        val currentIndex = SPEED_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextIndex = if (currentIndex in 0 until SPEED_STEPS.size - 1) currentIndex + 1 else 0
        val nextSpeed = SPEED_STEPS[nextIndex]

        controller.playbackParameters = PlaybackParameters(nextSpeed)
        _playbackSpeed.value = nextSpeed
    }

    fun release() {
        mainHandler.removeCallbacks(lowFrequencyPoller)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
