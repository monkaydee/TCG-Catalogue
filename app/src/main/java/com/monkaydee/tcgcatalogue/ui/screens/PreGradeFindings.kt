package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.DefectFinding
import com.monkaydee.tcgcatalogue.grade.Evidence
import com.monkaydee.tcgcatalogue.grade.EvidenceReport
import com.monkaydee.tcgcatalogue.grade.FindingStatus
import com.monkaydee.tcgcatalogue.grade.PreGradeReportStore
import com.monkaydee.tcgcatalogue.grade.VerifiedGrade
import com.monkaydee.tcgcatalogue.ui.components.ActionStyle
import com.monkaydee.tcgcatalogue.ui.components.AppButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Fixed marker colours: they sit on card photos of any colour, so they do not follow the theme.
private val CONFIRMED = Color(0xFFD32F2F)
private val POSSIBLE = Color(0xFFF9A825)
private val BLIND = Color(0xFF9E9E9E)

private fun statusColor(s: FindingStatus) = when (s) {
    FindingStatus.CONFIRMED -> CONFIRMED
    FindingStatus.POSSIBLE -> POSSIBLE
    FindingStatus.NOT_ASSESSABLE -> BLIND
}

private fun statusLabel(s: FindingStatus) = when (s) {
    FindingStatus.CONFIRMED -> R.string.pre_findings_confirmed
    FindingStatus.POSSIBLE -> R.string.pre_findings_possible
    FindingStatus.NOT_ASSESSABLE -> R.string.pre_findings_blind
}

private val ZONE = mapOf("top_left" to "TL", "top_right" to "TR", "bottom_right" to "BR", "bottom_left" to "BL",
    "top" to "T", "right" to "R", "bottom" to "B", "left" to "L")

private fun locationLabel(d: DefectFinding): Int = ZONE[d.location]?.let(::wearRegionLabel) ?: R.string.pre_whole_surface

/** What kind of finding it is, in the user's language (the exported report carries the measured detail). */
private fun typeLabel(d: DefectFinding): Int = when {
    // "visible" wording only for what the user confirmed; an automatic flag stays a possibility
    d.type.endsWith("whitening") && d.findingStatus == FindingStatus.CONFIRMED -> R.string.wear_whitening
    d.type == "chip_or_tear" -> R.string.wear_chip
    d.type == "bend_or_dent" -> R.string.wear_bend
    d.type == "corner_shape" -> R.string.pre_findings_corner_shape
    d.type == "surface_scratches" -> R.string.pre_findings_surface_scratches
    d.type == "surface_dent_or_crease" -> R.string.pre_findings_surface_dent
    d.type == "surface" -> R.string.pre_findings_surface_blind
    d.findingStatus == FindingStatus.NOT_ASSESSABLE -> R.string.wear_inconclusive
    else -> R.string.wear_possible_damage
}

/**
 * The findings of a pre-grade: the card with a marker on every region that carries one, and the list.
 * Each marker is a stored finding with its measured box; nothing is drawn for effect.
 */
