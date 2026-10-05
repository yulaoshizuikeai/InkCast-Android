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
            // === NPR / BBC 情感与人生故事专区 ===
            PodcastFeed(
                id = "preset_npr_lifekit",
                title = "NPR: Life Kit",
                description = "NPR 温暖实用的人际关系、家庭生活与心理调适指南，关于情感沟通与个人成长。",
                feedUrl = "https://feeds.npr.org/510338/podcast.xml",
                originalInput = "https://feeds.npr.org/510338/podcast.xml",
                artworkUrl = "https://media.npr.org/assets/img/2022/09/23/life-kit_tile_npr-network-01_sq-57e850131de55d68e20bbd9dff2408721d7518e2.jpg?s=400",
                author = "NPR"
            ),
            PodcastFeed(
                id = "preset_modern_love",
                title = "Modern Love",
                description = "《纽约时报》与 WBUR/NPR 联合经典情感专栏：讲述爱恋、失去、真情与救赎的动人故事。",
                feedUrl = "https://rss.art19.com/modern-love",
                originalInput = "https://rss.art19.com/modern-love",
                artworkUrl = "https://image.simplecastcdn.com/images/db3c768b-254c-4291-946b-8216b0b2a2a1/fc848224-9871-4b5a-96fd-8f8f7c33d7cf/400x400/b335632ea69f3a0c9b6235dbc62ccad8a30c7462992ae74f00c3e1127c66d245ae168cdfa53cfc8d5d11293ce0a22e2a953ebe0017f32567b707e15ff71edd93.jpeg?aid=rss_feed",
                author = "The New York Times / WBUR"
            ),
            PodcastFeed(
                id = "preset_npr_invisibilia",
                title = "NPR: Invisibilia",
                description = "探索控制人类行为、情感模式与信念深处的隐形力量，深邃动人的人性与心理叙事。",
                feedUrl = "https://feeds.npr.org/510307/podcast.xml",
                originalInput = "https://feeds.npr.org/510307/podcast.xml",
                artworkUrl = "https://media.npr.org/assets/img/2022/09/23/invisibilia_tile_npr-network-01_sq-94a8b38d9f7ae17255b5ea034778b45ddf5d6f6a.jpg?s=400",
                author = "NPR"
            ),
            PodcastFeed(
                id = "preset_hidden_brain",
                title = "Hidden Brain",
                description = "借助心理学与脑科学洞悉潜意识与情感规律，探索人类行为背后的真实心智。",
                feedUrl = "https://feeds.simplecast.com/kwWc0lhf",
                originalInput = "https://feeds.simplecast.com/kwWc0lhf",
                artworkUrl = "https://image.simplecastcdn.com/images/5982e1b7-239c-4b89-81f9-49df1f33fdae/dd71b17c-82bb-441d-a979-e16975aebc1c/400x400/hbtilewshankarfinal_rgbsimplecast.jpg?aid=rss_feed",
                author = "Hidden Brain, Shankar Vedantam"
            ),
            PodcastFeed(
                id = "preset_npr_rough_trans",
                title = "NPR: Rough Translation",
                description = "跨越文化的个体故事与细腻情感共鸣，探讨家庭、爱情、困境与心灵羁绊。",
                feedUrl = "https://feeds.npr.org/510324/podcast.xml",
                originalInput = "https://feeds.npr.org/510324/podcast.xml",
                artworkUrl = "https://media.npr.org/assets/img/2023/10/10/rough-translation-evergreen-new_tile_npr-network-03_sq-c05d75665535eebde94d3bac52f28da456180713.jpg?s=400",
                author = "NPR"
            ),
            PodcastFeed(
                id = "preset_bbc_soul_music",
                title = "BBC: Soul Music",
                description = "聚焦一首音乐如何触碰人们内心深处的生死离别、初恋、治愈与精神慰藉的真实自白。",
                feedUrl = "https://podcasts.files.bbci.co.uk/b008mj7p.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/b008mj7p.rss",
                artworkUrl = "http://ichef.bbci.co.uk/images/ic/400x400/p0m1wvw4.jpg",
                author = "BBC Radio 4"
            ),
            PodcastFeed(
                id = "preset_bbc_lives_less_ordinary",
                title = "BBC: Lives Less Ordinary",
                description = "扣人心弦的非凡人生自白，讲述真实个体的创伤自愈、逆境成长与深刻感情连接。",
                feedUrl = "https://podcasts.files.bbci.co.uk/p02s5rx7.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/p02s5rx7.rss",
                artworkUrl = "http://ichef.bbci.co.uk/images/ic/400x400/p0p8wzfw.jpg",
                author = "BBC World Service"
            ),
            PodcastFeed(
                id = "preset_bbc_life_scientific",
                title = "BBC: The Life Scientific",
                description = "探索科学家人生旅途中的激情、挫折、童年回忆与人性执着，洋溢人文温度。",
                feedUrl = "https://podcasts.files.bbci.co.uk/b015sqc7.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/b015sqc7.rss",
                artworkUrl = "http://ichef.bbci.co.uk/images/ic/400x400/p0m1wt7m.jpg",
                author = "BBC Radio 4"
            ),
            PodcastFeed(
                id = "preset_bbc_6min",
                title = "BBC: 6 Minute English",
                description = "BBC 经典轻松日常对话，温和生活话题与词汇练习，无政治说教，适合随身听。",
                feedUrl = "https://podcasts.files.bbci.co.uk/p02pc9tn.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/p02pc9tn.rss",
                artworkUrl = "http://ichef.bbci.co.uk/images/ic/400x400/p0hxqkd0.jpg",
                author = "BBC Radio"
            ),

            // === NPR / BBC 科技与前沿探索专区 (偶尔听) ===
            PodcastFeed(
                id = "preset_npr_ted_radio",
                title = "NPR: TED Radio Hour",
                description = "汇聚全球前沿科技灵感、数字创新思想与未来探索。",
                feedUrl = "https://feeds.npr.org/510298/podcast.xml",
                originalInput = "https://feeds.npr.org/510298/podcast.xml",
                artworkUrl = "https://media.npr.org/assets/img/2022/09/23/ted-radio-hour_tile_npr-network-01_sq-3ca507bd2dfa5c26d7db5b41c2b981b24a8789fa.jpg?s=400",
                author = "NPR"
            ),
            PodcastFeed(
                id = "preset_npr_short_wave",
                title = "NPR: Short Wave",
                description = "每日 10 分钟轻松日常科学与科技探索，用温暖生动的方式解读大自然奇妙奥秘。",
                feedUrl = "https://feeds.npr.org/510351/podcast.xml",
                originalInput = "https://feeds.npr.org/510351/podcast.xml",
                artworkUrl = "https://media.npr.org/assets/img/2022/09/23/short-wave_tile_npr-network-01_sq-c268cdb6be92867b01c6ef7e65aed8d605525779.jpg?s=400",
                author = "NPR"
            ),
            PodcastFeed(
                id = "preset_bbc_tech_life",
                title = "BBC: Tech Life",
                description = "探讨全球数字科技、人工智能与互联网创新如何深刻改变普通人的生活与世界。",
                feedUrl = "https://podcasts.files.bbci.co.uk/p01plr2p.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/p01plr2p.rss",
                artworkUrl = "http://ichef.bbci.co.uk/images/ic/400x400/p0kxnh0d.jpg",
                author = "BBC World Service"
            ),
            PodcastFeed(
                id = "preset_bbc_crowdscience",
                title = "BBC: CrowdScience",
                description = "解答世界各地听众关于科技、人脑、日常科学的趣味脑洞，富有趣味与温度。",
                feedUrl = "https://podcasts.files.bbci.co.uk/p04d42rc.rss",
                originalInput = "https://podcasts.files.bbci.co.uk/p04d42rc.rss",
                artworkUrl = "http://ichef.bbci.co.uk/images/ic/400x400/p0p7qtn7.jpg",
                author = "BBC World Service"
            ),

            // === 中文经典生活与文化对照 ===
            PodcastFeed(
                id = "preset_sheng_fm",
                title = "声动早咖啡",
                description = "唤醒沉睡的身体，每个工作日早晨的轻快早餐，带来全球商业前沿与科技商业动态。",
                feedUrl = "https://feed.shengfm.cn/shengfm.xml",
                originalInput = "声动早咖啡",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts125/v4/4a/1d/a5/4a1da54e-4f1d-f89a-07f1-7917dcabec6a/mza_14569502931448834789.jpg/300x300bb.jpg",
                author = "声动活泼"
            ),
            PodcastFeed(
                id = "preset_left_right",
                title = "忽左忽右",
                description = "一档文化沙龙类播客节目，由杨一和程衍樑主持，探索大历史背后的具体故事。",
                feedUrl = "https://feed.justpodfm.com/leftright.xml",
                originalInput = "忽左忽右",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts126/v4/57/ff/ae/57ffae55-7f91-adfe-b34b-e5621f6a4f75/mza_14365442965271077171.png/300x300bb.jpg",
                author = "JustPod"
            )
        )
    }

    init {
        // Initialize preset feeds if subscriptions are empty, or migrate to updated curated lineup
        val currentFeeds = getSubscribedFeeds()
        if (currentFeeds.isEmpty()) {
            saveSubscribedFeeds(PRESET_FEEDS)
            setSelectedFeedId(PRESET_FEEDS.first().id)
        } else {
            // Remove political feeds like Up First if present
            val filtered = currentFeeds.filterNot { it.id == "preset_npr_up_first" || it.feedUrl.contains("510318") }
            val existingIds = filtered.map { it.id }.toSet()
            val existingUrls = filtered.map { it.feedUrl }.toSet()
            val newPresets = PRESET_FEEDS.filterNot { it.id in existingIds || it.feedUrl in existingUrls }
            if (newPresets.isNotEmpty() || filtered.size != currentFeeds.size) {
                saveSubscribedFeeds(filtered + newPresets)
                if (getSelectedFeedId() == "preset_npr_up_first") {
                    setSelectedFeedId(filtered.firstOrNull()?.id ?: PRESET_FEEDS.first().id)
                }
            }
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
