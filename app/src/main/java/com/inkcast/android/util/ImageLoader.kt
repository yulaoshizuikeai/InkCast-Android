package com.inkcast.android.util

import android.content.Context
import android.util.Log
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.inkcast.android.InkCastApp
import java.io.File

/**
 * ImageCacheManager facade for managing Coil image caches and cover storage.
 * Eliminates legacy 30MB memory cache and redundant network client,
 * delegating memory and disk cache lifecycle directly to Coil.
 */
object ImageLoader {

    private const val TAG = "ImageLoader"

    private fun resolveContext(context: Context?): Context? {
        return context ?: InkCastApp.getInstanceOrNull()
    }

    private fun getDiskCacheDir(context: Context? = null): File? {
        val ctx = resolveContext(context) ?: return null
        return try {
            File(ctx.cacheDir, "podcast_covers").apply {
                if (!exists()) mkdirs()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to resolve podcast_covers cache dir", e)
            null
        }
    }

    /**
     * Calculates total disk space consumed by podcast covers in Coil diskCache directory.
     */
    fun getDiskCacheSizeBytes(context: Context? = null): Long {
        val dir = getDiskCacheDir(context) ?: return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Clears Coil in-memory and disk caches, and removes persistent cached covers from disk.
     */
    @OptIn(ExperimentalCoilApi::class)
    fun clearCache(context: Context? = null) {
        val ctx = resolveContext(context)
        if (ctx != null) {
            try {
                val coilLoader = Coil.imageLoader(ctx)
                coilLoader.memoryCache?.clear()
                coilLoader.diskCache?.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing Coil cache", e)
            }
        }

        try {
            getDiskCacheDir(context)?.deleteRecursively()
            getDiskCacheDir(context)?.mkdirs()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing covers disk directory", e)
        }
    }
}
