package com.kunalan.emojikitchen.core

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

class Kitchen private constructor(
    internal val context: Context,
    channel: FileChannel,
    base: Long,
) : PackIndex(channel, base) {
    val images: Images = Images(this)
    val favorites: ComboList by lazy { ComboList(this, File(context.filesDir, "favorites.txt"), 500) }
    val recents: ComboList by lazy { ComboList(this, File(context.filesDir, "recents.txt"), 24) }

    init {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = images.trim(level)
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = images.trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        })
    }

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
            return Kitchen(app, channel, base)
        }
    }
}
