package com.monkaydee.tcgcatalogue.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * What one camera frame shows: [cardLines] are the lines inside the card guide, positioned in
 * fractions of the guide (0 = its top), [allLines] everything (a slab label sits above the card),
 * and [card] a small picture of the area around the guide, for telling alt arts apart.
 */
class ScanFrame(val cardLines: List<OcrLine>, val allLines: List<OcrLine>, val card: Bitmap?)

/** The card-shaped guide (63×88) drawn over the camera preview, in the same place for the analyser. */
object CardGuide {
    fun rect(width: Float, height: Float): RectF {
        val w = width * 0.8f
        val h = (w * 88f / 63f).coerceAtMost(height * 0.7f)
        val cw = h * 63f / 88f
        val left = (width - cw) / 2
        val top = (height - h) / 2
        return RectF(left, top, left + cw, top + h)
    }
}

/**
 * Runs ML Kit on-device text recognition on camera frames, a few times per second. The frame is
 * cropped to what the preview shows (the camera is bound with the preview's viewport), so the
 * card guide is at the same place in the frame as on screen.
 */
class TextAnalyzer(
    private val isEnabled: () -> Boolean,
    private val onFrame: (ScanFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    private val recognizer = MultilingualOcr()
    private var lastRun = 0L

    override fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!isEnabled() || now - lastRun < 300) {
            proxy.close()
            return
        }
        lastRun = now
        val upright = runCatching { upright(proxy) }.getOrNull()
        if (upright == null) {
            proxy.close()
            return
        }
        val guide = CardGuide.rect(upright.width.toFloat(), upright.height.toFloat())
        recognizer.process(InputImage.fromBitmap(upright, 0))
            .addOnSuccessListener { text ->
                val all = text.textBlocks.flatMap { it.lines }.distinctBy { it.text to it.boundingBox?.top }.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    line.text to box
                }
                // Lines of the card: inside the guide, with some room for a card held a bit too close.
                val near = RectF(guide).apply { inset(-guide.width() * 0.15f, -guide.height() * 0.2f) }
                val cardLines = all.filter { (_, b) -> near.contains(b.exactCenterX(), b.exactCenterY()) }.map { (t, b) ->
                    OcrLine(t, (b.top - guide.top) / guide.height(), b.height() / guide.height())
                }
                val allLines = all.map { (t, b) -> OcrLine(t, b.top / upright.height.toFloat(), b.height() / upright.height.toFloat()) }
                onFrame(ScanFrame(cardLines, allLines, thumbnail(upright, near)))
            }
            .addOnCompleteListener {
                upright.recycle()
                proxy.close()
            }
    }

    /** The visible part of the frame, turned upright. */
    private fun upright(proxy: ImageProxy): Bitmap {
        val full = proxy.toBitmap()
        val crop = proxy.cropRect
        // toBitmap() may or may not have applied the viewport crop already.
        val rect = if (full.width == crop.width() && full.height == crop.height()) {
            Rect(0, 0, full.width, full.height)
        } else {
            Rect(crop).apply { if (!intersect(0, 0, full.width, full.height)) set(0, 0, full.width, full.height) }
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val out = Bitmap.createBitmap(full, rect.left, rect.top, rect.width(), rect.height(), Matrix().apply { postRotate(rotation.toFloat()) }, false)
        if (out !== full) full.recycle()
        return out
    }

    /** The area around the guide (the card is searched in it, so it may sit a bit off), small. */
    private fun thumbnail(frame: Bitmap, area: RectF): Bitmap? = runCatching {
        val r = Rect(area.left.toInt(), area.top.toInt(), area.right.toInt(), area.bottom.toInt())
        if (!r.intersect(0, 0, frame.width, frame.height) || r.width() < 20 || r.height() < 20) return null
        val scale = 320f / r.height()
        Bitmap.createBitmap(frame, r.left, r.top, r.width(), r.height(), Matrix().apply { postScale(scale, scale) }, true)
    }.getOrNull()

    fun close() = recognizer.close()
}
