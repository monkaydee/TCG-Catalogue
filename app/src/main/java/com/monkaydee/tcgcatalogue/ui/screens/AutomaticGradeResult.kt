package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.grade.*
import com.monkaydee.tcgcatalogue.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AutomaticGradeResult(front: PreGrader.Side?, back: PreGrader.Side?, game: Game?, title: String?,
                                  onRedo: () -> Unit, onEdit: (Step) -> Unit, onRetake: (Step) -> Unit,
                                  sessionReport: PreGradeReport? = null) {
    val result = remember(front, back) { AutomaticPreGrade.assess(front, back) }
    val label = title ?: stringResource(R.string.pre_quick_card)
    val report = remember(front, back, result, label, game) {
        result?.let { PreGradeReport.create(label, game, it, front!!, back) }
    }
    if (report != null) {
        PreGradeReportPage(sessionReport ?: report, photo = front!!.card, backPhoto = back?.card,
            onEdit = onEdit, onRedo = onRedo)
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.pre_quick_missing), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.pre_quick_missing_hint), style = MaterialTheme.typography.bodyMedium)
        for ((target, side) in listOf(Step.FRONT to front, Step.BACK to back)) {
            if (side != null && side.problems.isEmpty()) AppButton(stringResource(if (target == Step.FRONT) R.string.pre_edit_front else R.string.pre_edit_back),
                { onEdit(target) }, Modifier.fillMaxWidth(), style = ActionStyle.SECONDARY)
            AppButton(stringResource(if (target == Step.FRONT) R.string.pre_retake_front else R.string.pre_retake_back),
                { onRetake(target) }, Modifier.fillMaxWidth(), style = ActionStyle.SECONDARY)
        }
    }
}

