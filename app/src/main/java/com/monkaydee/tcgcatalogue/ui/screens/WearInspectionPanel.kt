package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
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
                    WearCrop(side.card, name, Modifier.size(72.dp))
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
                    WearCrop(side.card, name, Modifier.fillMaxWidth().height(220.dp), zoomable = true)
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
private fun WearCrop(bitmap: Bitmap, name: String, modifier: Modifier, zoomable: Boolean = false) {
    val crop = remember(bitmap, name) {
        val (x0, x1, y0, y1) = Wear.area(name, bitmap.width, bitmap.height)
        Bitmap.createBitmap(bitmap, x0.coerceAtLeast(0), y0.coerceAtLeast(0), (x1 - x0).coerceAtLeast(1), (y1 - y0).coerceAtLeast(1))
    }
    DisposableEffect(crop) { onDispose { if (crop !== bitmap) crop.recycle() } }
    var scale by remember(bitmap, name) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap, name) { mutableStateOf(Offset.Zero) }
    Column {
        Box(modifier.clipToBounds().then(if (zoomable) Modifier.pointerInput(bitmap, name) {
            detectTransformGestures { _, movement, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 12f)
                pan = if (scale == 1f) Offset.Zero else Offset((pan.x + movement.x).coerceIn(-size.width * scale / 2, size.width * scale / 2), (pan.y + movement.y).coerceIn(-size.height * scale / 2, size.height * scale / 2))
            }
        } else Modifier)) {
            Image(crop.asImageBitmap(), stringResource(wearRegionLabel(name)), Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit)
        }
        if (zoomable) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(1, 4, 8).forEach { z -> TextButton(onClick = { scale = z.toFloat(); pan = Offset.Zero }) { Text("${z}×") } }
        }
    }
}
