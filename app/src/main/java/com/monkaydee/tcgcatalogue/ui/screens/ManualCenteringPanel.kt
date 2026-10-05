package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.Centering
import com.monkaydee.tcgcatalogue.grade.PreGrader
import kotlin.math.abs
import kotlin.math.roundToInt

/** Explicit frame placement: photo edges were confirmed separately before this editor opens. */
@Composable
internal fun ManualCenteringPanel(side: PreGrader.Side, onApply: (Centering.Result) -> Unit) {
    var open by remember(side.card) { mutableStateOf(false) }
    if (!open) {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.center_adjust)) }
        return
    }
    val bitmap = side.card
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val initial = remember(bitmap, side.centering) {
        side.centering?.let { c ->
            listOf((c.left + c.cuts[0]) / bitmap.width, (c.right + c.cuts[1]) / bitmap.width,
                (c.top + c.cuts[2]) / bitmap.height, (c.bottom + c.cuts[3]) / bitmap.height)
        } ?: List(4) { 0.05 }
    }
    var borders by remember(bitmap, side.centering) { mutableStateOf(initial.map { it.coerceIn(0.001, 0.45) }) }
    var selected by remember(bitmap) { mutableIntStateOf(0) }
    val liveBorders by rememberUpdatedState(borders)
    val liveSelected by rememberUpdatedState(selected)
    val names = listOf(R.string.center_left, R.string.center_right, R.string.center_top, R.string.center_bottom)
    val result = Centering.manual(bitmap.width, bitmap.height, borders)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.center_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .pointerInput(bitmap) {
                detectDragGestures(onDragStart = { point ->
                    val f = liveBorders
                    val distances = listOf(abs(point.x - f[0] * size.width), abs(point.x - (1 - f[1]) * size.width),
                        abs(point.y - f[2] * size.height), abs(point.y - (1 - f[3]) * size.height))
                    selected = distances.indices.minBy { distances[it] }
                }) { change, _ ->
                    change.consume()
                    val index = liveSelected
                    val value = when (index) {
                        0 -> change.position.x / size.width
                        1 -> 1 - change.position.x / size.width
                        2 -> change.position.y / size.height
                        else -> 1 - change.position.y / size.height
                    }.toDouble().coerceIn(0.001, 0.45)
                    borders = liveBorders.toMutableList().also { it[index] = value }
                }
            }) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val xs = listOf(borders[0].toFloat() * size.width, (1 - borders[1]).toFloat() * size.width)
            val ys = listOf(borders[2].toFloat() * size.height, (1 - borders[3]).toFloat() * size.height)
            repeat(4) { i ->
                val a = if (i < 2) Offset(xs[i], 0f) else Offset(0f, ys[i - 2])
                val b = if (i < 2) Offset(xs[i], size.height) else Offset(size.width, ys[i - 2])
                drawLine(Color.Black, a, b, 5.dp.toPx())
                drawLine(if (i == selected) Color.Yellow else Color(0xFF00E676), a, b, 2.dp.toPx())
            }
        }
        result?.let {
            Text("↔ %.1f/%.1f · ↕ %.1f/%.1f".format(it.leftRight, 100 - it.leftRight, it.topBottom, 100 - it.topBottom),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            names.forEachIndexed { i, label ->
                FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(stringResource(label)) })
            }
        }
        // A two-times detail view around the selected guide makes small placement errors visible.
        Text(stringResource(R.string.center_detail), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
            val cw = (bitmap.width / 2).coerceAtLeast(1); val ch = (bitmap.height / 2).coerceAtLeast(1)
            val cx = when (selected) { 0 -> borders[0] * bitmap.width; 1 -> (1 - borders[1]) * bitmap.width; else -> bitmap.width / 2.0 }
            val cy = when (selected) { 2 -> borders[2] * bitmap.height; 3 -> (1 - borders[3]) * bitmap.height; else -> bitmap.height / 2.0 }
            val x = (cx - cw / 2).roundToInt().coerceIn(0, bitmap.width - cw)
            val y = (cy - ch / 2).roundToInt().coerceIn(0, bitmap.height - ch)
            drawImage(image, srcOffset = IntOffset(x, y), srcSize = IntSize(cw, ch),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val a = if (selected < 2) Offset(((cx - x) / cw * size.width).toFloat(), 0f)
                else Offset(0f, ((cy - y) / ch * size.height).toFloat())
            val b = if (selected < 2) Offset(a.x, size.height) else Offset(size.width, a.y)
            drawLine(Color.Black, a, b, 5.dp.toPx()); drawLine(Color.Yellow, a, b, 2.dp.toPx())
        }
        Slider(value = borders[selected].toFloat(), onValueChange = { value ->
            borders = borders.toMutableList().also { it[selected] = value.toDouble() }
        }, valueRange = 0.001f..0.45f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(-1, 1).forEach { direction ->
                OutlinedButton(onClick = {
                    val dimension = if (selected < 2) bitmap.width else bitmap.height
                    borders = borders.toMutableList().also {
                        it[selected] = (it[selected] + direction.toDouble() / dimension).coerceIn(0.001, 0.45)
                    }
                }) { Text(stringResource(if (direction < 0) R.string.center_minus_pixel else R.string.center_plus_pixel)) }
            }
        }
        Text(stringResource(R.string.center_manual_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { borders = initial.map { it.coerceIn(0.001, 0.45) }; open = false }) {
                Text(stringResource(R.string.center_cancel))
            }
            Button(enabled = result != null, onClick = { result?.let(onApply); open = false }) {
                Text(stringResource(R.string.center_apply))
            }
        }
    }
}
