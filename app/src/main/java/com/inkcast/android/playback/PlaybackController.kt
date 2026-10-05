package com.inkcast.android.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
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
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PlaybackProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller bridge connecting Compose UI to the background MediaSessionService.
 * Modern native Android 16 implementation with smooth 500ms continuous position updates,
 * high-precision slider seeking, and versatile playback rate adjustments.
 */
class PlaybackController(context: Context) {

    companion object {
        private const val TAG = "PlaybackController"
        val SPEED_STEPS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        private const val SMOOTH_POLL_INTERVAL_MS = 500L
    }

    private val appContext = context.applicationContext
    private val prefsManager = PreferencesManager.getInstance(appContext)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var controllerListener: Player.Listener? = null
    private var isReleased = false
    private var pendingPlayEpisode: Episode? = null

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Smooth real-time position in milliseconds
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    // Backward compatibility alias for any existing references
    val steppedPositionMs: StateFlow<Long> get() = positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Sleep Timer state
    val sleepTimerState: StateFlow<SleepTimerState> = SleepTimerManager.timerState

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSavedPositionMs = 0L

    private val smoothPositionPoller = object : Runnable {
        override fun run() {
            updatePosition()
            if (_isPlaying.value) {
                // Poll every 500ms for fluid progress bar / slider animation
                mainHandler.postDelayed(this, SMOOTH_POLL_INTERVAL_MS)
            }
        }
    }

    init {
        _playbackSpeed.value = prefsManager.getSettings().playbackSpeed
        _currentEpisode.value = prefsManager.getCurrentEpisode()
        _currentEpisode.value?.let { ep ->
            val savedProgress = prefsManager.getProgress(ep.id)
            if (savedProgress != null) {
                _positionMs.value = savedProgress.positionMs
                _durationMs.value = savedProgress.durationMs
            }
        }
        initMediaController()
    }

