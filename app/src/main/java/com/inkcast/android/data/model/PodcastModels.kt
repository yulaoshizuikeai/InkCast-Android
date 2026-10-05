package com.inkcast.android.data.model

import org.json.JSONArray
import org.json.JSONObject

data class PodcastFeed(
    val id: String,
    val title: String,
    val description: String,
    val feedUrl: String,
    val originalInput: String = "",
    val artworkUrl: String = "",
    val author: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("feedUrl", feedUrl)
            put("originalInput", originalInput)
            put("artworkUrl", artworkUrl)
            put("author", author)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PodcastFeed {
            return PodcastFeed(
                id = json.optString("id", ""),
                title = json.optString("title", "未命名播客"),
                description = json.optString("description", ""),
                feedUrl = json.optString("feedUrl", ""),
                originalInput = json.optString("originalInput", ""),
                artworkUrl = json.optString("artworkUrl", ""),
                author = json.optString("author", "")
            )
        }
    }
}

data class Episode(
    val id: String,
    val feedId: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val pubDate: String,
    val durationSeconds: Long,
    val durationFormatted: String,
    val imageUrl: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("feedId", feedId)
            put("title", title)
            put("description", description)
            put("audioUrl", audioUrl)
            put("pubDate", pubDate)
            put("durationSeconds", durationSeconds)
            put("durationFormatted", durationFormatted)
            put("imageUrl", imageUrl)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Episode {
            return Episode(
                id = json.optString("id", ""),
                feedId = json.optString("feedId", ""),
                title = json.optString("title", "未命名单集"),
                description = json.optString("description", ""),
                audioUrl = json.optString("audioUrl", ""),
                pubDate = json.optString("pubDate", ""),
                durationSeconds = json.optLong("durationSeconds", 0L),
                durationFormatted = json.optString("durationFormatted", "--:--"),
                imageUrl = json.optString("imageUrl", "")
            )
        }
    }
}

data class PlaybackProgress(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0

    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= (durationMs - 5000L) // within 5s of end

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("episodeId", episodeId)
            put("positionMs", positionMs)
            put("durationMs", durationMs)
            put("lastUpdated", lastUpdated)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlaybackProgress {
            return PlaybackProgress(
                episodeId = json.optString("episodeId", ""),
                positionMs = json.optLong("positionMs", 0L),
                durationMs = json.optLong("durationMs", 0L),
                lastUpdated = json.optLong("lastUpdated", 0L)
            )
        }
    }
}

data class AppSettings(
    val rsshubBaseUrl: String = DEFAULT_RSSHUB_URL,
    val cfWorkerUrl: String = DEFAULT_CF_WORKER_URL,
    val playbackSpeed: Float = 1.0f,
    val darkMode: Boolean? = null, // null: follow system, true: dark, false: light
    val dynamicColor: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("rsshubBaseUrl", rsshubBaseUrl)
            put("cfWorkerUrl", cfWorkerUrl)
            put("playbackSpeed", playbackSpeed.toDouble())
            if (darkMode != null) put("darkMode", darkMode)
            put("dynamicColor", dynamicColor)
        }
    }

    companion object {
        const val DEFAULT_RSSHUB_URL = "https://rsshub.rssforever.com"
        const val DEFAULT_CF_WORKER_URL = "https://podcast.yunet.cfd"

        fun fromJson(json: JSONObject): AppSettings {
            return AppSettings(
                rsshubBaseUrl = json.optString("rsshubBaseUrl", DEFAULT_RSSHUB_URL),
                cfWorkerUrl = if (json.has("cfWorkerUrl")) json.optString("cfWorkerUrl") else DEFAULT_CF_WORKER_URL,
                playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat(),
                darkMode = if (json.has("darkMode")) json.optBoolean("darkMode") else null,
                dynamicColor = json.optBoolean("dynamicColor", true)
            )
        }
    }
}

data class FeedResolveResult(
    val feed: PodcastFeed,
    val episodes: List<Episode>
)
