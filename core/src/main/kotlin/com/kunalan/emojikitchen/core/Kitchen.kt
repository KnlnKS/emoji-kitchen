package com.kunalan.emojikitchen.core

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class Kitchen private constructor(internal val context: Context, internal val pack: PackIndex) {
    val emojiCount: Int = pack.emojiCount
    val categoryStart: IntArray = pack.categoryStart
    val images: Images = Images(pack)
    val favorites: ComboList by lazy { ComboList(pack, File(context.filesDir, "favorites.txt"), 500) }
    val recents: ComboList by lazy { ComboList(pack, File(context.filesDir, "recents.txt"), 24) }

    init {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = images.trim(level)
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = images.trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        })
    }

    fun emoji(e: Int): String = pack.emoji(e)
    fun indexOf(emoji: String): Int = pack.indexOf(emoji)
    fun partnerCount(e: Int): Int = pack.partnerCount(e)
    fun partner(e: Int, k: Int): Int = pack.partner(e, k)
    fun partnerItem(e: Int, k: Int): Int = pack.partnerItem(e, k)
    fun item(a: Int, b: Int): Int = pack.item(a, b)
    fun combo(item: Int): Combo = pack.combo(item)

    companion object {
        private const val PACK = "kitchen.pak"

        @Volatile
        private var instance: Kitchen? = null

        fun get(context: Context): Kitchen? = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext)?.also { instance = it }
        }

        private fun open(app: Context): Kitchen? {
            val (channel, base) = try {
                val afd = app.assets.openFd(PACK)
                ParcelFileDescriptor.AutoCloseInputStream(afd.parcelFileDescriptor).channel to afd.startOffset
            } catch (_: IOException) {
                val file = File(app.getExternalFilesDir(null), PACK)
                if (!file.isFile) return null
                FileInputStream(file).channel to 0L
            }
            return Kitchen(app, PackIndex(channel, base))
        }
    }
}
