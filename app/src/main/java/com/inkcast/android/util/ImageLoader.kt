package com.inkcast.android.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.inkcast.android.InkCastApp
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.resolver.FeedResolverAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * High-performance asynchronous image loader with Two-Tier Caching:
 * Tier 1: Fast in-memory LruCache (30MB)
 * Tier 2: Persistent on-device disk cache (podcast_covers directory)
 *
 * Ensures ultra-smooth 120 FPS scrolling with zero network lag and persistent offline covers.
 */
object ImageLoader {

    private const val TAG = "ImageLoader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    // 30MB RAM cache for instant bitmap display
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    // Compose ImageBitmap cache to completely prevent GC allocations during 120Hz fling
    private val imageBitmapCache: LruCache<String, ImageBitmap> = LruCache(300)

    private fun getDiskCacheDir(): File? {
        return try {
            val app = InkCastApp.instance
            File(app.cacheDir, "podcast_covers").apply {
                if (!exists()) mkdirs()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.hashCode().toString()
        }
    }

    /**
     * Synchronous RAM cache lookup for zero-delay, zero-flicker first-frame rendering.
     */
    fun getMemoryCached(url: String, targetSizePx: Int = 400): Bitmap? {
        if (url.isBlank()) return null
        val cacheKey = "$url@$targetSizePx"
        return memoryCache.get(cacheKey) ?: memoryCache.get(url)
    }

    /**
     * Synchronous ImageBitmap cache lookup for Compose UI zero-recomposition rendering.
     */
    fun getImageBitmapCached(url: String, targetSizePx: Int = 400): ImageBitmap? {
        if (url.isBlank()) return null
        val cacheKey = "$url@$targetSizePx"
        imageBitmapCache.get(cacheKey)?.let { return it }
        val bmp = getMemoryCached(url, targetSizePx) ?: return null
        val imgBmp = bmp.asImageBitmap()
        imageBitmapCache.put(cacheKey, imgBmp)
        return imgBmp
    }

    /**
     * Asynchronously loads ImageBitmap, storing both Bitmap and ImageBitmap in cache.
     */
    suspend fun loadImageBitmap(
        url: String,
        cfWorkerUrl: String = AppSettings.DEFAULT_CF_WORKER_URL,
        targetSizePx: Int = 400
    ): ImageBitmap? {
        getImageBitmapCached(url, targetSizePx)?.let { return it }
        val bmp = loadBitmap(url, cfWorkerUrl, targetSizePx) ?: return null
        val imgBmp = bmp.asImageBitmap()
        val cacheKey = "$url@$targetSizePx"
        imageBitmapCache.put(cacheKey, imgBmp)
        return imgBmp
    }

    /**
     * Loads a bitmap with Two-Tier Cache (Memory -> Disk -> Network).
     */
    suspend fun loadBitmap(
        url: String,
        cfWorkerUrl: String = AppSettings.DEFAULT_CF_WORKER_URL,
        targetSizePx: Int = 400
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        val cacheKey = "$url@$targetSizePx"

        // 1. Tier 1: Memory Cache Hit
        memoryCache.get(cacheKey)?.let { return@withContext it }
        memoryCache.get(url)?.let { return@withContext it }

        // 2. Tier 2: Disk Cache Hit
        val diskCacheDir = getDiskCacheDir()
        val fileHash = hashUrl(url)
        val cachedFile = if (diskCacheDir != null) File(diskCacheDir, "$fileHash.img") else null

        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            val diskBitmap = decodeSampledBitmapFromFile(cachedFile, targetSizePx)
            if (diskBitmap != null) {
                memoryCache.put(cacheKey, diskBitmap)
                return@withContext diskBitmap
            }
        }

        // 3. Tier 3: Remote Network Fetch
        val thumbUrl = FeedResolverAgent.getInstance().toThumbnailUrl(url)
        val finalUrl = FeedResolverAgent.getInstance().applyProxyIfOverseas(thumbUrl, cfWorkerUrl)

        try {
            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "InkCast/2.0 (Android Native M3)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val bytes = body.bytes()
                if (bytes.isEmpty()) return@withContext null

                // Save to disk cache atomically
                if (cachedFile != null && diskCacheDir != null) {
                    try {
                        val tempFile = File.createTempFile("img_dl_", ".tmp", diskCacheDir)
                        FileOutputStream(tempFile).use { it.write(bytes) }
                        if (tempFile.renameTo(cachedFile)) {
                            Log.d(TAG, "Cached cover to disk: ${cachedFile.name}")
                        } else {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing cover to disk cache", e)
                    }
                }

                // Decode bitmap in ARGB_8888
                val bitmap = decodeSampledBitmapFromByteArray(bytes, targetSizePx)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                }
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromFile(file: File, targetSizePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var inSampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / (inSampleSize * 2) >= targetSizePx) {
                inSampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromByteArray(bytes: ByteArray, targetSizePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var inSampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / (inSampleSize * 2) >= targetSizePx) {
                inSampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(context: android.content.Context = InkCastApp.instance) {
        try {
            val coilLoader = coil.Coil.imageLoader(context)
            coilLoader.memoryCache?.clear()
            coilLoader.diskCache?.clear()
        } catch (_: Exception) {}
        memoryCache.evictAll()
        imageBitmapCache.evictAll()
        getDiskCacheDir()?.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {}
        }
    }
}
