package com.monkaydee.tcgcatalogue.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** The user's own background pictures, kept as small copies in the app's storage. */
object LookImages {
    private const val MAX_SIDE = 1600

    /**
     * Copies the picture at [uri] for [slot] ("home", "binder"), scaled down, and returns its
     * path. Each copy gets a new name, so the screens show the new picture straight away.
     */
    suspend fun import(context: Context, uri: Uri, slot: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "look").apply { mkdirs() }
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri).let { b ->
                val scale = MAX_SIDE.toFloat() / max(b.width, b.height)
                if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
            }
        }
        val file = File(dir, "$slot-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        source.recycle()
        // Drop older copies for this slot.
        dir.listFiles { f -> f.name.startsWith("$slot-") && f != file }?.forEach { it.delete() }
        file.absolutePath
    }

    fun remove(context: Context, slot: String) {
        File(context.filesDir, "look").listFiles { f -> f.name.startsWith("$slot-") }?.forEach { it.delete() }
    }
}
