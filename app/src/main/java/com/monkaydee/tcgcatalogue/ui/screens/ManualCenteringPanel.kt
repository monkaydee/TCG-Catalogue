package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.Centering
import com.monkaydee.tcgcatalogue.grade.PreGrader
import kotlin.math.abs
import kotlin.math.roundToInt

/** Four independent outer/inner pairs on the corrected card image, preserved in Result.cuts. */
@Composable
internal fun ManualCenteringPanel(side: PreGrader.Side, onApply: (Centering.Result) -> Unit) {
    var open by remember(side.card) { mutableStateOf(false) }
    if (!open) {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.center_adjust)) }
        return
    }
    val bitmap = side.card
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val dimensions = listOf(bitmap.width, bitmap.width, bitmap.height, bitmap.height)
    // Interleaved outer/inner fractions measured inward from L, R, T, B.
    val initial = remember(bitmap, side.centering) {
        val c = side.centering
        val widths = c?.let { listOf(it.left, it.right, it.top, it.bottom) }
        (0..3).flatMap { i ->
            val outer = ((c?.cuts?.get(i) ?: 0.0) / dimensions[i]).coerceIn(0.0, 0.48)
            listOf(outer, (outer + (widths?.get(i)?.div(dimensions[i]) ?: 0.05)).coerceIn(outer + 1.0 / dimensions[i], 0.49))
        }
    }
    var guides by remember(bitmap, side.centering) { mutableStateOf(initial) }
    var selected by remember(bitmap) { mutableIntStateOf(1) }
    var zoom by remember(bitmap) { mutableIntStateOf(2) }
    val currentGuides by rememberUpdatedState(guides)
    val currentSelected by rememberUpdatedState(selected)
    val names = listOf(R.string.center_left, R.string.center_right, R.string.center_top, R.string.center_bottom)
    val result = Centering.manual(bitmap.width, bitmap.height, (0..3).map { guides[it * 2] }, (0..3).map { guides[it * 2 + 1] })
    fun move(index: Int, value: Double) {
        val g = currentGuides
        val pixel = 1.0 / dimensions[index / 2]
        val range = if (index % 2 == 0) 0.0..(g[index + 1] - pixel).coerceAtLeast(0.0)
            else (g[index - 1] + pixel)..0.49
        guides = g.toMutableList().also { it[index] = value.coerceIn(range) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.center_hint), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
        result?.let {
            Text("↔ %.1f/%.1f · ↕ %.1f/%.1f".format(it.leftRight, 100 - it.leftRight, it.topBottom, 100 - it.topBottom),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(it.left, it.right, it.top, it.bottom).forEachIndexed { i, gap ->
                    Column { Text(stringResource(names[i]), color = MaterialTheme.colorScheme.onSurface)
                        Text("%.1f px".format(gap), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        // Main view and magnified detail both allow dragging; the detail focuses on the chosen line.
        for (detail in listOf(false, true)) {
            if (detail) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(2, 5, 10, 20).forEach { z ->
                        FilterChip(selected = zoom == z, onClick = { zoom = z }, label = { Text("${z}×") })
                    }
                }
            }
            val cw = if (detail) (bitmap.width / zoom).coerceAtLeast(1) else bitmap.width
            val ch = if (detail) (bitmap.height / zoom).coerceAtLeast(1) else bitmap.height
            val axis = selected / 2
            val pos = if (axis == 1 || axis == 3) 1 - guides[selected] else guides[selected]
            val cx = if (axis < 2) pos * bitmap.width else bitmap.width / 2.0
            val cy = if (axis >= 2) pos * bitmap.height else bitmap.height / 2.0
            var cropLock by remember(bitmap, detail) { mutableStateOf<IntOffset?>(null) }
            val x = cropLock?.x ?: if (detail) (cx - cw / 2.0).roundToInt().coerceIn(0, bitmap.width - cw) else 0
            val y = cropLock?.y ?: if (detail) (cy - ch / 2.0).roundToInt().coerceIn(0, bitmap.height - ch) else 0
            val viewport by rememberUpdatedState(IntOffset(x, y))
            Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .testTag(if (detail) "center_detail_canvas" else "center_main_canvas")
                .pointerInput(bitmap, detail, zoom) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val dragX = viewport.x; val dragY = viewport.y
                        val g = currentGuides
                        val candidates = if (detail) listOf(currentSelected / 2 * 2, currentSelected / 2 * 2 + 1) else (0..7).toList()
                        // Select at finger-down: waiting for touch slop can cross the neighboring line.
                        val index = candidates.minBy { i ->
                            val a = i / 2; val f = if (a == 1 || a == 3) 1 - g[i] else g[i]
                            if (a < 2) abs(down.position.x - (f * bitmap.width - dragX) / cw * size.width)
                            else abs(down.position.y - (f * bitmap.height - dragY) / ch * size.height)
                        }
                        selected = index
                        cropLock = IntOffset(dragX, dragY)
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.position == change.previousPosition) continue
                                change.consume()
                                val a = index / 2
                                val f = if (a < 2) (dragX + change.position.x / size.width * cw) / bitmap.width
                                    else (dragY + change.position.y / size.height * ch) / bitmap.height
                                move(index, if (a == 1 || a == 3) 1 - f.toDouble() else f.toDouble())
                            }
                        } finally { cropLock = null }
                    }
                }) {
                drawImage(image, srcOffset = IntOffset(x, y), srcSize = IntSize(cw, ch),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                repeat(8) { i ->
                    val a = i / 2; val f = if (a == 1 || a == 3) 1 - guides[i] else guides[i]
                    val line = if (a < 2) ((f * bitmap.width - x) / cw * size.width).toFloat()
                        else ((f * bitmap.height - y) / ch * size.height).toFloat()
                    val start = if (a < 2) Offset(line, 0f) else Offset(0f, line)
                    val end = if (a < 2) Offset(line, size.height) else Offset(size.width, line)
                    val color = if (i == selected) Color.Yellow else if (i % 2 == 0) Color.Cyan else Color(0xFF00E676)
                    drawLine(Color.Black, start, end, 5.dp.toPx()); drawLine(color, start, end, 2.dp.toPx())
                    val handle = if (a < 2) Offset(line.coerceIn(5.dp.toPx(), size.width - 5.dp.toPx()), size.height / 2)
                        else Offset(size.width / 2, line.coerceIn(5.dp.toPx(), size.height - 5.dp.toPx()))
                    if (line >= 0 && line <= if (a < 2) size.width else size.height) {
                        drawCircle(Color.Black, 6.dp.toPx(), handle); drawCircle(color, 3.dp.toPx(), handle)
                    }
                }
            }
            if (!detail) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    names.forEachIndexed { i, label ->
                        FilterChip(selected = selected / 2 == i, onClick = { selected = i * 2 + selected % 2 }, label = { Text(stringResource(label)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(R.string.center_outer, R.string.center_inner).forEachIndexed { i, label ->
                        FilterChip(selected = selected % 2 == i, onClick = { selected = selected / 2 * 2 + i }, label = { Text(stringResource(label)) })
                    }
                }
            }
        }
        val min = if (selected % 2 == 0) 0.0 else guides[selected - 1] + 1.0 / dimensions[selected / 2]
        val max = if (selected % 2 == 0) (guides[selected + 1] - 1.0 / dimensions[selected / 2]).coerceAtLeast(0.0) else 0.49
        Slider(value = guides[selected].toFloat(), onValueChange = { move(selected, it.toDouble()) }, valueRange = min.toFloat()..max.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(-1, 1).forEach { direction ->
                OutlinedButton(onClick = { move(selected, guides[selected] + direction.toDouble() / dimensions[selected / 2]) }) {
                    Text(stringResource(if (direction < 0) R.string.center_minus_pixel else R.string.center_plus_pixel))
                }
            }
        }
        Text(stringResource(R.string.center_manual_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.center_cancel)) }
            Button(enabled = result != null, onClick = { result?.let(onApply); open = false }) { Text(stringResource(R.string.center_apply)) }
        }
    }
}
