package com.inkcast.android.data.cache

import android.content.Context
import android.util.Log
import com.inkcast.android.data.model.Episode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk cache manager for Podcast RSS Feeds and Episode Lists.
 * Implements offline-first caching so episode lists load immediately without network wait,
 * and allows full offline browsing when network is disconnected.
 */
class PodcastFeedCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "FeedCacheManager"
        private const val CACHE_DIR_NAME = "podcast_feed_cache"

        @Volatile
        private var INSTANCE: PodcastFeedCacheManager? = null

        fun getInstance(context: Context): PodcastFeedCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PodcastFeedCacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getCacheFile(feedId: String): File {
        val safeFileName = "feed_${feedId.hashCode()}.json"
        return File(cacheDir, safeFileName)
    }

    /**
     * Save episodes to disk cache for a specific feed.
     */
    fun saveEpisodes(feedId: String, episodes: List<Episode>) {
        if (feedId.isBlank() || episodes.isEmpty()) return
        try {
            val file = getCacheFile(feedId)
            val jsonObject = JSONObject().apply {
                put("feedId", feedId)
                put("timestamp", System.currentTimeMillis())
                val array = JSONArray()
                episodes.forEach { ep -> array.put(ep.toJson()) }
                put("episodes", array)
            }
            file.writeText(jsonObject.toString(), Charsets.UTF_8)
            Log.d(TAG, "Cached ${episodes.size} episodes for feed: $feedId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache episodes for feed: $feedId", e)
        }
    }

    /**
     * Load cached episodes from disk. Returns empty list if not cached or corrupted.
     */
    fun getCachedEpisodes(feedId: String): List<Episode> {
        if (feedId.isBlank()) return emptyList()
        val file = getCacheFile(feedId)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText(Charsets.UTF_8)
            val jsonObject = JSONObject(content)
            val array = jsonObject.getJSONArray("episodes")
            val list = mutableListOf<Episode>()
            for (i in 0 until array.length()) {
                list.add(Episode.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached episodes for feed: $feedId", e)
            emptyList()
        }
    }

    /**
     * Check whether cached episodes exist for this feed.
     */
    fun hasCache(feedId: String): Boolean {
        val file = getCacheFile(feedId)
        return file.exists() && file.length() > 0
    }

    /**
     * Calculate total size of all cached feed JSON files in bytes.
     */
    fun getCacheSizeBytes(): Long {
        if (!cacheDir.exists()) return 0L
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Clear all cached feed JSON files.
     */
    fun clearCache(): Boolean {
        if (!cacheDir.exists()) return true
        val files = cacheDir.listFiles() ?: return true
        var success = true
        for (f in files) {
            if (!f.delete()) success = false
        }
        return success
    }
}
