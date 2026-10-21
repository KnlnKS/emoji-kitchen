package com.kunalan.emojikitchen.core

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val ARTWORK_REVISION = "8cc1fbdf37120228d7305dc2673deac97a7cda14"
private const val ARTWORK = "https://raw.githubusercontent.com/KnlnKS/emoji-kitchen/$ARTWORK_REVISION/"
private const val MAX_DOWNLOAD = 1 shl 20
private class ShareLock {
    val mutex = Mutex()
    var users = 0
}

private val shares = mutableMapOf<String, ShareLock>()

private suspend fun <T> withShareLock(path: String, block: suspend () -> T): T {
    val entry = synchronized(shares) { shares.getOrPut(path, ::ShareLock).also { it.users++ } }
    try {
        return entry.mutex.withLock { block() }
    } finally {
        synchronized(shares) { if (--entry.users == 0) shares.remove(path) }
    }
}

suspend fun Kitchen.pngUri(item: Int): Uri? = try {
    withContext(Dispatchers.IO) {
        val path = artworkPath(item)
        val original = File(context.cacheDir, "share/originals/$ARTWORK_REVISION/${path.removeSuffix(".webp")}.png")
        val file = if (original.isFile) original else withShareLock(path) {
            if (!original.isFile) {
                val networks = context.getSystemService(ConnectivityManager::class.java)
                val online = networks.getNetworkCapabilities(networks.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                if (online) downloadPng(path, original)
            }
            if (original.isFile) original else File(context.cacheDir, "share/$size/$item.png").also {
                if (!it.isFile) writePng(images.decode(item), it)
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }
} catch (_: IOException) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, R.string.copy_failed, Toast.LENGTH_SHORT).show()
    }
    null
}

suspend fun Kitchen.copyToClipboard(item: Int): Boolean {
    val uri = pngUri(item) ?: return false
    copyToClipboard(uri)
    return true
}

suspend fun Kitchen.copyToClipboard(uri: Uri) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newUri(context.contentResolver, "Emoji", uri))
    if (Build.VERSION.SDK_INT < 33) withContext(Dispatchers.Main) {
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}

private suspend fun downloadPng(path: String, file: File) {
    try {
        val bytes = suspendCancellableCoroutine<ByteArray?> { continuation ->
            val connection = URL(ARTWORK + path).openConnection() as HttpURLConnection
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                if (continuation.isActive) continuation.resume(readArtwork(connection))
            } catch (error: IOException) {
                continuation.resumeWithException(error)
            } finally {
                connection.disconnect()
            }
        } ?: return
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType != "image/webp" || bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        writePng(bitmap, file)
    } catch (_: IOException) {
    }
}

private fun readArtwork(connection: HttpURLConnection): ByteArray? {
    connection.connectTimeout = 2000
    connection.readTimeout = 3000
    if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLength > MAX_DOWNLOAD) return null
    return connection.inputStream.use { input ->
        val output = ByteArrayOutputStream(maxOf(8192, connection.contentLength))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_DOWNLOAD) return null
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

private fun writePng(bitmap: Bitmap, file: File) {
    var tmp: File? = null
    try {
        val dir = file.parentFile!!
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create $dir")
        tmp = File.createTempFile("share", ".png", dir)
        tmp.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cannot encode PNG")
        }
        if (!tmp.renameTo(file)) throw IOException("Cannot save $file")
    } finally {
        tmp?.delete()
        bitmap.recycle()
    }
}
