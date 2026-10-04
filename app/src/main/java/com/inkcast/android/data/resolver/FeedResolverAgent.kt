package com.inkcast.android.data.resolver

import android.util.Log
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.FeedResolveResult
import com.inkcast.android.data.model.PodcastFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Smart Feed Resolver Pipeline for InkCast-Android.
 *
 * Resolves inputs from:
 * 1. Xiaoyuzhou shared links/text -> RSSHub endpoint
 * 2. Ximalaya album links -> RSSHub endpoint
 * 3. Apple Podcasts CN search fallback -> official author RSS feedUrl
 * 4. Overseas proxy injection (Cloudflare Worker stream proxy)
 * 5. Direct standard RSS / XML feeds
 *
 * Lightweight XML parsing powered by XmlPullParser (Zero heavy 3rd-party XML dependencies).
 */
class FeedResolverAgent(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "FeedResolverAgent"

        @Volatile
        private var INSTANCE: FeedResolverAgent? = null

        fun getInstance(): FeedResolverAgent {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeedResolverAgent().also { INSTANCE = it }
            }
        }

        // Regex patterns for smart sniffers
        val XIAOYUZHOU_REGEX = Regex("""(?:xiaoyuzhoufm|xiaoyuzhou)\.com/podcast/([a-zA-Z0-9]+)""")
        val XIMALAYA_REGEX = Regex("""ximalaya\.com/(?:[a-zA-Z0-9_\-]+/)?(?:album|youshengshu)/(\d+)""")
        val URL_PATTERN = Regex("""^https?://\S+""", RegexOption.IGNORE_CASE)

        // Known overseas podcast hosting domains requiring proxy分流 when proxy is enabled
        val OVERSEAS_DOMAINS = setOf(
            "npr.org",
            "simplecast.com",
            "spreaker.com",
            "bbci.co.uk",
            "megaphone.fm",
            "traffic.megaphone.fm",
            "podbean.com",
            "libsyn.com",
            "acast.com",
            "transistor.fm",
            "omny.fm",
            "buzzsprout.com",
            "chrt.fm",
            "feedproxy.google.com",
            "dts.podtrac.com",
            "prx.org",
            "subsplash.com",
            "fireside.fm",
            "anchor.fm",
            "podtrac.com",
            "rss.acast.com",
            "feed.podbean.com"
        )
    }

    /**
     * Pipeline entry: Resolves user raw input string into a valid, reachable RSS feed URL.
     */
    suspend fun resolveFeedUrl(rawInput: String, settings: AppSettings): String = withContext(ioDispatcher) {
        val input = rawInput.trim()
        require(input.isNotEmpty()) { "输入内容不能为空" }

        // 1. Xiaoyuzhou link sniffer
        val xyzMatch = XIAOYUZHOU_REGEX.find(input)
        if (xyzMatch != null) {
            val podcastId = xyzMatch.groupValues[1]
            val rsshubBase = settings.rsshubBaseUrl.trimEnd('/')
            return@withContext "$rsshubBase/xiaoyuzhou/podcast/$podcastId"
        }

        // 2. Ximalaya link sniffer
        val xmlyMatch = XIMALAYA_REGEX.find(input)
        if (xmlyMatch != null) {
            val albumId = xmlyMatch.groupValues[1]
            val rsshubBase = settings.rsshubBaseUrl.trimEnd('/')
            return@withContext "$rsshubBase/ximalaya/album/$albumId"
        }

        // 3. Check if it's already an HTTP / HTTPS URL
        if (URL_PATTERN.containsMatchIn(input)) {
            // Check if it's an overseas feed and user has CF proxy configured
            return@withContext applyProxyIfOverseas(input, settings.cfWorkerUrl)
        }

        // 4. Non-URL input: Fallback to Apple Podcasts CN Search API
        val searchResult = searchApplePodcastsCn(input)
        return@withContext applyProxyIfOverseas(searchResult, settings.cfWorkerUrl)
    }

    /**
     * Full resolve & fetch: resolves raw input and pulls complete feed metadata + episode list.
     */
    suspend fun resolveAndFetch(rawInput: String, settings: AppSettings): FeedResolveResult = withContext(ioDispatcher) {
        val resolvedUrl = resolveFeedUrl(rawInput, settings)
        Log.d(TAG, "Resolved '$rawInput' to '$resolvedUrl'")

        val xmlContent = fetchXml(resolvedUrl)
        parseRssXml(
            xmlContent = xmlContent,
            feedUrl = resolvedUrl,
            originalInput = rawInput,
            cfWorkerUrl = settings.cfWorkerUrl
        )
    }

    /**
     * Pulls RSS XML from the given network URL with lightweight OkHttp request.
     */
    suspend fun fetchXml(url: String): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "InkCast/2.0 (Android; E-ink Native)")
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("拉取 RSS 失败 HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: throw IllegalStateException("RSS 响应内容为空")
        }
    }

    /**
     * Fallback to Apple Podcasts CN Search API for Chinese/English keywords without VPN.
     * https://itunes.apple.com/search?term=${encode(input)}&media=podcast&country=CN&limit=1
     */
    suspend fun searchApplePodcastsCn(query: String): String = withContext(ioDispatcher) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val itunesUrl = "https://itunes.apple.com/search?term=$encodedQuery&media=podcast&country=CN&limit=1"

        val request = Request.Builder()
            .url(itunesUrl)
            .header("User-Agent", "InkCast/2.0")
            .build()

        val jsonStr = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Apple 播客检索失败 HTTP ${response.code}")
            }
            response.body?.string() ?: throw IllegalStateException("Apple 检索响应为空")
        }

        val json = JSONObject(jsonStr)
        val resultCount = json.optInt("resultCount", 0)
        if (resultCount <= 0) {
            throw NoSuchElementException("未在 Apple Podcasts (中国区) 检索到名为 \"$query\" 的播客")
        }

        val results = json.getJSONArray("results")
        val firstItem = results.getJSONObject(0)
        val feedUrl = firstItem.optString("feedUrl", "")

        if (feedUrl.isBlank()) {
            throw NoSuchElementException("找到播客 \"$query\"，但未包含公开 feedUrl")
        }

        feedUrl
    }

    /**
     * Checks if the given URL belongs to overseas hosting domains.
     */
    fun isOverseasUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            OVERSEAS_DOMAINS.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Wraps target URL with Cloudflare Worker proxy if target is overseas and proxy is configured.
     * Pattern: ${CF_WORKER}/proxy/stream?url=${encode(url)}
     */
    fun applyProxyIfOverseas(targetUrl: String, cfWorkerUrl: String): String {
        if (cfWorkerUrl.isBlank() || targetUrl.isBlank()) return targetUrl
        if (targetUrl.contains("/proxy/stream?url=")) return targetUrl // Avoid double wrapping

        if (isOverseasUrl(targetUrl)) {
            val base = cfWorkerUrl.trimEnd('/')
            val encoded = URLEncoder.encode(targetUrl, "UTF-8")
            return "$base/proxy/stream?url=$encoded"
        }
        return targetUrl
    }

    /**
     * Stream parse RSS 2.0 XML using system-native XmlPullParser.
     * Enclosure audio URLs are selectively routed through Cloudflare Worker proxy if overseas.
     */
    fun parseRssXml(
        xmlContent: String,
        feedUrl: String,
        originalInput: String,
        cfWorkerUrl: String
    ): FeedResolveResult {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType

        var inChannel = false
        var inItem = false

        // Channel level metadata
        var channelTitle = ""
        var channelDescription = ""
        var channelAuthor = ""
        var channelArtworkUrl = ""

        val episodes = mutableListOf<Episode>()

        // Item level metadata
        var itemTitle = ""
        var itemDescription = ""
        var itemPubDate = ""
        var itemAudioUrl = ""
        var itemDurationRaw = ""
        var itemGuid = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName.equals("channel", ignoreCase = true) -> {
                            inChannel = true
                        }
                        tagName.equals("item", ignoreCase = true) -> {
                            inItem = true
                            itemTitle = ""
                            itemDescription = ""
                            itemPubDate = ""
                            itemAudioUrl = ""
                            itemDurationRaw = ""
                            itemGuid = ""
                        }
                        inItem -> {
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    itemTitle = parser.nextTextSafely()
                                }
                                tagName.equals("description", ignoreCase = true) ||
                                        tagName.equals("summary", ignoreCase = true) -> {
                                    if (itemDescription.isBlank()) {
                                        itemDescription = parser.nextTextSafely()
                                    }
                                }
                                tagName.equals("pubDate", ignoreCase = true) -> {
                                    itemPubDate = parser.nextTextSafely()
                                }
                                tagName.equals("guid", ignoreCase = true) -> {
                                    itemGuid = parser.nextTextSafely()
                                }
                                tagName.equals("duration", ignoreCase = true) -> {
                                    itemDurationRaw = parser.nextTextSafely()
                                }
                                tagName.equals("enclosure", ignoreCase = true) -> {
                                    val urlAttr = parser.getAttributeValue(null, "url")
                                    if (!urlAttr.isNullOrBlank()) {
                                        itemAudioUrl = urlAttr
                                    }
                                }
                            }
                        }
                        inChannel && !inItem -> {
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    channelTitle = parser.nextTextSafely()
                                }
                                tagName.equals("description", ignoreCase = true) -> {
                                    channelDescription = parser.nextTextSafely()
                                }
                                tagName.equals("author", ignoreCase = true) -> {
                                    channelAuthor = parser.nextTextSafely()
                                }
                                tagName.equals("image", ignoreCase = true) -> {
                                    // Could be <itunes:image href="..."> or <image><url>...</url></image>
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) {
                                        channelArtworkUrl = href
                                    }
                                }
                                tagName.equals("url", ignoreCase = true) && channelArtworkUrl.isBlank() -> {
                                    channelArtworkUrl = parser.nextTextSafely()
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when {
                        tagName.equals("item", ignoreCase = true) -> {
                            inItem = false
                            if (itemAudioUrl.isNotBlank() || itemTitle.isNotBlank()) {
                                val feedId = generateFeedId(feedUrl)
                                val finalAudioUrl = applyProxyIfOverseas(itemAudioUrl, cfWorkerUrl)
                                val durationSec = parseDurationSeconds(itemDurationRaw)
                                val durationFmt = formatDuration(durationSec)
                                val episodeId = itemGuid.ifBlank { generateEpisodeId(feedId, finalAudioUrl, itemTitle) }

                                episodes.add(
                                    Episode(
                                        id = episodeId,
                                        feedId = feedId,
                                        title = cleanHtml(itemTitle).ifBlank { "未命名单集" },
                                        description = cleanHtml(itemDescription),
                                        audioUrl = finalAudioUrl,
                                        pubDate = sanitizePubDate(itemPubDate),
                                        durationSeconds = durationSec,
                                        durationFormatted = durationFmt
                                    )
                                )
                            }
                        }
                        tagName.equals("channel", ignoreCase = true) -> {
                            inChannel = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val feedId = generateFeedId(feedUrl)
        val feed = PodcastFeed(
            id = feedId,
            title = cleanHtml(channelTitle).ifBlank { "InkCast Feed" },
            description = cleanHtml(channelDescription),
            feedUrl = feedUrl,
            originalInput = originalInput,
            artworkUrl = channelArtworkUrl,
            author = cleanHtml(channelAuthor)
        )

        return FeedResolveResult(feed = feed, episodes = episodes)
    }

    private fun XmlPullParser.nextTextSafely(): String {
        return try {
            nextText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun generateFeedId(feedUrl: String): String {
        return md5(feedUrl.trim())
    }

    fun generateEpisodeId(feedId: String, audioUrl: String, title: String): String {
        return md5("$feedId-$audioUrl-$title")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses duration strings such as "01:23:45", "45:30", or raw seconds "3600".
     */
    fun parseDurationSeconds(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0L

        // Raw number of seconds
        trimmed.toLongOrNull()?.let { return it }

        // Time format HH:MM:SS or MM:SS
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].trim().toLong()
                    val m = parts[1].trim().toLong()
                    val s = parts[2].trim().toLong()
                    h * 3600 + m * 60 + s
                }
                2 -> {
                    val m = parts[0].trim().toLong()
                    val s = parts[1].trim().toLong()
                    m * 60 + s
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Formats duration seconds into readable time string: "MM:SS" or "HH:MM:SS".
     */
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""
        return html
            .replace(Regex("<[^>]*>"), "") // Remove XML/HTML tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun sanitizePubDate(rawDate: String): String {
        val trimmed = rawDate.trim()
        if (trimmed.length > 25) {
            // Cut off time zone details if too long: e.g. "Mon, 18 Sep 2026 07:00:00 +0000" -> "2026-09-18" or clean slice
            val parts = trimmed.split(" ")
            if (parts.size >= 4) {
                return "${parts[1]} ${parts[2]} ${parts[3]}"
            }
        }
        return trimmed
    }
}
