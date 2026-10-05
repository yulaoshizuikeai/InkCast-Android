package com.inkcast.android.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.resolver.FeedResolverAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lightweight, zero-dependency asynchronous image loader for E-ink displays.
 * Supports:
 * 1. Automatic high-res to thumbnail URL rewriting (BBC / NPR / Apple / Simplecast)
 * 2. Overseas CDN proxy routing via Cloudflare Worker
 * 3. Memory-safe BitmapFactory.Options.inSampleSize downsampling
 * 4. RGB_565 16-bit color depth for optimal E-ink RAM footprint
 * 5. In-memory LruCache
 */
object ImageLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 10MB memory cache for thumbnails
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    suspend fun loadBitmap(
        url: String,
        cfWorkerUrl: String = AppSettings.DEFAULT_CF_WORKER_URL,
        targetSizePx: Int = 400
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        val cacheKey = "$url@$targetSizePx"
        memoryCache.get(cacheKey)?.let { return@withContext it }
        memoryCache.get(url)?.let { return@withContext it }

        // 1. Optimize URL to remote thumbnail query if supported by CDN
        val thumbUrl = FeedResolverAgent.getInstance().toThumbnailUrl(url)

        // 2. Wrap with proxy if overseas image host (e.g. BBC ichef, NPR media, Apple mzstatic)
        val finalUrl = FeedResolverAgent.getInstance().applyProxyIfOverseas(thumbUrl, cfWorkerUrl)

        try {
            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "InkCast/2.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val bytes = body.bytes()
                if (bytes.isEmpty()) return@withContext null

                // 3. Decode bounds first to prevent OOM
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                // 4. Calculate downsampling inSampleSize (power of 2)
                var inSampleSize = 1
                val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                while (maxDim / (inSampleSize * 2) >= targetSizePx) {
                    inSampleSize *= 2
                }

                // 5. Decode with downsampling & RGB_565 (optimal 16-bit memory for E-ink)
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    memoryCache.put(url, bitmap)
                }
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }
}
