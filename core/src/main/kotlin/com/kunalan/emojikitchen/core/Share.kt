package com.kunalan.emojikitchen.core

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Kitchen.pngUri(item: Int): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "share/$size")
    val file = File(dir, "$item.png")
    if (!file.isFile) {
        dir.mkdirs()
        val bitmap = images.decode(item, ImageDecoder.ALLOCATOR_SOFTWARE)
        val tmp = File.createTempFile("share", null, dir)
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        tmp.renameTo(file)
    }
    FileProvider.getUriForFile(context, "${context.packageName}.share", file)
}

suspend fun Kitchen.copyToClipboard(item: Int) {
    val uri = pngUri(item)
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newUri(context.contentResolver, "Emoji", uri))
    if (Build.VERSION.SDK_INT < 33) withContext(Dispatchers.Main) {
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
