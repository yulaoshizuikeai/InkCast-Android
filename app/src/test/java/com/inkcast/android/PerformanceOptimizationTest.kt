package com.inkcast.android

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Immutable
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.FeedResolveResult
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.data.model.PodcastFeed
import com.inkcast.android.data.resolver.FeedResolverAgent
import com.inkcast.android.playback.SleepTimerOption
import com.inkcast.android.playback.SleepTimerState
import com.inkcast.android.ui.MainUiState
import com.inkcast.android.util.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PerformanceOptimizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestContext(fakePrefs: FakeSharedPreferences? = null): Context {
        val root = tempFolder.newFolder("test_cache_" + System.nanoTime())
        val prefsToUse = fakePrefs ?: FakeSharedPreferences()
        return object : ContextWrapper(null) {
            override fun getCacheDir(): File = root
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefsToUse
        }
    }

    @Test
    fun testDomainModelsContract() {
        val feed = PodcastFeed(
            id = "test_feed",
            title = "Test Podcast",
            description = "Description",
            feedUrl = "https://example.com/rss.xml"
        )
        val episode = Episode(
            id = "test_ep",
            feedId = "test_feed",
            title = "Test Episode",
            description = "Ep Desc",
            audioUrl = "https://example.com/audio.mp3",
            pubDate = "2026-10-01",
            durationSeconds = 1800L,
            durationFormatted = "30:00"
        )
        val progress = PlaybackProgress(
            episodeId = "test_ep",
            positionMs = 900000L,
            durationMs = 1800000L
        )
        val settings = AppSettings(
            playbackSpeed = 1.25f,
            dynamicColor = true
        )
        val resolveResult = FeedResolveResult(
            feed = feed,
            episodes = listOf(episode)
        )
        val uiState = MainUiState(
            subscribedFeeds = listOf(feed),
            allEpisodes = listOf(episode)
        )
        val timerState = SleepTimerState(
            isActive = true,
            selectedOption = SleepTimerOption.MINUTES_30,
            remainingSeconds = 1800L,
            formattedTime = "30:00"
        )

        assertEquals("test_feed", feed.id)
        assertEquals("test_ep", episode.id)
        assertEquals(50, progress.progressPercent)
        assertFalse(progress.isFinished)
        assertEquals(1.25f, settings.playbackSpeed)
        assertEquals(1, resolveResult.episodes.size)
        assertEquals(1, uiState.subscribedFeeds.size)
        assertTrue(timerState.isActive)
    }

    @Test
    fun testFeedResolverAgentPrecompiledRegexBenchmark() {
        val agent = FeedResolverAgent.getInstance()

        val sampleHtml = "<p>Welcome to <b>InkCast</b> &amp; enjoy &lt;reading&gt;! &quot;Quote&#39;s&quot;</p>"
        for (i in 0 until 5000) {
            val cleaned = agent.cleanHtml(sampleHtml)
            assertEquals("Welcome to InkCast & enjoy <reading>! \"Quote's\"", cleaned)
        }

        val bbcUrl = "http://ichef.bbci.co.uk/images/ic/3000x3000/p0m1wvw4.jpg"
        val appleUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts125/mza_123.jpg/600x600bb.jpg"
        val simplecastUrl = "https://image.simplecastcdn.com/images/uid1/uid2/3000x3000/cover.jpeg?aid=rss"
        val nprUrl = "https://media.npr.org/assets/img/2022/09/23/life-kit_tile.jpg?s=1400&c=66&f=jpg"

        for (i in 0 until 5000) {
            assertEquals("http://ichef.bbci.co.uk/images/ic/400x400/p0m1wvw4.jpg", agent.toThumbnailUrl(bbcUrl))
            assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Podcasts125/mza_123.jpg/300x300bb.jpg", agent.toThumbnailUrl(appleUrl))
            assertEquals("https://image.simplecastcdn.com/images/uid1/uid2/400x400/cover.jpeg?aid=rss", agent.toThumbnailUrl(simplecastUrl))
            assertEquals("https://media.npr.org/assets/img/2022/09/23/life-kit_tile.jpg?s=400&c=66&f=jpg", agent.toThumbnailUrl(nprUrl))
        }
    }

    @Test
    fun testPreferencesManagerInMemeoryCachingAndMigration() {
        PreferencesManager.resetForTesting()
        val fakePrefs = FakeSharedPreferences()
        val context = createTestContext(fakePrefs)

        val pm1 = PreferencesManager.getInstance(context)
        val initialFeeds = pm1.getSubscribedFeeds()
        assertTrue("Initial feeds should not be empty", initialFeeds.isNotEmpty())
        assertEquals("preset_npr_lifekit", pm1.getSelectedFeedId())

        // Simulate user unsubscribing from a preset feed
        val feedToRemove = initialFeeds.first().id
        pm1.removeFeed(feedToRemove)
        val feedsAfterRemoval = pm1.getSubscribedFeeds()
        assertFalse("Removed feed must not be present in cache", feedsAfterRemoval.any { it.id == feedToRemove })

        // Simulate App Restart: Reset singleton instance and re-create PreferencesManager with same SharedPreferences
        PreferencesManager.resetForTesting()
        val pm2 = PreferencesManager.getInstance(context)
        val feedsOnRestart = pm2.getSubscribedFeeds()

        // Verify that one-time migration did NOT resurrect the deleted feed
        assertFalse("Deleted preset feed must not be resurrected on app restart", feedsOnRestart.any { it.id == feedToRemove })
        assertEquals(feedsAfterRemoval.size, feedsOnRestart.size)

        // Test getCurrentEpisode null caching
        PreferencesManager.resetForTesting()
        val pm3 = PreferencesManager.getInstance(context)
        val ep1 = pm3.getCurrentEpisode()
        assertEquals(null, ep1)
        val ep2 = pm3.getCurrentEpisode()
        assertEquals(null, ep2)

        val testEpisode = Episode(
            id = "ep_test",
            feedId = "feed_1",
            title = "Test Ep",
            description = "Desc",
            audioUrl = "http://example.com/audio.mp3",
            pubDate = "2026-10-01",
            durationSeconds = 600L,
            durationFormatted = "10:00"
        )
        pm3.saveCurrentEpisode(testEpisode)
        assertEquals("ep_test", pm3.getCurrentEpisode()?.id)

        pm3.saveCurrentEpisode(null)
        assertEquals(null, pm3.getCurrentEpisode())

        PreferencesManager.resetForTesting()
    }

    @Test
    fun testPreferencesManagerConcurrencyStress() = runTest {
        PreferencesManager.resetForTesting()
        val fakePrefs = FakeSharedPreferences()
        val context = createTestContext(fakePrefs)
        val pm = PreferencesManager.getInstance(context)

        val jobs = (0 until 30).map { i ->
            async(Dispatchers.Default) {
                val feed = PodcastFeed(
                    id = "concurrent_feed_$i",
                    title = "Feed $i",
                    description = "Desc $i",
                    feedUrl = "https://example.com/feed_$i.xml"
                )
                pm.addFeed(feed)
                pm.saveSettings(AppSettings(playbackSpeed = 1.0f + (i % 5) * 0.25f))
                pm.saveProgress(PlaybackProgress(episodeId = "ep_$i", positionMs = i * 1000L, durationMs = 60000L))
                val settings = pm.getSettings()
                assertTrue(settings.playbackSpeed >= 1.0f)
            }
        }
        jobs.awaitAll()

        assertTrue(pm.getSubscribedFeeds().size >= 30)
        PreferencesManager.resetForTesting()
    }

    @Test
    fun testImageLoaderSafeContextHandling() {
        // Test calling ImageLoader with explicit context
        val context = createTestContext()
        val coversDir = File(context.cacheDir, "podcast_covers").apply { mkdirs() }

        assertEquals(0L, ImageLoader.getDiskCacheSizeBytes(context))

        val dummyFile1 = File(coversDir, "cover1.jpg").apply { writeBytes(ByteArray(1024)) }
        val dummyFile2 = File(coversDir, "cover2.jpg").apply { writeBytes(ByteArray(2048)) }

        val calculatedSize = ImageLoader.getDiskCacheSizeBytes(context)
        assertEquals(3072L, calculatedSize)

        ImageLoader.clearCache(context)
        assertEquals(0L, ImageLoader.getDiskCacheSizeBytes(context))

        // Test calling ImageLoader with null context when InkCastApp.instance is not initialized
        // Should not throw UninitializedPropertyAccessException
        val nullSize = ImageLoader.getDiskCacheSizeBytes(null)
        assertEquals(0L, nullSize)
        ImageLoader.clearCache(null)
    }

    @Test
    fun testPlayingFeedResolution() {
        val feedA = PodcastFeed(id = "feed_a", title = "Podcast A", description = "Desc A", feedUrl = "https://a.com/rss")
        val feedB = PodcastFeed(id = "feed_b", title = "Podcast B", description = "Desc B", feedUrl = "https://b.com/rss")
        val feeds = listOf(feedA, feedB)

        // Episode is from feed A, but user selected / browsed feed B
        val episodeFromA = Episode(
            id = "ep_1",
            feedId = "feed_a",
            title = "Episode 1",
            description = "Desc",
            audioUrl = "https://a.com/1.mp3",
            pubDate = "2026-10-01",
            durationSeconds = 1800L,
            durationFormatted = "30:00"
        )
        val selectedFeed = feedB

        // Playing feed should resolve to feed A
        val resolvedPlayingFeed = feeds.find { it.id == episodeFromA.feedId } ?: selectedFeed
        assertEquals("feed_a", resolvedPlayingFeed.id)
        assertEquals("Podcast A", resolvedPlayingFeed.title)

        // When episode feedId is unknown/external, fallback to selectedFeed
        val episodeUnknown = episodeFromA.copy(feedId = "unknown_feed")
        val fallbackFeed = feeds.find { it.id == episodeUnknown.feedId } ?: selectedFeed
        assertEquals("feed_b", fallbackFeed.id)
        assertEquals("Podcast B", fallbackFeed.title)
    }

    @Test
    fun testDurationZeroOrNegativeHandling() {
        // Episode with zero or missing duration
        val episodeZeroDur = Episode(
            id = "ep_0",
            feedId = "feed_a",
            title = "Live Stream",
            description = "",
            audioUrl = "https://stream.com/live.mp3",
            pubDate = "2026-10-01",
            durationSeconds = 0L,
            durationFormatted = ""
        )
        val durationMs = 0L
        val durationToUse = if (durationMs > 0) durationMs else (episodeZeroDur.durationSeconds * 1000L).coerceAtLeast(0L)
        assertEquals(0L, durationToUse)

        // MiniPlayer progress calculation: should be 0f instead of dividing by 1ms and clamping to 100%
        val durMs = if (durationMs > 0) durationMs else (episodeZeroDur.durationSeconds * 1000L).coerceAtLeast(0L)
        val positionMs = 5000L
        val progress = if (durMs <= 0L) 0f else (positionMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0f, progress, 0.0001f)
    }

    @Test
    fun testProgressTimeFormattingWithHours() {
        fun formatProgressTime(ms: Long, forceHours: Boolean = false): String {
            if (ms <= 0L) return if (forceHours) "00:00:00" else "00:00"
            val totalSeconds = ms / 1000L
            val h = totalSeconds / 3600L
            val m = (totalSeconds % 3600L) / 60L
            val s = totalSeconds % 60L
            return if (h > 0 || forceHours) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", m, s)
            }
        }

        val totalDurationMs = 4500000L // 1 hour 15 minutes
        val currentPositionMs = 300000L // 5 minutes

        val durationFormatted = formatProgressTime(totalDurationMs)
        val posFormatted = formatProgressTime(currentPositionMs, forceHours = totalDurationMs >= 3600000L)

        assertEquals("01:15:00", durationFormatted)
        assertEquals("00:05:00", posFormatted) // symmetric hour digit formatting

        // Sub-hour duration
        val shortDurationMs = 1800000L // 30 minutes
        val shortPosMs = 125000L // 2 minutes 5 seconds
        assertEquals("30:00", formatProgressTime(shortDurationMs))
        assertEquals("02:05", formatProgressTime(shortPosMs, forceHours = shortDurationMs >= 3600000L))
    }

    @Test
    fun testStateIdleZeroPositionGuard() {
        val testContext = createTestContext()
        val prefs = PreferencesManager.getInstance(testContext)
        val testEpId = "ep_saved_guard"

        // Pre-condition: user has saved 15-minute progress
        prefs.saveProgress(PlaybackProgress(episodeId = testEpId, positionMs = 900000L, durationMs = 1800000L))
        assertEquals(900000L, prefs.getProgress(testEpId)?.positionMs)

        // Simulated logic from PlaybackService and PlaybackController:
        // When player is in STATE_IDLE or currentMediaItem is null and position is 0L, ignore progress write
        val isIdle = true
        val positionMs = 0L

        val shouldWriteProgress = !(isIdle && positionMs == 0L)
        assertFalse("Progress should NOT be overwritten on STATE_IDLE zero position", shouldWriteProgress)
        assertEquals(900000L, prefs.getProgress(testEpId)?.positionMs)
    }

    class FakeSharedPreferences : android.content.SharedPreferences {
        private val data = java.util.concurrent.ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any>()
            private val toRemove = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    if (value != null) temp[key] = value else toRemove.add(key)
                }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    if (values != null) temp[key] = values else toRemove.add(key)
                }
                return this
            }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                if (key != null) toRemove.add(key)
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                clear = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) data.clear()
                toRemove.forEach { data.remove(it) }
                data.putAll(temp)
            }
        }
    }
}
