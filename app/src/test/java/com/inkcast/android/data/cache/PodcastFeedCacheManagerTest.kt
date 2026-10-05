package com.inkcast.android.data.cache

import android.content.Context
import android.content.ContextWrapper
import com.inkcast.android.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PodcastFeedCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestContext(): Context {
        val root = tempFolder.newFolder("test_cache")
        return object : ContextWrapper(null) {
            override fun getCacheDir(): File = root
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun testSaveAndGetCachedEpisodes() {
        val context = createTestContext()
        val cacheManager = PodcastFeedCacheManager(context)

        val feedId = "feed_123"
        val sampleEpisodes = listOf(
            Episode(
                id = "ep_1",
                feedId = feedId,
                title = "Episode 1",
                description = "Desc 1",
                audioUrl = "https://example.com/audio1.mp3",
                pubDate = "2026-10-01",
                durationSeconds = 1200L,
                durationFormatted = "20:00"
            ),
            Episode(
                id = "ep_2",
                feedId = feedId,
                title = "Episode 2",
                description = "Desc 2",
                audioUrl = "https://example.com/audio2.mp3",
                pubDate = "2026-10-02",
                durationSeconds = 1800L,
                durationFormatted = "30:00"
            )
        )

        assertFalse(cacheManager.hasCache(feedId))
        assertEquals(0, cacheManager.getCachedEpisodes(feedId).size)

        cacheManager.saveEpisodes(feedId, sampleEpisodes)

        assertTrue(cacheManager.hasCache(feedId))
        val cached = cacheManager.getCachedEpisodes(feedId)
        assertEquals(2, cached.size)
        assertEquals("Episode 1", cached[0].title)
        assertEquals("https://example.com/audio1.mp3", cached[0].audioUrl)
        assertEquals("Episode 2", cached[1].title)
        assertEquals(1800L, cached[1].durationSeconds)

        assertTrue(cacheManager.getCacheSizeBytes() > 0)

        // Clear cache
        val cleared = cacheManager.clearCache()
        assertTrue(cleared)
        assertFalse(cacheManager.hasCache(feedId))
        assertEquals(0, cacheManager.getCachedEpisodes(feedId).size)
    }

    @Test
    fun testEmptyFeedHandling() {
        val context = createTestContext()
        val cacheManager = PodcastFeedCacheManager(context)

        cacheManager.saveEpisodes("", emptyList())
        assertFalse(cacheManager.hasCache(""))
        assertEquals(0, cacheManager.getCachedEpisodes("").size)
    }
}