    private val sleepTimerPauseAction: () -> Unit = {
        Log.d(TAG, "SleepTimer triggered: pausing MediaController")
        mediaController?.pause()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture?.addListener({
            if (isReleased) {
                return@addListener
            }
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
                // If a play request was made while connecting, execute it now
                pendingPlayEpisode?.let { episode ->
                    pendingPlayEpisode = null
                    playEpisode(episode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting MediaController", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(appContext))
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return

        // Hook SleepTimer to pause the controller if fired while connected
        SleepTimerManager.registerPauseAction(sleepTimerPauseAction)

        _isPlaying.value = controller.isPlaying
        _playbackSpeed.value = controller.playbackParameters.speed
        val dur = controller.duration.coerceAtLeast(0L)
        if (dur > 0L) {
            _durationMs.value = dur
        }
        controller.currentMediaItem?.let { item ->
            syncCurrentMediaItem(item)
            updatePosition()
        }

        // If the service was already actively playing when connecting, start smooth poller immediately
        if (controller.isPlaying) {
            mainHandler.removeCallbacks(smoothPositionPoller)
            mainHandler.post(smoothPositionPoller)
        }

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncCurrentMediaItem(mediaItem)
                updatePosition()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    mainHandler.removeCallbacks(smoothPositionPoller)
                    mainHandler.post(smoothPositionPoller)
                } else {
                    mainHandler.removeCallbacks(smoothPositionPoller)
                    updatePosition()
                    persistCurrentProgress()
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                val currentSettings = prefsManager.getSettings()
                prefsManager.saveSettings(currentSettings.copy(playbackSpeed = playbackParameters.speed))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val currentDur = controller.duration.coerceAtLeast(0L)
                if (currentDur > 0L) {
                    _durationMs.value = currentDur
                }
                updatePosition()
                if (playbackState == Player.STATE_ENDED) {
                    SleepTimerManager.onEpisodeEnded()
                    persistCurrentProgress()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updatePosition()
            }
        }
        controllerListener = listener
        controller.addListener(listener)
    }

    private fun syncCurrentMediaItem(mediaItem: MediaItem?) {
        if (mediaItem != null) {
            val current = _currentEpisode.value
            if (current == null || current.id != mediaItem.mediaId) {
                val saved = prefsManager.getCurrentEpisode()
                if (saved != null && (saved.id == mediaItem.mediaId || saved.audioUrl == mediaItem.requestMetadata.mediaUri?.toString())) {
                    _currentEpisode.value = saved
                } else {
                    val audioUri = mediaItem.requestMetadata.mediaUri?.toString()
                        ?: mediaItem.localConfiguration?.uri?.toString() ?: ""
                    val durMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    val durSec = durMs / 1000L
                    val durFormatted = if (durSec > 0) {
                        val m = durSec / 60
                        val s = durSec % 60
                        String.format(java.util.Locale.US, "%02d:%02d", m, s)
                    } else ""
                    val resolvedFeedId = mediaItem.mediaMetadata.extras?.getString("feedId")
                        ?.takeIf { it.isNotBlank() }
                        ?: current?.feedId.orEmpty()
                    _currentEpisode.value = Episode(
                        id = mediaItem.mediaId.ifBlank { audioUri },
                        feedId = resolvedFeedId,
                        title = mediaItem.mediaMetadata.title?.toString() ?: "",
                        description = mediaItem.mediaMetadata.description?.toString() ?: "",
                        audioUrl = audioUri,
                        pubDate = mediaItem.mediaMetadata.artist?.toString() ?: "",
                        durationSeconds = durSec,
                        durationFormatted = durFormatted,
                        imageUrl = mediaItem.mediaMetadata.artworkUri?.toString() ?: ""
                    )
                }
            }
        }
    }

    private fun updatePosition() {
        val controller = mediaController ?: return
        if (controller.currentMediaItem == null) return
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val dur = controller.duration.coerceAtLeast(0L)
        if (dur > 0) {
            _durationMs.value = dur
        }
        _positionMs.value = pos
    }

    private fun persistCurrentProgress() {
        val ep = _currentEpisode.value ?: return
        val pos = _positionMs.value
        val dur = _durationMs.value
        if (pos > 0 || dur > 0) {
            lastSavedPositionMs = pos
            prefsManager.saveProgress(
                PlaybackProgress(
                    episodeId = ep.id,
                    positionMs = pos,
                    durationMs = dur
                )
            )
        }
    }

    fun playEpisode(episode: Episode) {
        val controller = mediaController
        if (controller == null) {
            // Controller not yet connected; queue and execute upon connection
            pendingPlayEpisode = episode
            _currentEpisode.value = episode
            prefsManager.saveCurrentEpisode(episode)
            return
        }

        // If the requested episode is already the active item, toggle playback seamlessly
        if (controller.currentMediaItem?.mediaId == episode.id) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
            return
        }

        _currentEpisode.value = episode
        prefsManager.saveCurrentEpisode(episode)

        // Read previous progress breakpoint
        val progress = prefsManager.getProgress(episode.id)
        val startPositionMs = progress?.positionMs ?: 0L

        val extras = Bundle().apply {
            putString("feedId", episode.feedId)
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.pubDate)
            .setDescription(episode.description)
            .setExtras(extras)
            .apply {
                if (episode.imageUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(episode.imageUrl))
                }
            }
            .build()

        val item = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.audioUrl)
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.setMediaItem(item, startPositionMs)
        controller.prepare()
        controller.play()

        updatePosition()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            updatePosition()
            persistCurrentProgress()
        } else {
            if (controller.mediaItemCount == 0) {
                _currentEpisode.value?.let { playEpisode(it) }
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val dur = controller.duration
        val target = positionMs.coerceAtLeast(0L)
        val newPos = if (dur > 0) target.coerceAtMost(dur) else target
        controller.seekTo(newPos)
        _positionMs.value = newPos
        persistCurrentProgress()
    }

    fun seekBack15() {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition - 15000L).coerceAtLeast(0L)
        controller.seekTo(newPos)
        _positionMs.value = newPos
        persistCurrentProgress()
    }

    fun seekForward30() {
        val controller = mediaController ?: return
        val dur = controller.duration
        val target = controller.currentPosition + 30000L
        val newPos = if (dur > 0) target.coerceAtMost(dur) else target
        controller.seekTo(newPos)
        _positionMs.value = newPos
        persistCurrentProgress()
    }

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.playbackParameters = PlaybackParameters(speed)
        _playbackSpeed.value = speed
    }

    fun cyclePlaybackSpeed() {
        val current = _playbackSpeed.value
        val currentIndex = SPEED_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextIndex = if (currentIndex in 0 until SPEED_STEPS.size - 1) currentIndex + 1 else 0
        val nextSpeed = SPEED_STEPS[nextIndex]
        setPlaybackSpeed(nextSpeed)
    }

    fun setSleepTimer(option: SleepTimerOption) {
        SleepTimerManager.setTimer(option)
    }

    fun cancelSleepTimer() {
        SleepTimerManager.cancelTimer()
    }

    fun release() {
        isReleased = true
        SleepTimerManager.unregisterPauseAction(sleepTimerPauseAction)
        persistCurrentProgress()
        mainHandler.removeCallbacks(smoothPositionPoller)
        controllerListener?.let { mediaController?.removeListener(it) }
        controllerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        pendingPlayEpisode = null
    }
}
