package com.inkcast.android

import com.inkcast.android.data.resolver.FeedResolverAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class XmlPullParserTest {

    private val sampleRssXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
            <channel>
                <title>InkCast Test Podcast</title>
                <description>A podcast for testing E-ink RSS parser</description>
                <link>https://example.com/podcast</link>
                <itunes:author>InkCast Team</itunes:author>
                <itunes:image href="https://example.com/cover.jpg"/>
                <item>
                    <title>Episode 1: The E-ink Revolution</title>
                    <description>&lt;p&gt;Discussion on E-ink screens &amp; battery life.&lt;/p&gt;</description>
                    <pubDate>Mon, 01 Sep 2026 10:00:00 GMT</pubDate>
                    <enclosure url="https://traffic.megaphone.fm/test-ep1.mp3" length="12345678" type="audio/mpeg"/>
                    <itunes:duration>00:45:30</itunes:duration>
                    <guid>ep-1-guid</guid>
                </item>
                <item>
                    <title>Episode 2: Domestic Stream</title>
                    <description>Domestic feed test</description>
                    <pubDate>Tue, 02 Sep 2026 12:00:00 GMT</pubDate>
                    <enclosure url="https://feed.shengfm.cn/ep2.mp3" length="8765432" type="audio/mpeg"/>
                    <itunes:duration>1800</itunes:duration>
                    <guid>ep-2-guid</guid>
                </item>
            </channel>
        </rss>
    """.trimIndent()

    @Test
    fun testParseRssXml_MetadataAndProxyInjection() {
        val agent = FeedResolverAgent()
        val cfWorker = "https://my-proxy.workers.dev"

        val result = agent.parseRssXml(
            xmlContent = sampleRssXml,
            feedUrl = "https://example.com/rss.xml",
            originalInput = "https://example.com/rss.xml",
            cfWorkerUrl = cfWorker
        )

        val feed = result.feed
        assertEquals("InkCast Test Podcast", feed.title)
        assertEquals("A podcast for testing E-ink RSS parser", feed.description)
        assertEquals("InkCast Team", feed.author)
        assertEquals("https://example.com/cover.jpg", feed.artworkUrl)

        val episodes = result.episodes
        assertEquals(2, episodes.size)

        // Episode 1 (Megaphone.fm is overseas -> must be wrapped by Cloudflare Worker proxy)
        val ep1 = episodes[0]
        assertEquals("Episode 1: The E-ink Revolution", ep1.title)
        assertEquals("Discussion on E-ink screens & battery life.", ep1.description)
        assertEquals(2730L, ep1.durationSeconds)
        assertEquals("45:30", ep1.durationFormatted)
        val expectedEp1Audio = "$cfWorker/proxy/stream?url=${URLEncoder.encode("https://traffic.megaphone.fm/test-ep1.mp3", "UTF-8")}"
        assertEquals(expectedEp1Audio, ep1.audioUrl)

        // Episode 2 (Shengfm.cn is domestic -> must NOT be wrapped)
        val ep2 = episodes[1]
        assertEquals("Episode 2: Domestic Stream", ep2.title)
        assertEquals(1800L, ep2.durationSeconds)
        assertEquals("30:00", ep2.durationFormatted)
        assertEquals("https://feed.shengfm.cn/ep2.mp3", ep2.audioUrl)
    }
}
