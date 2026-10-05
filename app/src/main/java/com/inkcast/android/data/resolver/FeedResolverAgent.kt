package com.inkcast.android.data.resolver

import android.util.Log
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.FeedResolveResult
import com.inkcast.android.data.model.PodcastFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        val XIMALAYA_REGEX = Regex("""ximalaya\.com/(?:[a-zA-Z0-9_\-]+/)?(?:album|youshengshu)/(\d+)|(?:albumId=)(\d+)""")
        val GENERIC_URL_REGEX = Regex("""https?://[^\s<>"'\)]+""", RegexOption.IGNORE_CASE)

        // Known overseas podcast hosting domains requiring proxy分流 when proxy is enabled
        val OVERSEAS_DOMAINS = setOf(
            "npr.org",
            "simplecast.com",
            "simplecastaudio.com",
            "spreaker.com",
            "bbc.co.uk",
            "bbci.co.uk",
            "bbcfmt.akamaized.net",
            "megaphone.fm",
            "traffic.megaphone.fm",
            "podbean.com",
            "libsyn.com",
            "acast.com",
            "transistor.fm",
            "omny.fm",
            "buzzsprout.com",
            "chrt.fm",
            "chartable.com",
            "feedproxy.google.com",
            "dts.podtrac.com",
            "prx.org",
            "subsplash.com",
            "fireside.fm",
            "anchor.fm",
            "podtrac.com",
            "pdst.fm",
            "rss.acast.com",
            "feed.podbean.com",
            "apple.com",
            "mzstatic.com",
            "spotify.com",
            "byspotify.com",
            "swap.fm",
            "mgln.ai",
            "art19.com",
            "simplecastcdn.com",
            "cloudfront.net",
            "akamaihd.net",
            "audiomeans.fr",
            "iheart.com"
        )
    }

    /**
     * Pipeline entry: Resolves user raw input string into a valid, canonical RSS feed URL.
     */
    suspend fun resolveFeedUrl(rawInput: String, settings: AppSettings): String = withContext(ioDispatcher) {
        val input = rawInput.trim()
        require(input.isNotEmpty()) { "输入内容不能为空" }

        // 1. Xiaoyuzhou link sniffer (matches in share text or direct url)
        val xyzMatch = XIAOYUZHOU_REGEX.find(input)
        if (xyzMatch != null) {
            val podcastId = xyzMatch.groupValues[1]
            val rsshubBase = settings.rsshubBaseUrl.trimEnd('/')
            return@withContext "$rsshubBase/xiaoyuzhou/podcast/$podcastId"
        }

        // 2. Ximalaya link sniffer (matches album path or albumId query)
        val xmlyMatch = XIMALAYA_REGEX.find(input)
        if (xmlyMatch != null) {
            val albumId = xmlyMatch.groupValues[1].ifEmpty { xmlyMatch.groupValues[2] }
            val rsshubBase = settings.rsshubBaseUrl.trimEnd('/')
            return@withContext "$rsshubBase/ximalaya/album/$albumId"
        }

        // 3. Check if any HTTP / HTTPS URL is embedded in input
        val urlMatch = GENERIC_URL_REGEX.find(input)
        if (urlMatch != null) {
            val extractedUrl = urlMatch.value

            // Re-check extracted URL against Xiaoyuzhou / Ximalaya patterns
            val subXyz = XIAOYUZHOU_REGEX.find(extractedUrl)
            if (subXyz != null) {
                val podcastId = subXyz.groupValues[1]
                val rsshubBase = settings.rsshubBaseUrl.trimEnd('/')
                return@withContext "$rsshubBase/xiaoyuzhou/podcast/$podcastId"
            }

            val subXmly = XIMALAYA_REGEX.find(extractedUrl)
            if (subXmly != null) {
                val albumId = subXmly.groupValues[1].ifEmpty { subXmly.groupValues[2] }
                val rsshubBase = settings.rsshubBaseUrl.trimEnd('/')
                return@withContext "$rsshubBase/ximalaya/album/$albumId"
            }

            // Standard direct HTTP/HTTPS XML link
            return@withContext extractedUrl
        }

        // 4. Non-URL input: Fallback to Apple Podcasts CN Search API
        searchApplePodcastsCn(input)
    }

    /**
     * Full resolve & fetch: resolves raw input, applies proxy to request if overseas,
     * pulls complete feed metadata + episode list.
     */
    suspend fun resolveAndFetch(rawInput: String, settings: AppSettings): FeedResolveResult = withContext(ioDispatcher) {
        val canonicalFeedUrl = resolveFeedUrl(rawInput, settings)
        Log.d(TAG, "Resolved '$rawInput' to canonical '$canonicalFeedUrl'")

        // Proxy the RSS XML fetch request if overseas and proxy configured
        val fetchUrl = applyProxyIfOverseas(canonicalFeedUrl, settings.cfWorkerUrl)
        val xmlContent = fetchXml(fetchUrl)

        parseRssXml(
            xmlContent = xmlContent,
            feedUrl = canonicalFeedUrl,
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
     * Fallback to Apple Podcasts Search API for Chinese/English keywords without VPN.
     * Tries Apple CN first: https://itunes.apple.com/search?term=${encode(input)}&media=podcast&country=CN&limit=1
     * If 0 results, fallbacks to global search without country parameter.
     */
    suspend fun searchApplePodcastsCn(query: String): String = withContext(ioDispatcher) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val itunesCnUrl = "https://itunes.apple.com/search?term=$encodedQuery&media=podcast&country=CN&limit=1"

        try {
            val feedUrl = executeItunesSearch(itunesCnUrl)
            if (feedUrl.isNotBlank()) return@withContext feedUrl
        } catch (_: Exception) {
            // Fall through to global search
        }

        // Global fallback search without country parameter
        val itunesGlobalUrl = "https://itunes.apple.com/search?term=$encodedQuery&media=podcast&limit=1"
        val fallbackFeedUrl = executeItunesSearch(itunesGlobalUrl)
        if (fallbackFeedUrl.isNotBlank()) {
            return@withContext fallbackFeedUrl
        }

        throw NoSuchElementException("未在 Apple Podcasts 检索到名为 \"$query\" 的播客")
    }

    private fun executeItunesSearch(url: String): String {
        val request = Request.Builder()
            .url(url)
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
        if (resultCount <= 0) return ""

        val results = json.getJSONArray("results")
        if (results.length() == 0) return ""

        val firstItem = results.getJSONObject(0)
        return firstItem.optString("feedUrl", "").trim()
    }

    /**
     * Checks if the given URL belongs to overseas hosting domains.
     * Uses robust host parsing that does not throw on spaces or unencoded characters.
     */
    fun isOverseasUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val host = try {
            url.toHttpUrlOrNull()?.host?.lowercase()
                ?: URI(url).host?.lowercase()
        } catch (_: Exception) {
            Regex("""https?://([^/:\s]+)""").find(url)?.groupValues?.get(1)?.lowercase()
        } ?: return false

        return OVERSEAS_DOMAINS.any { domain ->
            host == domain || host.endsWith(".$domain")
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

        // Strip leading UTF-8 BOM or whitespace if present
        val sanitizedXml = xmlContent.trim().removePrefix("\uFEFF")
        parser.setInput(StringReader(sanitizedXml))

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
        var itemImageUrl = ""

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
                            itemImageUrl = ""
                        }
                        inItem -> {
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    itemTitle = readTextSafely(parser)
                                }
                                tagName.equals("itunes:title", ignoreCase = true) && itemTitle.isBlank() -> {
                                    itemTitle = readTextSafely(parser)
                                }
                                tagName.equals("description", ignoreCase = true) -> {
                                    if (itemDescription.isBlank()) {
                                        itemDescription = readTextSafely(parser)
                                    }
                                }
                                tagName.equals("summary", ignoreCase = true) ||
                                        tagName.equals("encoded", ignoreCase = true) -> {
                                    if (itemDescription.isBlank()) {
                                        itemDescription = readTextSafely(parser)
                                    }
                                }
                                tagName.equals("pubDate", ignoreCase = true) -> {
                                    itemPubDate = readTextSafely(parser)
                                }
                                tagName.equals("guid", ignoreCase = true) -> {
                                    itemGuid = readTextSafely(parser)
                                }
                                tagName.equals("duration", ignoreCase = true) -> {
                                    itemDurationRaw = readTextSafely(parser)
                                }
                                tagName.equals("image", ignoreCase = true) -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) {
                                        itemImageUrl = href.trim()
                                    }
                                }
                                tagName.equals("enclosure", ignoreCase = true) -> {
                                    val urlAttr = parser.getAttributeValue(null, "url")
                                    if (!urlAttr.isNullOrBlank()) {
                                        itemAudioUrl = urlAttr.trim()
                                    }
                                }
                            }
                        }
                        inChannel && !inItem -> {
                            when {
                                tagName.equals("title", ignoreCase = true) && channelTitle.isBlank() -> {
                                    channelTitle = readTextSafely(parser)
                                }
                                tagName.equals("description", ignoreCase = true) && channelDescription.isBlank() -> {
                                    channelDescription = readTextSafely(parser)
                                }
                                tagName.equals("author", ignoreCase = true) && channelAuthor.isBlank() -> {
                                    channelAuthor = readTextSafely(parser)
                                }
                                tagName.equals("image", ignoreCase = true) -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) {
                                        channelArtworkUrl = href.trim()
                                    }
                                }
                                tagName.equals("url", ignoreCase = true) && channelArtworkUrl.isBlank() -> {
                                    channelArtworkUrl = readTextSafely(parser)
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
                                val epImage = toThumbnailUrl(itemImageUrl.ifBlank { channelArtworkUrl })

                                episodes.add(
                                    Episode(
                                        id = episodeId,
                                        feedId = feedId,
                                        title = cleanHtml(itemTitle).ifBlank { "未命名单集" },
                                        description = cleanHtml(itemDescription),
                                        audioUrl = finalAudioUrl,
                                        pubDate = sanitizePubDate(itemPubDate),
                                        durationSeconds = durationSec,
                                        durationFormatted = durationFmt,
                                        imageUrl = epImage
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
            artworkUrl = toThumbnailUrl(channelArtworkUrl),
            author = cleanHtml(channelAuthor)
        )

        return FeedResolveResult(feed = feed, episodes = episodes)
    }

    /**
     * Safely reads text from the current element.
     * Unlike XmlPullParser.nextText() which throws when encountering inner child XML tags (like <p>, <b>, <br/>),
     * this method collects all inner text nodes and CDATA until the element's matching END_TAG.
     */
    private fun readTextSafely(parser: XmlPullParser): String {
        val targetDepth = parser.depth
        val sb = StringBuilder()
        var event = parser.next()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    sb.append(parser.text)
                }
                XmlPullParser.ENTITY_REF -> {
                    sb.append(parser.text)
                }
            }

            if (parser.depth == targetDepth && event == XmlPullParser.END_TAG) {
                break
            }
            event = parser.next()
        }

        return sb.toString().trim()
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
        if (trimmed.isEmpty()) return ""

        // ISO-8601 date handling: 2026-09-01T... -> 2026-09-01
        if (trimmed.contains("T") && trimmed.contains("-")) {
            return trimmed.substringBefore("T")
        }

        // Standard RFC 2822 date handling: "Mon, 01 Sep 2026 10:00:00 GMT" -> "01 Sep 2026"
        val parts = trimmed.split(" ")
        if (parts.size >= 4 && parts[0].endsWith(",")) {
            return "${parts[1]} ${parts[2]} ${parts[3]}"
        }

        return if (trimmed.length > 20) trimmed.take(20) else trimmed
    }

    /**
     * Converts high-resolution / full-sized podcast cover URLs into lightweight thumbnail URLs.
     * Prevents massive 3000x3000 images from clogging memory and bandwidth on E-ink devices.
     */
    fun toThumbnailUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        var url = rawUrl.trim()

        // 1. BBC ichef service: e.g. /images/ic/3000x3000/xxx.jpg -> /images/ic/400x400/xxx.jpg
        url = url.replace(Regex("""/images/ic/(?:[0-9]+x[0-9]+|raw)/"""), "/images/ic/400x400/")

        // 2. Apple Podcasts CDN (mzstatic): e.g. /600x600bb.jpg -> /300x300bb.jpg
        url = url.replace("600x600bb", "300x300bb")

        // 3. Simplecast CDN: e.g. /3000x3000/ -> /400x400/
        url = url.replace(Regex("""/images/[^/]+/[^/]+/[0-9]+x[0-9]+/""")) { match ->
            match.value.replace(Regex("""[0-9]+x[0-9]+"""), "400x400")
        }

        // 4. NPR asset server: e.g. ?s=1400 -> ?s=400
        if (url.contains("media.npr.org")) {
            url = url.replace(Regex("""[?&]s=\d+"""), "?s=400")
            if (!url.contains("s=400") && !url.contains("?")) {
                url = "$url?s=400"
            }
        }

        return url
    }
}
