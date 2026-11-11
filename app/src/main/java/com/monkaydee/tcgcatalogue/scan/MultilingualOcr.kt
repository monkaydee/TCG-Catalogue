package com.monkaydee.tcgcatalogue.scan

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

/** Bundled script models work offline; merge positioned lines instead of replacing native text with Latin guesses. */
class MultilingualOcr {
    data class Result(val textBlocks: List<Text.TextBlock>)
    private val recognizers = listOf(
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
    )
    fun process(image: InputImage): Task<Result> = Tasks.whenAllSuccess<Text>(recognizers.map { it.process(image) })
        .continueWith { task -> Result(task.result.flatMap { it.textBlocks }) }
    fun close() = recognizers.forEach { it.close() }
}
