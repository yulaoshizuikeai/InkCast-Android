package com.inkcast.android

import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.resolver.FeedResolverAgent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLEncoder

class FeedResolverAgentTest {

    private lateinit var agent: FeedResolverAgent
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val client = OkHttpClient.Builder().build()
        agent = FeedResolverAgent(client = client)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testXiaoyuzhouLinkSniffer_DirectUrl() = runBlocking {
        val input = "https://www.xiaoyuzhoufm.com/podcast/6021f949a789fca4eff4492c?s=share"
        val settings = AppSettings(rsshubBaseUrl = "https://rsshub.rssforever.com")
        val resolved = agent.resolveFeedUrl(input, settings)
        assertEquals("https://rsshub.rssforever.com/xiaoyuzhou/podcast/6021f949a789fca4eff4492c", resolved)
    }

    @Test
    fun testXiaoyuzhouLinkSniffer_ShareText() = runBlocking {
        val input = "【播客名字】https://www.xiaoyuzhoufm.com/podcast/6021f949a789fca4eff4492c?s=... 复制此消息打开小宇宙"
        val settings = AppSettings(rsshubBaseUrl = "https://rsshub.rssforever.com")
        val resolved = agent.resolveFeedUrl(input, settings)
        assertEquals("https://rsshub.rssforever.com/xiaoyuzhou/podcast/6021f949a789fca4eff4492c", resolved)
    }

    @Test
    fun testXimalayaLinkSniffer_AlbumUrl() = runBlocking {
        val input = "https://www.ximalaya.com/album/24716766"
        val settings = AppSettings(rsshubBaseUrl = "https://rsshub.custom.org/")
        val resolved = agent.resolveFeedUrl(input, settings)
        assertEquals("https://rsshub.custom.org/ximalaya/album/24716766", resolved)
    }

    @Test
    fun testXimalayaLinkSniffer_MobileUrl() = runBlocking {
        val input = "https://m.ximalaya.com/album/3829104"
        val settings = AppSettings(rsshubBaseUrl = "https://rsshub.rssforever.com")
        val resolved = agent.resolveFeedUrl(input, settings)
        assertEquals("https://rsshub.rssforever.com/ximalaya/album/3829104", resolved)
    }

    @Test
    fun testXimalayaLinkSniffer_YoushengshuUrl() = runBlocking {
        val input = "https://www.ximalaya.com/youshengshu/99887766"
        val settings = AppSettings(rsshubBaseUrl = "https://rsshub.rssforever.com")
        val resolved = agent.resolveFeedUrl(input, settings)
        assertEquals("https://rsshub.rssforever.com/ximalaya/album/99887766", resolved)
    }

    @Test
    fun testShareTextWithTextBeforeGenericUrl() = runBlocking {
        val input = "推荐一档好播客：https://feed.shengfm.cn/shengfm.xml 欢迎收听！"
        val settings = AppSettings()
        val resolved = agent.resolveFeedUrl(input, settings)
        assertEquals("https://feed.shengfm.cn/shengfm.xml", resolved)
    }

    @Test
    fun testOverseasUrlDetection() {
        assertTrue(agent.isOverseasUrl("https://feeds.npr.org/510318/podcast.xml"))
        assertTrue(agent.isOverseasUrl("https://traffic.megaphone.fm/SC123456.mp3"))
        assertTrue(agent.isOverseasUrl("https://podcasts.files.bbci.co.uk/p02pc9tn.rss"))
        assertTrue(agent.isOverseasUrl("https://media.simplecast.com/audio.mp3"))
        assertTrue(agent.isOverseasUrl("https://dts.podtrac.com/redirect.mp3/example.com/audio.mp3"))

        // Chinese domestic / direct hosts should NOT be marked as overseas
        assertFalse(agent.isOverseasUrl("https://feed.shengfm.cn/shengfm.xml"))
        assertFalse(agent.isOverseasUrl("https://feed.justpodfm.com/leftright.xml"))
        assertFalse(agent.isOverseasUrl("https://rsshub.rssforever.com/xiaoyuzhou/podcast/123"))
    }

    @Test
    fun testOverseasUrlWithSpacesAndParams() {
        val messyUrl = "https://traffic.megaphone.fm/ep 1.mp3?title=你好&token=xyz"
        assertTrue(agent.isOverseasUrl(messyUrl))
    }

    @Test
    fun testOverseasProxyWrapping() {
        val cfWorker = "https://podcast-proxy.user.workers.dev"
        val overseasAudio = "https://traffic.megaphone.fm/audio.mp3"
        val domesticAudio = "https://feed.shengfm.cn/audio.mp3"

        // Overseas with proxy configured -> wrapped
        val wrapped = agent.applyProxyIfOverseas(overseasAudio, cfWorker)
        val expected = "$cfWorker/proxy/stream?url=${URLEncoder.encode(overseasAudio, "UTF-8")}"
        assertEquals(expected, wrapped)

        // Domestic with proxy configured -> untouched
        val domesticResult = agent.applyProxyIfOverseas(domesticAudio, cfWorker)
        assertEquals(domesticAudio, domesticResult)

        // Overseas with empty proxy -> untouched
        val noProxyResult = agent.applyProxyIfOverseas(overseasAudio, "")
        assertEquals(overseasAudio, noProxyResult)

        // Already wrapped -> should not double-wrap
        val doubleWrapped = agent.applyProxyIfOverseas(wrapped, cfWorker)
        assertEquals(wrapped, doubleWrapped)
    }

    @Test
    fun testDurationParsing() {
        assertEquals(5025L, agent.parseDurationSeconds("01:23:45"))
        assertEquals(2730L, agent.parseDurationSeconds("45:30"))
        assertEquals(3600L, agent.parseDurationSeconds("3600"))
        assertEquals(0L, agent.parseDurationSeconds(""))
        assertEquals(0L, agent.parseDurationSeconds("invalid"))
    }

    @Test
    fun testDurationFormatting() {
        assertEquals("01:23:45", agent.formatDuration(5025L))
        assertEquals("45:30", agent.formatDuration(2730L))
        assertEquals("--:--", agent.formatDuration(0L))
    }

    @Test
    fun testCleanHtml() {
        val raw = "<p>Welcome to <b>InkCast</b> &amp; enjoy &lt;reading&gt;! &quot;Quote&#39;s&quot;</p>"
        val cleaned = agent.cleanHtml(raw)
        assertEquals("Welcome to InkCast & enjoy <reading>! \"Quote's\"", cleaned)
    }
}
