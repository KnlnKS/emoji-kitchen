package com.kunalan.emojikitchen.core

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.util.LruCache
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aomedia.avif.android.AvifDecoder

class Images internal constructor(private val pack: PackIndex) {
    private val decoder = Dispatchers.IO.limitedParallelism(4)
    private val canvases = ConcurrentLinkedQueue<Bitmap>()
    private val cache = object : LruCache<Int, Bitmap>(minOf(Runtime.getRuntime().maxMemory() / 8, 48L shl 20).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }

    fun peek(item: Int): Bitmap? = cache[item]

    suspend fun load(item: Int): Bitmap = cache[item] ?: withContext(decoder) {
        cache[item] ?: decode(item) { it.copy(Bitmap.Config.HARDWARE, false) }.also { cache.put(item, it) }
    }

    internal fun <T> decode(item: Int, use: (Bitmap) -> T): T {
        val canvas = canvases.poll() ?: Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            val encoded = pack.read(item)
            check(AvifDecoder.decode(encoded, encoded.remaining(), canvas)) { "Undecodable item $item" }
            return use(canvas)
        } finally {
            canvases.offer(canvas)
        }
    }

    internal fun trim(level: Int) {
        cache.trimToSize(if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) 0 else cache.maxSize() / 2)
    }

    private companion object {
        const val SIZE = 256
    }
}
