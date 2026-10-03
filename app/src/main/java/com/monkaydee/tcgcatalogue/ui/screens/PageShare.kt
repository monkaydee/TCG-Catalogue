package com.monkaydee.tcgcatalogue.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.monkaydee.tcgcatalogue.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the picture of a binder page says in its footer strip. */
internal data class PageShareInfo(val footer: String)

/** Turns a screenshot of a binder page into a PNG with a footer strip and opens the share sheet. */
internal object PageShare {
    /**
     * Adds the footer ([left] and [right] text on [background] in [foreground]) below [page], saves the
     * result as PNG in cacheDir/shared/ and returns its content URI.
     */
    suspend fun save(context: Context, page: Bitmap, left: String, right: String, background: Int, foreground: Int): Uri = withContext(Dispatchers.IO) {
        // A bitmap taken from a graphics layer can live in graphics memory; a Canvas needs a normal one.
        val source = page.copy(Bitmap.Config.ARGB_8888, false) ?: error("bitmap copy failed")
        val margin = (source.width * 0.03f).toInt()
        val footer = (source.width * 0.09f).toInt().coerceAtLeast(48)
        val width = source.width + margin * 2
        val height = source.height + margin + footer
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)

        val size = footer * 0.42f
        val baseline = source.height + margin + footer * 0.5f + size * 0.35f
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = size; typeface = Typeface.DEFAULT_BOLD }
        val brandWidth = brand.measureText(right)
        canvas.drawText(right, width - margin - brandWidth, baseline, brand)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; alpha = 210; textSize = size }
        val room = width - margin * 3 - brandWidth
        canvas.drawText(TextUtils.ellipsize(left, android.text.TextPaint(title), room, TextUtils.TruncateAt.END).toString(), margin.toFloat(), baseline, title)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "binder-page-${System.currentTimeMillis()}.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.recycle()
        source.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Opens the Android share sheet for the picture at [uri]. */
    fun open(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
