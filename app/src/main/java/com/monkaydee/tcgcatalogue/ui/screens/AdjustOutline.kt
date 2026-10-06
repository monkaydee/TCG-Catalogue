package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.Pt
import com.monkaydee.tcgcatalogue.grade.Quad

/**
 * The photo with the card's outline and a dot on each corner; dragging the dots onto the card's
 * corners fixes an outline the automatic search got wrong. The magnified spot under the finger
 * helps to place a corner exactly.
 */
@Composable
fun AdjustOutline(photo: Bitmap, start: Quad, onCancel: () -> Unit, fullscreen: Boolean = false, applyLabel: String? = null, onApply: (Quad) -> Unit) {
    var corners by remember(start) { mutableStateOf(start.corners) }
    var dragging by remember { mutableIntStateOf(-1) }
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.then(if (fullscreen) Modifier.fillMaxSize() else Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.grade_adjust_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(photo.width.toFloat() / photo.height))) {
            val k = minOf(constraints.maxWidth.toFloat() / photo.width, constraints.maxHeight.toFloat() / photo.height).coerceAtLeast(0.001f)
            val ox = (constraints.maxWidth - photo.width * k) / 2
            val oy = (constraints.maxHeight - photo.height * k) / 2
            val grab = with(LocalDensity.current) { 40.dp.toPx() }
            val image = remember(photo) { photo.asImageBitmap() }
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(
                Modifier.fillMaxSize().pointerInput(k, ox, oy) {
                    detectDragGestures(
                        onDragStart = { at ->
                            val near = corners.withIndex().minByOrNull { (_, c) -> (Offset(ox + c.x.toFloat() * k, oy + c.y.toFloat() * k) - at).getDistance() }
                            dragging = near?.takeIf { (_, c) -> (Offset(ox + c.x.toFloat() * k, oy + c.y.toFloat() * k) - at).getDistance() < grab }?.index ?: -1
                        },
                        onDragEnd = { dragging = -1 },
                        onDragCancel = { dragging = -1 },
                    ) { change, amount ->
                        val i = dragging
                        if (i >= 0) {
                            change.consume()
                            val c = corners[i]
                            val x = (c.x + amount.x / k).coerceIn(0.0, photo.width - 1.0)
                            val y = (c.y + amount.y / k).coerceIn(0.0, photo.height - 1.0)
                            corners = corners.toMutableList().also { it[i] = Pt(x, y) }
                        }
                    }
                },
            ) {
                val pts = corners.map { Offset(ox + it.x.toFloat() * k, oy + it.y.toFloat() * k) }
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, accent, style = Stroke(width = 2.dp.toPx()))
                pts.forEachIndexed { i, p ->
                    drawCircle(Color.Black.copy(alpha = 0.4f), radius = 14.dp.toPx(), center = p)
                    drawCircle(accent, radius = (if (i == dragging) 10 else 7).dp.toPx(), center = p, style = Stroke(width = 2.dp.toPx()))
                }
                // magnifier: the spot under the dragged corner, three times larger, in the opposite top corner
                if (dragging >= 0) {
                    val c = corners[dragging]
                    val size = 110.dp.toPx()
                    val zoom = 3f
                    val left = if (pts[dragging].x < this.size.width / 2) this.size.width - size else 0f
                    val src = size / zoom / k
                    val sx = (c.x - src / 2).toInt().coerceIn(0, (photo.width - src).toInt().coerceAtLeast(0))
                    val sy = (c.y - src / 2).toInt().coerceIn(0, (photo.height - src).toInt().coerceAtLeast(0))
                    drawImage(
                        image,
                        srcOffset = androidx.compose.ui.unit.IntOffset(sx, sy),
                        srcSize = androidx.compose.ui.unit.IntSize(src.toInt().coerceAtLeast(1), src.toInt().coerceAtLeast(1)),
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), 0),
                        dstSize = androidx.compose.ui.unit.IntSize(size.toInt(), size.toInt()),
                    )
                    drawRect(accent, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(size, size), style = Stroke(width = 2.dp.toPx()))
                    // where the corner is inside the magnifier
                    val mx = left + ((c.x - sx) * k * zoom).toFloat()
                    val my = ((c.y - sy) * k * zoom).toFloat()
                    drawLine(accent, Offset(mx - 12, my), Offset(mx + 12, my), strokeWidth = 2f)
                    drawLine(accent, Offset(mx, my - 12), Offset(mx, my + 12), strokeWidth = 2f)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            Button(onClick = { onApply(Quad(corners[0], corners[1], corners[2], corners[3])) }, modifier = Modifier.weight(1f)) { Text(applyLabel ?: stringResource(R.string.grade_adjust_apply)) }
        }
    }
}
