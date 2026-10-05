package com.inkcast.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.inkcast.android.InkCastApp
import com.inkcast.android.R
import com.inkcast.android.data.cache.AudioCacheManager
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.ui.MainActivity

/**
 * AndroidX Media3 MediaSessionService for PodFlow.
 * Ensures rock-solid foreground audio playback during screen-off and aggressive OS background killer states.
 */
class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
        const val NOTIFICATION_ID = 1001
        private const val PROGRESS_SAVE_INTERVAL_MS = 5000L
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private lateinit var prefsManager: PreferencesManager

    private val sleepTimerPauseAction: () -> Unit = {
        Log.d(TAG, "SleepTimer fired: pausing player")
        player?.pause()
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            saveCurrentPlaybackProgress()
            if (player?.isPlaying == true) {
                progressHandler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager.getInstance(this)

        // Register SleepTimer pause action
        SleepTimerManager.registerPauseAction(sleepTimerPauseAction)

        // Initialize ExoPlayer with AudioCache DataSource for offline & streaming caching
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(AudioCacheManager.createCacheDataSourceFactory(this))

        // Initialize ExoPlayer with AudioAttributes for speech/podcasts, audio focus, and wake lock
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(15000L) // 15s seek back
            .setSeekForwardIncrementMs(30000L) // 30s seek forward
            .build()

        // Apply saved playback speed
        val savedSpeed = prefsManager.getSettings().playbackSpeed
        exoPlayer.playbackParameters = PlaybackParameters(savedSpeed)

        // Player event listener for breakpoint saving
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveCurrentPlaybackProgress()
                if (isPlaying) {
                    progressHandler.removeCallbacks(progressRunnable)
                    progressHandler.postDelayed(progressRunnable, PROGRESS_SAVE_INTERVAL_MS)
                } else {
                    progressHandler.removeCallbacks(progressRunnable)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        SleepTimerManager.onEpisodeEnded()
                        saveCurrentPlaybackProgress()
                        progressHandler.removeCallbacks(progressRunnable)
                    }
                    Player.STATE_IDLE -> {
                        // Do not save progress in STATE_IDLE to prevent overwriting with 0
                        progressHandler.removeCallbacks(progressRunnable)
                    }
                    Player.STATE_READY -> {
                        saveCurrentPlaybackProgress()
                    }
                    else -> {}
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                saveCurrentPlaybackProgress()
            }
        }
        playerListener = listener
        exoPlayer.addListener(listener)

        player = exoPlayer

        // Session Activity PendingIntent
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // MediaSession configuration
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()

        // Configure Media3 foreground notification provider
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(InkCastApp.PLAYBACK_CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        setMediaNotificationProvider(notificationProvider)

        Log.d(TAG, "PlaybackService created successfully with ExoPlayer & MediaSession")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun saveCurrentPlaybackProgress() {
        val p = player ?: return
        val currentMediaItem = p.currentMediaItem ?: return
        val episodeId = currentMediaItem.mediaId.ifBlank {
            currentMediaItem.requestMetadata.mediaUri?.toString() ?: ""
        }
        if (episodeId.isBlank()) return

        val currentPosition = p.currentPosition
        // Prevent STATE_IDLE or invalid zero position from wiping existing progress
        if (p.playbackState == Player.STATE_IDLE && currentPosition == 0L) {
            return
        }

        val playerDuration = p.duration.coerceAtLeast(0L)
        val finalDuration = if (playerDuration > 0L) {
            playerDuration
        } else {
            val existing = prefsManager.getProgress(episodeId)
            val fallbackEp = prefsManager.getCurrentEpisode()
            if (existing != null && existing.durationMs > 0L) {
                existing.durationMs
            } else if (fallbackEp != null && fallbackEp.id == episodeId && fallbackEp.durationSeconds > 0L) {
                fallbackEp.durationSeconds * 1000L
            } else {
                0L
            }
        }

        val progress = PlaybackProgress(
            episodeId = episodeId,
            positionMs = currentPosition,
            durationMs = finalDuration
        )
        prefsManager.saveProgress(progress)
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService destroying: saving progress and releasing player")
        SleepTimerManager.unregisterPauseAction(sleepTimerPauseAction)
        progressHandler.removeCallbacks(progressRunnable)
        saveCurrentPlaybackProgress()

        // Remove listener BEFORE calling stop to prevent STATE_IDLE callback from overwriting progress
        playerListener?.let {
            player?.removeListener(it)
        }
        playerListener = null

        player?.let {
            it.stop()
            it.clearMediaItems()
        }

        // Release MediaSession FIRST, then release Player (official Media3 requirement)
        mediaSession?.run {
            release()
        }
        mediaSession = null

        player?.release()
        player = null

        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val lastEpisode = prefsManager.getCurrentEpisode()
            if (lastEpisode != null) {
                val progress = prefsManager.getProgress(lastEpisode.id)
                val startPos = progress?.positionMs ?: 0L

                val extras = android.os.Bundle().apply {
                    putString("feedId", lastEpisode.feedId)
                }
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(lastEpisode.title)
                    .setArtist(lastEpisode.pubDate)
                    .setDescription(lastEpisode.description)
                    .setExtras(extras)
                    .apply {
                        if (lastEpisode.imageUrl.isNotBlank()) {
                            setArtworkUri(android.net.Uri.parse(lastEpisode.imageUrl))
                        }
                    }
                    .build()

                val item = MediaItem.Builder()
                    .setMediaId(lastEpisode.id)
                    .setUri(lastEpisode.audioUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                return com.google.common.util.concurrent.Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        listOf(item),
                        0,
                        startPos
                    )
                )
            }
            return super.onPlaybackResumption(mediaSession, controller)
        }
    }
}
