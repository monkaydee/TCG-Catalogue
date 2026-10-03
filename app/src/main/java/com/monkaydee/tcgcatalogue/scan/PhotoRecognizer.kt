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
     * card, [picture] is the photo (upright, small), for telling alt arts apart.
     */
    data class Result(val hits: List<ScanHit>, val grade: GradeInfo?, val picture: Bitmap? = null)

    suspend fun recognize(context: Context, uri: Uri, parse: (List<OcrLine>) -> List<ScanHit>): Result {
        val bitmap = withContext(Dispatchers.IO) { decode(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // Photos taken sideways or upside down: try the other orientations when nothing is found.
            for (rotation in listOf(0, 90, 270, 180)) {
                val text = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
                val height = (if (rotation % 180 == 0) bitmap.height else bitmap.width).toFloat()
                val lines = text.textBlocks.flatMap { it.lines }.map { line ->
                    val box = line.boundingBox
                    OcrLine(line.text, (box?.top ?: 0) / height, (box?.height() ?: 0) / height)
                }
                val hits = parse(lines)
                if (hits.isNotEmpty()) {
                    val single = hits.size == 1
                    val picture = if (single) runCatching { uprightSmall(bitmap, rotation) }.getOrNull() else null
                    return Result(hits, CardTextParser.parseGrade(lines).takeIf { single }, picture)
                }
            }
            return Result(emptyList(), null)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /** The photo turned the way the text was read, at most 420 pixels on its longest side. */
    private fun uprightSmall(photo: Bitmap, rotation: Int): Bitmap {
        val scale = 420f / max(photo.width, photo.height)
        return Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }, true)
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
