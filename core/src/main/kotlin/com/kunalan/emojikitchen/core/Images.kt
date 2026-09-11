package com.kunalan.emojikitchen.core

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aomedia.avif.android.AvifDecoder

class Images internal constructor(private val pack: PackIndex) {
    private val decoder = Dispatchers.IO.limitedParallelism(4)
    private val cache = object : LruCache<Int, Bitmap>(minOf(Runtime.getRuntime().maxMemory() / 8, 48L shl 20).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }

    fun peek(item: Int): Bitmap? = cache[item]

    suspend fun load(item: Int): Bitmap = cache[item] ?: withContext(decoder) {
        cache[item] ?: decode(item).also {
            it.prepareToDraw()
            cache.put(item, it)
        }
    }

    internal fun decode(item: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val encoded = pack.read(item)
        check(AvifDecoder.decode(encoded, encoded.remaining(), bitmap)) { "Undecodable item $item" }
        return bitmap
    }

    internal fun trim(level: Int) {
        cache.trimToSize(if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) 0 else cache.maxSize() / 2)
    }

    private companion object {
        const val SIZE = 256
    }
}
