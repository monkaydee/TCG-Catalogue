package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.Centering
import com.monkaydee.tcgcatalogue.grade.CenteringRotation
import com.monkaydee.tcgcatalogue.grade.PreGrader
import com.monkaydee.tcgcatalogue.grade.AutoAlignment
import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Four independent outer/inner pairs on the corrected card image, preserved in Result.cuts. */
@Composable
internal fun ManualCenteringPanel(side: PreGrader.Side, fullscreen: Boolean = false, onCancel: () -> Unit = {}, onSkip: (() -> Unit)? = null, onApply: (Centering.Result) -> Unit) {
    var open by remember(side.card) { mutableStateOf(fullscreen) }
    if (!open) {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.center_adjust)) }
        return
    }
    val bitmap = side.card
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var rotation by remember(bitmap, side.centering) { mutableDoubleStateOf(side.centering?.rotationDegrees ?: 0.0) }
    var rotating by remember(bitmap) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var aligning by remember(bitmap) { mutableStateOf(false) }
    var alignmentNote by remember(bitmap) { mutableStateOf<String?>(null) }
    val rotatedBounds = CenteringRotation.bounds(bitmap.width, bitmap.height, rotation)
    val workWidth = rotatedBounds.width; val workHeight = rotatedBounds.height
    val dimensions = listOf(workWidth, workWidth, workHeight, workHeight)
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
    var zoom by remember(bitmap) { mutableIntStateOf(if (fullscreen) 1 else 2) }
    val currentGuides by rememberUpdatedState(guides)
    val currentSelected by rememberUpdatedState(selected)
    val names = listOf(R.string.center_left, R.string.center_right, R.string.center_top, R.string.center_bottom)
    val result = Centering.manual(workWidth, workHeight, (0..3).map { guides[it * 2] }, (0..3).map { guides[it * 2 + 1] })?.copy(rotationDegrees = rotation)
    fun move(index: Int, value: Double) {
        val g = currentGuides
        val pixel = 1.0 / dimensions[index / 2]
        val range = if (index % 2 == 0) 0.0..(g[index + 1] - pixel).coerceAtLeast(0.0)
            else (g[index - 1] + pixel)..0.49
        guides = g.toMutableList().also { it[index] = value.coerceIn(range) }
    }
    fun rotateTo(degrees: Double) {
        val next = degrees.coerceIn(-CenteringRotation.LIMIT, CenteringRotation.LIMIT)
        val old = CenteringRotation.bounds(bitmap.width, bitmap.height, rotation)
        val bounds = CenteringRotation.bounds(bitmap.width, bitmap.height, next)
        guides = CenteringRotation.reframeGuides(guides, old, bounds)
        rotation = next
    }
    Column(Modifier.then(if (fullscreen) Modifier.fillMaxSize() else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!fullscreen) Text(stringResource(R.string.center_hint), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
        result?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("↔ %.1f/%.1f · ↕ %.1f/%.1f".format(it.leftRight, 100 - it.leftRight, it.topBottom, 100 - it.topBottom),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { rotating = !rotating; if (rotating) zoom = 1 }, modifier = Modifier.width(100.dp).testTag("center_rotation_toggle")) {
                    Text(stringResource(R.string.center_rotation_value, rotation))
                }
            }
            if (!fullscreen) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(it.left, it.right, it.top, it.bottom).forEachIndexed { i, gap ->
                    Column { Text(stringResource(names[i]), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
                        Text("%.1f px".format(gap), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        if (fullscreen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    names.forEachIndexed { i, label ->
                        FilterChip(selected = selected / 2 == i, onClick = { selected = i * 2 + selected % 2 }, label = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.center_outer, R.string.center_inner).forEachIndexed { i, label ->
                        FilterChip(selected = selected % 2 == i, onClick = { selected = selected / 2 * 2 + i }, modifier = Modifier.weight(1f), label = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface) })
                    }
                    Box {
                        var zoomMenu by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { zoomMenu = true }) { Text("${zoom}× ▾") }
                        DropdownMenu(expanded = zoomMenu, onDismissRequest = { zoomMenu = false }) {
                            listOf(1, 2, 5, 10, 20).forEach { z ->
                                DropdownMenuItem(text = { Text("${z}×") }, onClick = { zoom = z; zoomMenu = false })
                            }
                        }
                    }
                }
            }
        if (rotating) {
            OutlinedButton(onClick = {
                aligning = true
                scope.launch(Dispatchers.Main.immediate) {
                    val correction = withContext(Dispatchers.Default) {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        AutoAlignment.correction(Pixels(bitmap.width, bitmap.height, pixels))
                    }
                    if (correction != null) {
                        rotateTo(correction)
                        alignmentNote = context.getString(R.string.pre_align_done)
                    } else alignmentNote = context.getString(R.string.pre_align_missing)
                    aligning = false
                }
            }, enabled = !aligning, modifier = Modifier.fillMaxWidth().testTag("center_auto_align")) {
                Text(stringResource(if (aligning) R.string.pre_align_busy else R.string.pre_auto_align))
            }
            alignmentNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(-1.0, -0.1, 0.0, 0.1, 1.0).forEach { step ->
                    TextButton(onClick = { rotateTo(if (step == 0.0) 0.0 else rotation + step) }, modifier = Modifier.weight(1f)) {
                        Text(if (step == 0.0) stringResource(R.string.center_rotation_reset) else "%+.1f°".format(step))
                    }
                }
            }
            Slider(value = rotation.toFloat(), onValueChange = { rotateTo(it.toDouble()) }, valueRange = -15f..15f,
                modifier = Modifier.testTag("center_rotation_slider"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.center_rotation_hint), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { rotating = false }) { Text(stringResource(R.string.center_rotation_done)) }
            }
        }
        // Main view and magnified detail both allow dragging; the detail focuses on the chosen line.
        for (detail in if (fullscreen) listOf(zoom > 1) else listOf(false, true)) {
            if (detail && !fullscreen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(2, 5, 10, 20).forEach { z ->
                        FilterChip(selected = zoom == z, onClick = { zoom = z }, label = { Text("${z}×", color = MaterialTheme.colorScheme.onSurface) })
                    }
                }
            }
            val cw = if (detail) (workWidth / zoom).coerceAtLeast(1) else workWidth
            val ch = if (detail) (workHeight / zoom).coerceAtLeast(1) else workHeight
            val axis = selected / 2
            val pos = if (axis == 1 || axis == 3) 1 - guides[selected] else guides[selected]
            val cx = if (axis < 2) pos * workWidth else workWidth / 2.0
            val cy = if (axis >= 2) pos * workHeight else workHeight / 2.0
            var cropLock by remember(bitmap, detail) { mutableStateOf<IntOffset?>(null) }
            val x = cropLock?.x ?: if (detail) (cx - cw / 2.0).roundToInt().coerceIn(0, workWidth - cw) else 0
            val y = cropLock?.y ?: if (detail) (cy - ch / 2.0).roundToInt().coerceIn(0, workHeight - ch) else 0
            val viewport by rememberUpdatedState(IntOffset(x, y))
            Canvas(Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(workWidth.toFloat() / workHeight))
                .testTag(if (detail) "center_detail_canvas" else "center_main_canvas")
                .pointerInput(bitmap, detail, zoom, rotating) {
                    val vw = minOf(size.width.toFloat(), size.height * workWidth.toFloat() / workHeight)
                    val vh = vw * workHeight / workWidth
                    val ox = (size.width - vw) / 2; val oy = (size.height - vh) / 2
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (rotating) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val touches = event.changes.filter { it.pressed && it.previousPressed }
                                if (touches.isEmpty()) continue
                                val before: Offset; val after: Offset
                                if (touches.size >= 2) {
                                    before = touches[0].previousPosition - touches[1].previousPosition
                                    after = touches[0].position - touches[1].position
                                } else {
                                    val centre = Offset(size.width / 2f, size.height / 2f)
                                    before = touches[0].previousPosition - centre
                                    after = touches[0].position - centre
                                }
                                if (before.getDistance() > 24.dp.toPx() && after.getDistance() > 24.dp.toPx()) {
                                    rotateTo(rotation + CenteringRotation.angleDelta(before.x, before.y, after.x, after.y))
                                }
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                            return@awaitEachGesture
                        }
                        val dragX = viewport.x; val dragY = viewport.y
                        val g = currentGuides
                        val candidates = if (detail) listOf(currentSelected / 2 * 2, currentSelected / 2 * 2 + 1) else (0..7).toList()
                        // Select at finger-down: waiting for touch slop can cross the neighboring line.
                        val index = candidates.minBy { i ->
                            val a = i / 2; val f = if (a == 1 || a == 3) 1 - g[i] else g[i]
                            if (a < 2) abs(down.position.x - ox - (f * workWidth - dragX) / cw * vw)
                            else abs(down.position.y - oy - (f * workHeight - dragY) / ch * vh)
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
                                val f = if (a < 2) (dragX + (change.position.x - ox) / vw * cw) / workWidth
                                    else (dragY + (change.position.y - oy) / vh * ch) / workHeight
                                move(index, if (a == 1 || a == 3) 1 - f.toDouble() else f.toDouble())
                            }
                        } finally { cropLock = null }
                    }
                }) {
                val vw = minOf(size.width, size.height * workWidth.toFloat() / workHeight)
                val vh = vw * workHeight / workWidth
                val ox = (size.width - vw) / 2; val oy = (size.height - vh) / 2
                clipRect(ox, oy, ox + vw, oy + vh) {
                    withTransform({
                        translate(ox - x * vw / cw, oy - y * vh / ch)
                        scale(vw / cw, vh / ch, pivot = Offset.Zero)
                        translate(workWidth / 2f, workHeight / 2f)
                        rotate(rotation.toFloat(), pivot = Offset.Zero)
                        translate(-bitmap.width / 2f, -bitmap.height / 2f)
                    }) { drawImage(image) }
                }
                if (rotating) {
                    // Screen-space references: only the photo turns beneath the grid.
                    // Constant spacing and centre prevent the guides drifting as padding changes.
                    clipRect(ox, oy, ox + vw, oy + vh) {
                        val spacing = 32.dp.toPx()
                        val centre = Offset(size.width / 2, size.height / 2)
                        fun reference(start: Offset, end: Offset, central: Boolean) {
                            drawLine(Color.Black.copy(alpha = 0.45f), start, end, 2.dp.toPx())
                            drawLine(Color.White.copy(alpha = if (central) 0.85f else 0.5f), start, end,
                                (if (central) 1f else 0.5f).dp.toPx())
                        }
                        val columns = (vw / spacing / 2).toInt() + 1
                        val rows = (vh / spacing / 2).toInt() + 1
                        for (i in -columns..columns) {
                            val gx = centre.x + i * spacing
                            reference(Offset(gx, oy), Offset(gx, oy + vh), i == 0)
                        }
                        for (i in -rows..rows) {
                            val gy = centre.y + i * spacing
                            reference(Offset(ox, gy), Offset(ox + vw, gy), i == 0)
                        }
                    }
                }
                repeat(8) { i ->
                    val a = i / 2; val f = if (a == 1 || a == 3) 1 - guides[i] else guides[i]
                    val line = if (a < 2) ((f * workWidth - x) / cw * vw).toFloat()
                        else ((f * workHeight - y) / ch * vh).toFloat()
                    val start = if (a < 2) Offset(ox + line, oy) else Offset(ox, oy + line)
                    val end = if (a < 2) Offset(ox + line, oy + vh) else Offset(ox + vw, oy + line)
                    val color = if (i == selected) Color.Yellow else if (i % 2 == 0) Color.Cyan else Color(0xFF00E676)
                    drawLine(Color.Black, start, end, 5.dp.toPx()); drawLine(color, start, end, 2.dp.toPx())
                    val handle = if (a < 2) Offset(ox + line.coerceIn(minOf(5.dp.toPx(), vw / 2), vw - minOf(5.dp.toPx(), vw / 2)), oy + vh / 2)
                        else Offset(ox + vw / 2, oy + line.coerceIn(minOf(5.dp.toPx(), vh / 2), vh - minOf(5.dp.toPx(), vh / 2)))
                    if (line >= 0 && line <= if (a < 2) vw else vh) {
                        drawCircle(Color.Black, 6.dp.toPx(), handle); drawCircle(color, 3.dp.toPx(), handle)
                    }
                }
            }
            if (!detail && !fullscreen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    names.forEachIndexed { i, label ->
                        FilterChip(selected = selected / 2 == i, onClick = { selected = i * 2 + selected % 2 }, label = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(R.string.center_outer, R.string.center_inner).forEachIndexed { i, label ->
                        FilterChip(selected = selected % 2 == i, onClick = { selected = selected / 2 * 2 + i }, label = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface) })
                    }
                }
            }
        }
        val min = if (selected % 2 == 0) 0.0 else guides[selected - 1] + 1.0 / dimensions[selected / 2]
        val max = if (selected % 2 == 0) (guides[selected + 1] - 1.0 / dimensions[selected / 2]).coerceAtLeast(0.0) else 0.49
        if (!fullscreen) Slider(value = guides[selected].toFloat(), onValueChange = { move(selected, it.toDouble()) }, valueRange = min.toFloat()..max.toFloat())
        if (!fullscreen) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(-1, 1).forEach { direction ->
                OutlinedButton(onClick = { move(selected, guides[selected] + direction.toDouble() / dimensions[selected / 2]) }) {
                    Text(stringResource(if (direction < 0) R.string.center_minus_pixel else R.string.center_plus_pixel))
                }
            }
        }
        if (!fullscreen) Text(stringResource(R.string.center_manual_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (fullscreen && onSkip != null) TextButton(onClick = onSkip) { Text(stringResource(R.string.pre_skip_frame)) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (fullscreen) listOf(-1, 1).forEach { direction ->
                IconButton(onClick = { move(selected, guides[selected] + direction.toDouble() / dimensions[selected / 2]) }, modifier = Modifier.size(40.dp)) {
                    Icon(if (direction < 0) Icons.Default.Remove else Icons.Default.Add,
                        stringResource(if (direction < 0) R.string.center_minus_pixel else R.string.center_plus_pixel))
                }
            }
            TextButton(onClick = { open = false; onCancel() }) { Text(stringResource(R.string.center_cancel)) }
            Button(modifier = if (fullscreen) Modifier.weight(1f) else Modifier, enabled = result != null, onClick = { result?.let(onApply); open = false }) { Text(stringResource(R.string.center_apply)) }
        }
    }
}
