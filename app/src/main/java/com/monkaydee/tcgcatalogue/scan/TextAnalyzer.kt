package com.monkaydee.tcgcatalogue.scan

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Runs ML Kit on-device text recognition on camera frames, a few times per second. */
class TextAnalyzer(
    private val isEnabled: () -> Boolean,
    private val onLines: (List<OcrLine>) -> Unit,
) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastRun = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = proxy.image
        if (media == null || !isEnabled() || now - lastRun < 250) {
            proxy.close()
            return
        }
        lastRun = now
        val rotation = proxy.imageInfo.rotationDegrees
        val height = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()
        recognizer.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { it.lines }.map { line ->
                    val box = line.boundingBox
                    OcrLine(line.text, (box?.top ?: 0) / height, (box?.height() ?: 0) / height)
                }
                if (lines.isNotEmpty()) onLines(lines)
            }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = recognizer.close()
}
