package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.PreGrader
import com.monkaydee.tcgcatalogue.grade.Wear
import com.monkaydee.tcgcatalogue.grade.ContourCheck

internal fun wearRegionLabel(name: String) = when (name) {
    "TL" -> R.string.wear_top_left
    "TR" -> R.string.wear_top_right
    "BR" -> R.string.wear_bottom_right
    "BL" -> R.string.wear_bottom_left
    "T" -> R.string.wear_top
    "R" -> R.string.wear_right
    "B" -> R.string.wear_bottom
    else -> R.string.wear_left
}

internal fun wearFindingLabel(finding: Wear.Finding) = when (finding) {
    Wear.Finding.NOT_REVIEWED -> R.string.wear_not_reviewed
    Wear.Finding.NO_VISIBLE_DAMAGE -> R.string.wear_observed_clear
    Wear.Finding.WHITENING -> R.string.wear_whitening
    Wear.Finding.CHIP_OR_TEAR -> R.string.wear_chip
    Wear.Finding.BEND_OR_DENT -> R.string.wear_bend
}

/** Eight independently inspected regions; user findings never override a failed photo check. */
@Composable
internal fun WearInspectionPanel(side: PreGrader.Side, label: String, onFinding: (String, Wear.Finding) -> Unit) {
    var expanded by remember(side.card) { mutableStateOf(false) }
    var selected by remember(side.card) { mutableStateOf<String?>(null) }
    val usable = side.usableForWear
    val measured = if (usable) side.wear.zones.values.count { it.evidence == Wear.Evidence.MEASURED } else 0
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.wear_evidence_count, measured), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.wear_review_count, side.wearFindings.values.count { it != Wear.Finding.NOT_REVIEWED }), style = MaterialTheme.typography.bodySmall)
    if (!usable) Text(stringResource(R.string.pre_quality_blocked), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (expanded) R.string.wear_hide_regions else R.string.wear_inspect_regions))
    }
    if (expanded) {
        Text(stringResource(R.string.wear_screening_scope), style = MaterialTheme.typography.bodySmall)
        (Wear.CORNERS + Wear.EDGES).forEach { name ->
            val zone = side.wear.zones[name]
            OutlinedCard(onClick = { selected = name }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WearCrop(side, name, Modifier.size(72.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(wearRegionLabel(name)), style = MaterialTheme.typography.titleSmall)
                        WearEvidence(zone, usable)
                        Text(stringResource(R.string.wear_your_observation, stringResource(wearFindingLabel(side.wearFindings[name] ?: Wear.Finding.NOT_REVIEWED))),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (side.wearFindings[name]?.damage == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    selected?.let { name ->
        Dialog(onDismissRequest = { selected = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$label · ${stringResource(wearRegionLabel(name))}", style = MaterialTheme.typography.titleMedium)
                    WearEvidence(side.wear.zones[name], usable)
                    side.contours[name]?.let { evidence ->
                        Text(when(evidence) {
                            ContourCheck.Evidence.ASYMMETRIC -> "Corner silhouette differs from the other corners. Inspect the cut for rounding, chips or damage."
                            ContourCheck.Evidence.COMPARABLE -> "Corner silhouettes are comparable; this does not prove an undamaged corner."
                            ContourCheck.Evidence.INCONCLUSIVE -> "Corner contour check is inconclusive: border/background contrast or artwork limits it."
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    WearCrop(side, name, Modifier.fillMaxWidth().height(220.dp), zoomable = true)
                    Text(stringResource(R.string.wear_zoom_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.wear_record_observation), style = MaterialTheme.typography.titleSmall)
                    Wear.Finding.entries.forEach { finding ->
                        FilterChip(selected = (side.wearFindings[name] ?: Wear.Finding.NOT_REVIEWED) == finding,
                            onClick = { onFinding(name, finding) },
                            label = { Text(stringResource(wearFindingLabel(finding)), color = MaterialTheme.colorScheme.onSurface) })
                    }
                    Text(stringResource(R.string.wear_observation_scope), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wear_done)) }
                }
            }
        }
    }
}

@Composable
private fun WearEvidence(zone: Wear.Zone?, usable: Boolean) {
    if (!usable || zone == null || zone.evidence == Wear.Evidence.INSUFFICIENT) {
        Text(stringResource(R.string.wear_not_assessed), color = MaterialTheme.colorScheme.error)
        return
    }
    Text(stringResource(if (Wear.possibleDamage(zone)) R.string.wear_possible_damage else if (zone.evidence == Wear.Evidence.MEASURED) R.string.wear_auto_none else R.string.wear_inconclusive),
        style = MaterialTheme.typography.bodySmall)
    if (zone.evidence == Wear.Evidence.LOW_CONTRAST) Text(stringResource(R.string.wear_white_border), style = MaterialTheme.typography.bodySmall)
    if (zone.evidence == Wear.Evidence.TEXTURED) Text(stringResource(R.string.wear_textured_border), style = MaterialTheme.typography.bodySmall)
    if (Wear.possibleDamage(zone)) Text(stringResource(R.string.wear_flagged_pixels, zone.defects * 100, zone.whitening * 100), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun WearCrop(side: PreGrader.Side, name: String, modifier: Modifier, zoomable: Boolean = false) {
    val bitmap = side.card
    var segment by remember(bitmap, name) { mutableIntStateOf(0) }
    var flags by remember(bitmap, name) { mutableStateOf(false) }
    var scale by remember(bitmap, name, segment) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap, name, segment) { mutableStateOf(Offset.Zero) }
    val w = if (zoomable) side.inspectionWidth else bitmap.width
    val h = if (zoomable) side.inspectionHeight else bitmap.height
    val rect = remember(bitmap, name, segment, zoomable) {
        if (!zoomable) {
            val (x0, x1, y0, y1) = Wear.area(name, w, h)
            Rect(x0, y0, x1, y1)
        } else if (name.length == 2) {
            val left = name.endsWith("L"); val top = name.startsWith("T")
            Rect(if (left) 0 else (w * .84).toInt(), if (top) 0 else (h * .86).toInt(), if (left) (w * .16).toInt() else w, if (top) (h * .14).toInt() else h)
        } else {
            val horizontal = name == "T" || name == "B"
            val length = if (horizontal) w else h
            val start = if (segment == 0) 0 else length * (segment - 1) / 4
            val end = if (segment == 0) length else length * segment / 4
            when (name) {
                "T" -> Rect(start, 0, end, (h * .10).toInt())
                "B" -> Rect(start, (h * .90).toInt(), end, h)
                "L" -> Rect(0, start, (w * .14).toInt(), end)
                else -> Rect((w * .86).toInt(), start, w, end)
            }
        }
    }
    val crop by produceState<Bitmap?>(null, bitmap, side.inspectionFile, rect) {
        value = withContext(Dispatchers.IO) {
            val detail = if (zoomable) side.inspectionFile?.let { path -> runCatching {
                val decoder = BitmapRegionDecoder.newInstance(path, false)
                try { decoder.decodeRegion(rect, android.graphics.BitmapFactory.Options()) } finally { decoder.recycle() }
            }.getOrNull() } else null
            detail ?: run {
                val sx = bitmap.width.toDouble() / w; val sy = bitmap.height.toDouble() / h
                val x = (rect.left * sx).toInt().coerceIn(0, bitmap.width - 1)
                val y = (rect.top * sy).toInt().coerceIn(0, bitmap.height - 1)
                Bitmap.createBitmap(bitmap, x, y, ((rect.width() * sx).toInt()).coerceIn(1, bitmap.width - x), ((rect.height() * sy).toInt()).coerceIn(1, bitmap.height - y))
            }
        }
    }
    // Compose may retain a bitmap in a drawing layer after recomposition. Let GC release UI crops.
    Column {
        if (zoomable && name.length == 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (0..4).forEach { part -> TextButton(onClick = { segment = part }) { Text(if (part == 0) "Full" else "$part/4") } }
        }
        Box(modifier.clipToBounds().then(if (zoomable) Modifier.pointerInput(bitmap, name, segment) {
            detectTransformGestures { _, movement, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 12f)
                pan = if (scale == 1f) Offset.Zero else Offset((pan.x + movement.x).coerceIn(-size.width * scale / 2, size.width * scale / 2), (pan.y + movement.y).coerceIn(-size.height * scale / 2, size.height * scale / 2))
            }
        } else Modifier)) {
            crop?.let { image ->
                Box(Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y)) {
                    Image(image.asImageBitmap(), stringResource(wearRegionLabel(name)), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (flags) Canvas(Modifier.fillMaxSize()) {
                        val fit = minOf(size.width / image.width, size.height / image.height)
                        val ox = (size.width - image.width * fit) / 2; val oy = (size.height - image.height * fit) / 2
                        side.wear.zones[name]?.flaggedPoints?.forEach { point ->
                            val x = point.x * w / bitmap.width - rect.left; val y = point.y * h / bitmap.height - rect.top
                            if (x >= 0 && y >= 0 && x < rect.width() && y < rect.height()) drawCircle(Color.Magenta, 3f, Offset(ox + (x * image.width / rect.width() * fit).toFloat(), oy + (y * image.height / rect.height() * fit).toFloat()), style = Stroke(1.5f))
                        }
                    }
                }
            }
        }
        if (zoomable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(1, 4, 8).forEach { z -> TextButton(onClick = { scale = z.toFloat(); pan = Offset.Zero }) { Text("${z}×") } }
            }
            FilterChip(selected = flags, onClick = { flags = !flags }, label = { Text("Show colour flags") })
            Text("Original-photo detail · flags mark colour differences, not confirmed damage", style = MaterialTheme.typography.bodySmall)
        }
    }
}
