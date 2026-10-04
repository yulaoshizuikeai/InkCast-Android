package com.inkcast.android.data.local

import android.content.Context
import android.content.SharedPreferences
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.data.model.PodcastFeed
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "inkcast_preferences"
        private const val KEY_SETTINGS = "key_app_settings"
        private const val KEY_FEEDS = "key_subscribed_feeds"
        private const val KEY_SELECTED_FEED_ID = "key_selected_feed_id"
        private const val KEY_PROGRESS_PREFIX = "key_progress_"
        private const val KEY_CURRENT_EPISODE = "key_current_playing_episode"

        val PRESET_FEEDS = listOf(
            PodcastFeed(
                id = "preset_sheng_fm",
                title = "声动早咖啡",
                description = "唤醒沉睡的身体，每个工作日早晨的轻快早餐，带来全球商业前沿与科技商业动态。",
                feedUrl = "https://feed.shengfm.cn/shengfm.xml",
                originalInput = "声动早咖啡",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts125/v4/4a/1d/a5/4a1da54e-4f1d-f89a-07f1-7917dcabec6a/mza_14569502931448834789.jpg/600x600bb.jpg",
                author = "声动活泼"
            ),
            PodcastFeed(
                id = "preset_left_right",
                title = "忽左忽右",
                description = "一档文化沙龙类播客节目，由杨一和程衍樑主持，探索大历史背后的具体故事。",
                feedUrl = "https://feed.justpodfm.com/leftright.xml",
                originalInput = "忽左忽右",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts126/v4/57/ff/ae/57ffae55-7f91-adfe-b34b-e5621f6a4f75/mza_14365442965271077171.png/600x600bb.jpg",
                author = "JustPod"
            ),
            PodcastFeed(
                id = "preset_npr_up_first",
                title = "NPR: Up First",
                description = "NPR's Up First is the news you need to start your day. The biggest stories and ideas, in 10-15 minutes.",
                feedUrl = "https://feeds.npr.org/510318/podcast.xml",
                originalInput = "https://feeds.npr.org/510318/podcast.xml",
                artworkUrl = "https://media.npr.org/assets/img/2022/11/04/upfirst_square-252199b4d89a71060938ff56bc55a01946fe7e1c.jpg",
                author = "NPR"
            ),
            PodcastFeed(
                id = "preset_bbc_6min",
                title = "BBC 6 Minute English",
                description = "Learn and practise useful English language for everyday situations with BBC Learning English.",
                feedUrl = "https://podcasts.files.bbci.co.uk/p02pc9tn.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/p02pc9tn.rss",
                artworkUrl = "https://ichef.bbci.co.uk/images/ic/640x640/p09s28cr.jpg",
                author = "BBC Radio"
            )
        )
    }

    init {
        // Initialize preset feeds if subscriptions are empty
        if (getSubscribedFeeds().isEmpty()) {
            saveSubscribedFeeds(PRESET_FEEDS)
            setSelectedFeedId(PRESET_FEEDS.first().id)
        }
    }

    fun getSettings(): AppSettings {
        val jsonStr = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            AppSettings.fromJson(JSONObject(jsonStr))
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, settings.toJson().toString()).apply()
    }

    fun getSubscribedFeeds(): List<PodcastFeed> {
        val jsonStr = prefs.getString(KEY_FEEDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<PodcastFeed>()
            for (i in 0 until array.length()) {
                list.add(PodcastFeed.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSubscribedFeeds(feeds: List<PodcastFeed>) {
        val array = JSONArray()
        feeds.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FEEDS, array.toString()).apply()
    }

    fun addFeed(feed: PodcastFeed) {
        val current = getSubscribedFeeds().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == feed.id || it.feedUrl == feed.feedUrl }
        if (existingIndex >= 0) {
            current[existingIndex] = feed
        } else {
            current.add(0, feed)
        }
        saveSubscribedFeeds(current)
    }

    fun removeFeed(feedId: String) {
        val current = getSubscribedFeeds().filterNot { it.id == feedId }
        saveSubscribedFeeds(current)
        if (getSelectedFeedId() == feedId) {
            setSelectedFeedId(current.firstOrNull()?.id ?: "")
        }
    }

    fun getSelectedFeedId(): String {
        return prefs.getString(KEY_SELECTED_FEED_ID, "") ?: ""
    }

    fun setSelectedFeedId(feedId: String) {
        prefs.edit().putString(KEY_SELECTED_FEED_ID, feedId).apply()
    }

    fun saveProgress(progress: PlaybackProgress) {
        if (progress.episodeId.isBlank()) return
        val key = KEY_PROGRESS_PREFIX + progress.episodeId.hashCode()
        prefs.edit().putString(key, progress.toJson().toString()).apply()
    }

    fun getProgress(episodeId: String): PlaybackProgress? {
        if (episodeId.isBlank()) return null
        val key = KEY_PROGRESS_PREFIX + episodeId.hashCode()
        val jsonStr = prefs.getString(key, null) ?: return null
        return try {
            PlaybackProgress.fromJson(JSONObject(jsonStr))
        } catch (_: Exception) {
            null
        }
    }

    fun saveCurrentEpisode(episode: Episode?) {
        if (episode == null) {
            prefs.edit().remove(KEY_CURRENT_EPISODE).apply()
        } else {
            prefs.edit().putString(KEY_CURRENT_EPISODE, episode.toJson().toString()).apply()
        }
    }

    fun getCurrentEpisode(): Episode? {
        val jsonStr = prefs.getString(KEY_CURRENT_EPISODE, null) ?: return null
        return try {
            Episode.fromJson(JSONObject(jsonStr))
        } catch (_: Exception) {
            null
        }
    }
}