@Composable
internal fun PreGradeReportPage(report: PreGradeReport, photo: Bitmap? = null, backPhoto: Bitmap? = null,
                                alreadySaved: Boolean = false, imagePath: String? = null,
                                onEdit: ((Step) -> Unit)? = null, onRedo: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PreGradeReportStore(context) }
    var saved by remember(report.id) { mutableStateOf(alreadySaved || store.contains(report.id)) }
    var saving by remember(report.id) { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            val success = withContext(Dispatchers.IO) { runCatching {
                val out = context.contentResolver.openOutputStream(uri) ?: error("Document unavailable")
                out.bufferedWriter(Charsets.UTF_8).use { it.write(PreGradeCsv.encode(report)) }
            }.isSuccess }
            exporting = false
            Toast.makeText(context, context.getString(if (success) R.string.pre_quick_exported else R.string.pre_quick_export_failed), Toast.LENGTH_LONG).show()
        }
    }
    Scaffold(bottomBar = {
        ReportActions(saved, !saved && !saving && photo != null, !exporting,
            onExport = { export.launch("CardNavo-pregrade-${report.id.take(8)}.csv") },
            onSave = {
                if (!saved && !saving && photo != null) scope.launch {
                    saving = true
                    val success = withContext(Dispatchers.IO) { runCatching { store.save(report, photo, backPhoto) }.isSuccess }
                    saved = success; saving = false
                    if (!success) Toast.makeText(context, context.getString(R.string.pre_quick_save_failed), Toast.LENGTH_LONG).show()
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(report.card, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(stringResource(if (report.scope == AutomaticPreGrade.Scope.CENTERING_ONLY)
                        R.string.pre_quick_center_only else R.string.pre_quick_front_only), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (photo != null) Image(photo.asImageBitmap(), null, Modifier.width(66.dp).aspectRatio(.716f))
                        else if (imagePath != null) CardImage(imagePath, Modifier.width(66.dp))
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                            Text(report.grade.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Text(" / 10", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                        }
                            Text(stringResource(R.string.pre_quick_range, report.low, report.high), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text(stringResource(R.string.pre_quick_why), style = MaterialTheme.typography.titleMedium)
            report.reasons.filter { it != AutomaticPreGrade.Reason.ADJUSTED_GUIDES }.forEach {
                Text(stringResource(reasonText(it)), style = MaterialTheme.typography.bodyMedium)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.grade_centering), style = MaterialTheme.typography.titleSmall)
                    ReportCenteringLine(stringResource(R.string.grade_front), report.front)
                    report.back?.let { ReportCenteringLine(stringResource(R.string.grade_back), it) }
                    if (onEdit != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ onEdit(Step.FRONT) }) { Text(stringResource(R.string.pre_edit_front)) }
                        if (report.back != null) TextButton({ onEdit(Step.BACK) }) { Text(stringResource(R.string.pre_edit_back)) }
                    }
                }
            }
            ExpandablePanel(stringResource(R.string.pre_quick_scanning)) {
                Text(stringResource(R.string.pre_quick_auto_note), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.pre_quick_score_notice), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.pre_quick_disclaimer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            onRedo?.let { TextButton(it, Modifier.fillMaxWidth()) { Text(stringResource(R.string.grade_again)) } }
        }
    }
}

@Composable
private fun ReportActions(saved: Boolean, saveEnabled: Boolean, exportEnabled: Boolean, onExport: () -> Unit, onSave: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        @Composable fun exportButton(modifier: Modifier) {
            AppButton(stringResource(R.string.pre_quick_export), onExport, modifier,
                Icons.Outlined.FileDownload, style = ActionStyle.SECONDARY, enabled = exportEnabled)
        }
        @Composable fun saveButton(modifier: Modifier) {
            AppButton(stringResource(if (saved) R.string.pre_quick_saved else R.string.pre_quick_save), onSave, modifier,
                Icons.Outlined.Save, enabled = saveEnabled)
        }
        if (maxWidth < 300.dp || (maxWidth < 360.dp && LocalDensity.current.fontScale > 1.1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exportButton(Modifier.fillMaxWidth()); saveButton(Modifier.fillMaxWidth())
            }
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            exportButton(Modifier.weight(1f).fillMaxHeight()); saveButton(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ReportCenteringLine(label: String, record: CenteringRecord) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("↔ %.1f/%.1f · ↕ %.1f/%.1f".format(record.leftRight, 100 - record.leftRight, record.topBottom, 100 - record.topBottom),
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

internal fun reasonText(reason: AutomaticPreGrade.Reason): Int = when (reason) {
    AutomaticPreGrade.Reason.CENTERED -> R.string.pre_quick_centered
    AutomaticPreGrade.Reason.FRONT_OFF_CENTER -> R.string.pre_quick_front_off
    AutomaticPreGrade.Reason.BACK_OFF_CENTER -> R.string.pre_quick_back_off
    AutomaticPreGrade.Reason.BORDERLINE -> R.string.pre_quick_borderline
    AutomaticPreGrade.Reason.BACK_MISSING -> R.string.pre_quick_back_missing
    AutomaticPreGrade.Reason.SURFACE_UNAVAILABLE -> R.string.pre_quick_surface
    AutomaticPreGrade.Reason.ADJUSTED_GUIDES -> R.string.pre_quick_adjusted
}

@Composable
internal fun SavedPreGrades() {
    val context = LocalContext.current
    val store = remember { PreGradeReportStore(context) }
    var reports by remember { mutableStateOf<List<PreGradeReport>>(emptyList()) }
    var selected by remember { mutableStateOf<PreGradeReport?>(null) }
    LaunchedEffect(Unit) { reports = withContext(Dispatchers.IO) { store.all() } }
    val report = selected
    androidx.activity.compose.BackHandler(enabled = report != null) { selected = null }
    if (report != null) {
        Column(Modifier.fillMaxSize()) {
            TextButton({ selected = null }) { Text(stringResource(R.string.card_back)) }
            Box(Modifier.weight(1f)) { PreGradeReportPage(report, alreadySaved = true, imagePath = store.image(report).absolutePath) }
        }
    } else if (reports.isEmpty()) Box(Modifier.fillMaxSize().padding(20.dp)) { Text(stringResource(R.string.pre_quick_no_saved)) }
    else LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(reports, key = { it.id }) { item ->
            Card(onClick = { selected = item }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardImage(store.image(item).absolutePath, Modifier.width(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.card, style = MaterialTheme.typography.titleSmall)
                        Text(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.createdAt)),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(if (item.scope == AutomaticPreGrade.Scope.CENTERING_ONLY) R.string.pre_quick_center_only else R.string.pre_quick_front_only),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${item.grade}/10", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
