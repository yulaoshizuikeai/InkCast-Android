package com.inkcast.android.data.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance Media3 Audio Cache Manager.
 *
 * Provides:
 * 1. Automatic streaming cache: Any streamed podcast audio is seamlessly cached to disk using SimpleCache.
 *    Re-listening or seeking backward does not consume extra bandwidth and works offline.
 * 2. On-demand pre-caching / offline downloading: Users can manually trigger one-tap episode caching
 *    via CacheWriter with real-time download progress tracking.
 * 3. Cache inspection & cleanup: Query total audio cache size and purge on demand.
 */
@OptIn(UnstableApi::class)
object AudioCacheManager {

    private const val TAG = "AudioCacheManager"
    private const val MAX_AUDIO_CACHE_BYTES = 500L * 1024 * 1024 // 500 MB LRU disk cache
    private const val CACHE_DIR_NAME = "media3_audio_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: DatabaseProvider? = null

    // Tracks caching progress: episodeId -> progress percentage (0..100)
    private val _cachingProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cachingProgress: StateFlow<Map<String, Int>> = _cachingProgress.asStateFlow()

    // Set of episode IDs that have been cached
    private val _cachedEpisodeIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedEpisodeIds: StateFlow<Set<String>> = _cachedEpisodeIds.asStateFlow()

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply {
                    if (!exists()) mkdirs()
                }
                val dbProvider = databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also {
                    databaseProvider = it
                }
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_AUDIO_CACHE_BYTES)
                SimpleCache(cacheDir, evictor, dbProvider).also {
                    simpleCache = it
                    Log.d(TAG, "Media3 SimpleCache initialized at ${cacheDir.absolutePath}")
                }
            }
        }
    }

    /**
     * Builds a CacheDataSource.Factory that wraps DefaultHttpDataSource.
     * Can be passed directly into ExoPlayer.Builder or DefaultMediaSourceFactory.
     */
    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("PodFlow/2.1 (Android Native M3)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Check if an audio URL or episode is cached.
     */
    fun isEpisodeCached(context: Context, episodeId: String, audioUrl: String): Boolean {
        if (_cachedEpisodeIds.value.contains(episodeId)) return true
        if (audioUrl.isBlank()) return false
        return try {
            val cache = getCache(context)
            val cachedBytes = cache.getCachedBytes(audioUrl, 0, C.LENGTH_UNSET.toLong())
            // If cached bytes > 500KB or key is cached, consider cached
            if (cachedBytes > 500 * 1024L) {
                _cachedEpisodeIds.update { it + episodeId }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Asynchronously pre-cache an entire episode audio into SimpleCache using CacheWriter.
     */
    suspend fun cacheEpisodeAudio(
        context: Context,
        episodeId: String,
        audioUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (audioUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Audio URL is empty"))
        }

        try {
            _cachingProgress.update { it + (episodeId to 0) }

            val cache = getCache(context)
            val upstreamFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("PodFlow/2.1 (Android Native M3)")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)

            val cacheDataSource = CacheDataSource(
                cache,
                upstreamFactory.createDataSource(),
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
            )

            val dataSpec = DataSpec(Uri.parse(audioUrl))
            val cacheWriter = CacheWriter(
                cacheDataSource,
                dataSpec,
                null, // Use default buffer
                object : CacheWriter.ProgressListener {
                    private var lastReportedPercent = -1

                    override fun onProgress(requestLength: Long, bytesCached: Long, newBytesCached: Long) {
                        val percent = if (requestLength > 0L) {
                            ((bytesCached * 100L) / requestLength).toInt().coerceIn(0, 100)
                        } else {
                            50 // Indeterminate progress
                        }
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            _cachingProgress.update { it + (episodeId to percent) }
                        }
                    }
                }
            )

            cacheWriter.cache()

            _cachingProgress.update { it - episodeId }
            _cachedEpisodeIds.update { it + episodeId }
            Log.d(TAG, "Successfully cached episode audio: $episodeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache episode audio: $episodeId", e)
            _cachingProgress.update { it - episodeId }
            Result.failure(e)
        }
    }

    /**
     * Get the total size in bytes consumed by the audio cache.
     */
    fun getAudioCacheSizeBytes(context: Context): Long {
        return try {
            getCache(context).cacheSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clear all cached audio files.
     */
    fun clearAudioCache(context: Context) {
        try {
            val cache = getCache(context)
            val keys = cache.keys.toList()
            for (key in keys) {
                try {
                    cache.removeResource(key)
                } catch (_: Exception) {}
            }
            _cachedEpisodeIds.value = emptySet()
            _cachingProgress.value = emptyMap()
            Log.d(TAG, "Audio cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio cache", e)
        }
    }
}
