package com.monkaydee.tcgcatalogue.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Reads card numbers from a saved photo (gallery pick or shared image), fully on-device. */
object PhotoRecognizer {
    /** Big enough to read the small collector number on a binder page, small enough for memory. */
    private const val MAX_SIDE = 4000

    /**
     * Cards found in a photo, and the slab label if the photo shows one graded card. With a single
     * card, [picture] is the photo (upright, small), for telling alt arts apart. [texts] is what
     * could be read when no card number was found (e.g. the name), to help a search by picture.
     */
    data class Result(val hits: List<ScanHit>, val grade: GradeInfo?, val picture: Bitmap? = null, val texts: List<String> = emptyList())

    suspend fun recognize(context: Context, uri: Uri, parse: (List<OcrLine>) -> List<ScanHit>): Result {
        val bitmap = withContext(Dispatchers.IO) { decode(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // Photos taken sideways or upside down: try the other orientations when nothing is found.
            var bestTexts = emptyList<String>()
            var bestLines = emptyList<OcrLine>()
            for (rotation in listOf(0, 90, 270, 180)) {
                val text = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
                val height = (if (rotation % 180 == 0) bitmap.height else bitmap.width).toFloat()
                val lines = text.textBlocks.flatMap { it.lines }.map { line ->
                    val box = line.boundingBox
                    OcrLine(line.text, (box?.top ?: 0) / height, (box?.height() ?: 0) / height)
                }
                val hits = parse(lines)
                // Keep the orientation in which the most words could be read.
                val texts = lines.map { it.text }
                if (texts.sumOf { t -> t.count(Char::isLetter) } > bestTexts.sumOf { t -> t.count(Char::isLetter) }) {
                    bestTexts = texts
                    bestLines = lines
                }
                if (hits.isNotEmpty()) {
                    val single = hits.size == 1
                    val picture = if (single) runCatching { uprightSmall(bitmap, rotation) }.getOrNull() else null
                    return Result(hits, CardTextParser.parseGrade(lines).takeIf { single }, picture, texts)
                }
            }
            // No number anywhere: read the card's bottom strip again, enlarged. Collector numbers and
            // promo codes ("SVP DE 123", "TG03/TG30") are tiny and often lost behind a toploader.
            val strip = smallPrint(recognizer, bitmap)
            if (strip.isNotEmpty()) {
                val lines = bestLines + strip
                val hits = parse(lines)
                if (hits.isNotEmpty()) {
                    val single = hits.size == 1
                    val picture = if (single) runCatching { uprightSmall(bitmap, 0) }.getOrNull() else null
                    return Result(hits, CardTextParser.parseGrade(lines).takeIf { single }, picture, lines.map { it.text })
                }
            }
            return Result(emptyList(), null, texts = bestTexts + strip.map { it.text })
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /**
     * Text in the bottom strip of the card in [photo], read again from enlarged crops: the strip
     * under the card's outline (straight-edge search, works behind toploaders) and, in case the
     * outline is off, two bands across the lower half of the photo.
     */
    private suspend fun smallPrint(recognizer: com.google.mlkit.vision.text.TextRecognizer, photo: Bitmap): List<OcrLine> {
        val regions = ArrayList<android.graphics.Rect>()
        val scale = minOf(1f, 1600f / max(photo.width, photo.height))
        val work = if (scale < 1f) Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true) else photo
        val pixels = IntArray(work.width * work.height).also { work.getPixels(it, 0, work.width, 0, 0, work.width, work.height) }
        val quad = runCatching { com.monkaydee.tcgcatalogue.grade.CardRectifier.findQuad(Pixels(work.width, work.height, pixels)) }.getOrNull()
        if (work !== photo) work.recycle()
        quad?.corners?.let { c ->
            val x0 = (c.minOf { it.x } / scale).toInt(); val x1 = (c.maxOf { it.x } / scale).toInt()
            val y0 = c.minOf { it.y } / scale; val y1 = c.maxOf { it.y } / scale
            regions += android.graphics.Rect(x0, (y0 + (y1 - y0) * 0.80).toInt(), x1, (y1 + (y1 - y0) * 0.03).toInt())
        }
        regions += android.graphics.Rect(0, (photo.height * 0.55).toInt(), photo.width, (photo.height * 0.80).toInt())
        regions += android.graphics.Rect(0, (photo.height * 0.75).toInt(), photo.width, photo.height)
        val out = ArrayList<OcrLine>()
        for (r in regions) {
            if (!r.intersect(0, 0, photo.width, photo.height) || r.width() < 16 || r.height() < 8) continue
            val zoom = (2000f / r.width()).coerceIn(1f, 3f)
            val crop = Bitmap.createBitmap(photo, r.left, r.top, r.width(), r.height(), Matrix().apply { postScale(zoom, zoom) }, true)
            val text = runCatching { recognizer.process(InputImage.fromBitmap(crop, 0)).await() }.getOrNull()
            crop.recycle()
            // Placed at the bottom of the card, where the parser expects collector numbers and set codes.
            text?.textBlocks?.flatMap { it.lines }?.forEach { out += OcrLine(it.text, 0.95f, 0.02f) }
        }
        return out
    }

    /** The photo turned the way the text was read, at most 420 pixels on its longest side. */
    private fun uprightSmall(photo: Bitmap, rotation: Int): Bitmap {
        val scale = 420f / max(photo.width, photo.height)
        return Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }, true)
    }

    /** A photo, upright and at most [maxSide] pixels on its longest side, for finding a card by its picture. */
    suspend fun loadSmall(context: Context, uri: Uri, maxSide: Int = 900): Bitmap = withContext(Dispatchers.IO) {
        val full = decode(context, uri)
        val scale = maxSide.toFloat() / max(full.width, full.height)
        if (scale >= 1f) full else Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true).also { full.recycle() }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF orientation itself.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Not an image")
        val orientation = runCatching {
            resolver.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
