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
import com.inkcast.android.InkCastApp
import com.inkcast.android.R
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.ui.MainActivity

/**
 * AndroidX Media3 MediaSessionService for InkCast.
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
    private lateinit var prefsManager: PreferencesManager

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
        prefsManager = PreferencesManager(this)

        // Initialize ExoPlayer with AudioAttributes for speech/podcasts, audio focus, and wake lock
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
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
        exoPlayer.addListener(object : Player.Listener {
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
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        saveCurrentPlaybackProgress()
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
        })

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
            .build()
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
        val duration = p.duration.coerceAtLeast(0L)

        val progress = PlaybackProgress(
            episodeId = episodeId,
            positionMs = currentPosition,
            durationMs = duration
        )
        prefsManager.saveProgress(progress)
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService destroying: saving progress and releasing player")
        progressHandler.removeCallbacks(progressRunnable)
        saveCurrentPlaybackProgress()

        player?.let {
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        player = null

        mediaSession?.run {
            release()
            mediaSession = null
        }

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

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(lastEpisode.title)
                    .setArtist(lastEpisode.pubDate)
                    .setDescription(lastEpisode.description)
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
