package com.inkcast.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.resolver.FeedResolverAgent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

class PodcastCoverInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url.toString()
        val thumbUrl = FeedResolverAgent.getInstance().toThumbnailUrl(originalUrl)
        val settings = try {
            PreferencesManager(InkCastApp.instance).getSettings()
        } catch (_: Exception) {
            null
        }
        val cfWorkerUrl = settings?.cfWorkerUrl.orEmpty()
        val finalUrl = FeedResolverAgent.getInstance().applyProxyIfOverseas(thumbUrl, cfWorkerUrl)

        val newRequest = request.newBuilder()
            .url(finalUrl)
            .header("User-Agent", "InkCast/2.0 (Android Native M3)")
            .build()
        return chain.proceed(newRequest)
    }
}

class InkCastApp : Application(), ImageLoaderFactory {

    companion object {
        const val PLAYBACK_CHANNEL_ID = "inkcast_playback_channel"
        const val PLAYBACK_CHANNEL_NAME = "InkCast Playback Service"
        lateinit var instance: InkCastApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .addInterceptor(PodcastCoverInterceptor())
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .respectCacheHeaders(false) // Podcast covers rarely have proper cache-control headers
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available heap
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("podcast_covers"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB disk cache
                    .build()
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                PLAYBACK_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback notification for InkCast"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