@Composable
internal fun FindingsSection(evidence: EvidenceReport, front: Bitmap?, back: Bitmap?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var side by remember { mutableStateOf("front") }
    var markers by remember { mutableStateOf(true) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                (context.contentResolver.openOutputStream(uri) ?: error("unavailable")).bufferedWriter(Charsets.UTF_8).use { it.write(Evidence.json(evidence)) }
            }.isSuccess }
            Toast.makeText(context, context.getString(if (ok) R.string.pre_quick_exported else R.string.pre_quick_export_failed), Toast.LENGTH_LONG).show()
        }
    }
    val image = if (side == "back") back else front
    val shown = evidence.defects.filter { it.side == side }
    Card(Modifier.fillMaxWidth().testTag("pre_findings")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pre_findings_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.pre_findings_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (back != null) {
                    FilterChip(side == "front", { side = "front" }, { Text(stringResource(R.string.grade_front)) })
                    FilterChip(side == "back", { side = "back" }, { Text(stringResource(R.string.grade_back)) })
                }
                FilterChip(markers, { markers = !markers }, { Text(stringResource(R.string.pre_findings_markers)) }, modifier = Modifier.testTag("pre_findings_markers"))
            }
            if (image != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(220.dp).aspectRatio(image.width.toFloat() / image.height)) {
                    Image(image.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    if (markers) Canvas(Modifier.matchParentSize()) {
                        shown.forEach { d ->
                            val b = d.boundingBox ?: return@forEach
                            // tiny boxes get a minimum size so a single flagged spot stays visible
                            val w = (b.width * size.width).toFloat().coerceAtLeast(10f); val h = (b.height * size.height).toFloat().coerceAtLeast(10f)
                            val x = (b.x * size.width).toFloat().coerceAtMost(size.width - w); val y = (b.y * size.height).toFloat().coerceAtMost(size.height - h)
                            drawRect(statusColor(d.findingStatus), Offset(x, y), Size(w, h),
                                style = Stroke(4f, pathEffect = if (d.findingStatus == FindingStatus.POSSIBLE) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FindingStatus.entries.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(10.dp).background(statusColor(s), CircleShape))
                        Text("${stringResource(statusLabel(s))} ${shown.count { it.findingStatus == s }}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (shown.none { it.findingStatus != FindingStatus.NOT_ASSESSABLE })
                Text(stringResource(R.string.pre_findings_none), style = MaterialTheme.typography.bodySmall)
            shown.sortedBy { it.findingStatus.ordinal }.forEach { d ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).background(statusColor(d.findingStatus), CircleShape))
                    Column {
                        Text("${stringResource(locationLabel(d))} · ${stringResource(statusLabel(d.findingStatus))}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(stringResource(typeLabel(d)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton({ export.launch("CardNavo-pregrade-findings.json") }) { Text(stringResource(R.string.pre_findings_export)) }
        }
    }
}

/** A saved report's findings with its stored photos. */
@Composable
internal fun SavedFindings(store: PreGradeReportStore, id: String, frontFile: File, backFile: File) {
    var loaded by remember(id) { mutableStateOf<Triple<EvidenceReport, Bitmap?, Bitmap?>?>(null) }
    LaunchedEffect(id) {
        loaded = withContext(Dispatchers.IO) {
            store.evidence(id)?.let { Triple(it, BitmapFactory.decodeFile(frontFile.absolutePath), backFile.takeIf(File::isFile)?.let { f -> BitmapFactory.decodeFile(f.absolutePath) }) }
        }
    }
    loaded?.let { (evidence, front, back) -> FindingsSection(evidence, front, back) }
}

/**
 * Entering the grade a card received after it came back from grading. The photos taken before and that
 * grade form a verified sample the owner can export for training (docs/PREGRADING-SYSTEM.md).
 */
@Composable
internal fun VerifiedGradeSection(store: PreGradeReportStore, id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var verified by remember(id) { mutableStateOf(store.verified(id)) }
    var grader by remember(id) { mutableStateOf(verified?.grader ?: "PSA") }
    var grade by remember(id) { mutableStateOf("") }
    var cert by remember(id) { mutableStateOf("") }
    var invalid by remember(id) { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                (context.contentResolver.openOutputStream(uri) ?: error("unavailable")).use { store.exportSample(id, it) }
            }.isSuccess }
            Toast.makeText(context, context.getString(if (ok) R.string.pre_quick_exported else R.string.pre_quick_export_failed), Toast.LENGTH_LONG).show()
        }
    }
    Card(Modifier.fillMaxWidth().testTag("pre_verified")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pre_verified_title), style = MaterialTheme.typography.titleSmall)
            val v = verified
            if (v != null) {
                Text("${v.grader} ${v.grade.toString().removeSuffix(".0")}" + (v.cert?.let { " · #$it" } ?: ""), Modifier.testTag("pre_verified_value"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppButton(stringResource(R.string.pre_verified_export), { export.launch("CardNavo-sample-${id.take(8)}.zip") }, Modifier.fillMaxWidth(), style = ActionStyle.SECONDARY)
                TextButton({ store.setVerified(id, null); verified = null }) { Text(stringResource(R.string.pre_verified_remove)) }
            } else {
                Text(stringResource(R.string.pre_verified_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VerifiedGrade.GRADERS.forEach { g -> FilterChip(grader == g, { grader = g }, { Text(g) }) }
                }
                OutlinedTextField(grade, { grade = it; invalid = false }, Modifier.fillMaxWidth().testTag("pre_verified_grade"), singleLine = true, isError = invalid,
                    label = { Text(stringResource(R.string.pre_verified_grade)) },
                    supportingText = if (invalid) ({ Text(stringResource(R.string.pre_verified_invalid)) }) else null)
                OutlinedTextField(cert, { cert = it; invalid = false }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.pre_verified_cert)) })
                AppButton(stringResource(R.string.pre_verified_save), {
                    val entry = VerifiedGrade.of(grader, grade, cert)
                    if (entry == null) invalid = true else { store.setVerified(id, entry); verified = entry }
                }, Modifier.fillMaxWidth().testTag("pre_verified_save"))
            }
        }
    }
}
