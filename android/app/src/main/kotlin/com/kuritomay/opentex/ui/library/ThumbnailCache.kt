package com.kuritomay.opentex.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailCache {
    private const val TARGET_WIDTH = 420
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height) / 1024
    }

    fun peek(path: String): ImageBitmap? = runCatching { cache.get(path) }.getOrNull()

    suspend fun load(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        peek(path) ?: runCatching { decode(path)?.also { cache.put(path, it) } }.getOrNull()
    }

    private fun decode(path: String): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > TARGET_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }
}
