package com.monkaydee.tcgcatalogue.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.monkaydee.tcgcatalogue.scan.Pixels
import java.util.concurrent.TimeUnit
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import com.monkaydee.tcgcatalogue.grade.AutomaticPreGrade
import com.monkaydee.tcgcatalogue.grade.PreGradeReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import com.monkaydee.tcgcatalogue.ui.components.AppButton
import com.monkaydee.tcgcatalogue.ui.components.ActionStyle
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.ui.components.InspectionStages
import com.monkaydee.tcgcatalogue.ui.components.ExpandablePanel
import com.monkaydee.tcgcatalogue.ui.components.StandardButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.monkaydee.tcgcatalogue.ui.components.StandardOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.grade.CenteringPotential
import com.monkaydee.tcgcatalogue.grade.Centering
import com.monkaydee.tcgcatalogue.grade.CenteringRotation
import com.monkaydee.tcgcatalogue.grade.GradeModel
import com.monkaydee.tcgcatalogue.grade.PhotoCheck
import com.monkaydee.tcgcatalogue.grade.PreGrader
import com.monkaydee.tcgcatalogue.grade.Wear
import com.monkaydee.tcgcatalogue.grade.PreGradeEstimate
import com.monkaydee.tcgcatalogue.grade.SurfaceFinding
import com.monkaydee.tcgcatalogue.grade.CaptureQuality
import java.io.File
import com.monkaydee.tcgcatalogue.scan.PhotoRecognizer
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** The card guide on the camera screen, as fractions of the preview (a 63 x 88 card shape). */
private fun guideFractions(viewW: Float, viewH: Float): FloatArray {
    val h = minOf(viewH * 0.78f, viewW * 0.86f / 0.716f)
    val w = h * 0.716f
    val left = (viewW - w) / 2
    val top = (viewH - h) / 2
    return floatArrayOf(left / viewW, top / viewH, (left + w) / viewW, (top + h) / viewH)
}

internal enum class Step { FRONT, BACK, REVEAL, RESULT }

/**
 * Automatic two-photo flow with optional centering adjustments. Until reliable surface assessment
 * is available, the experimental score uses centering only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreGradeScreen(title: String?, onBack: () -> Unit, initialGame: Game? = null) {
    PreGradeFlow(title, onBack, initialGame)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreGradeFlow(title: String?, onBack: () -> Unit, initialGame: Game? = null,
    initialFront: PreGrader.Side? = null, initialBack: PreGrader.Side? = null, initialStep: Step = Step.FRONT) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(initialStep) }
    var game by remember { mutableStateOf(initialGame) }
    var front by remember { mutableStateOf(initialFront) }
    var back by remember { mutableStateOf(initialBack) }
    var camera by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingCentering by remember { mutableStateOf<Step?>(null) }
    var history by remember { mutableStateOf(false) }
    val result = remember(front, back) { AutomaticPreGrade.assess(front, back) }
    val label = title ?: stringResource(R.string.pre_quick_card)
    val report = remember(front, back, game, label) {
        result?.let { PreGradeReport.create(label, game, it, front!!, back) }
    }
    fun finish() { step = if (AutomaticPreGrade.assess(front, back) != null) Step.REVEAL else Step.RESULT }
    fun accept(outcome: PreGrader.Outcome, target: Step) {
        busy = false
        when (outcome) {
            PreGrader.Outcome.NoCard -> error = context.getString(R.string.grade_no_card)
            is PreGrader.Outcome.Ok -> {
                error = null
                // The detector found the real outline; there is no manual confirmation gate.
                val side = outcome.side.copy(outlineConfirmed = true)
                if (target == Step.FRONT) front = side else back = side
                if (side.problems.isEmpty() && side.centering != null) {
                    if (target == Step.FRONT) step = Step.BACK else finish()
                }
            }
        }
    }
    fun analyse(photo: Bitmap, guide: FloatArray?) {
        val target = step
        busy = true; camera = false
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    // Fast flow needs the rectified card, not large inspection files or source re-encoding.
                    val outcome = PreGrader.analyse(photo, guide)
                    if (outcome !is PreGrader.Outcome.Ok || outcome.side.photo !== photo) photo.recycle()
                    outcome
                }.getOrDefault(PreGrader.Outcome.NoCard)
            }
            accept(outcome, target)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val photo = withContext(Dispatchers.IO) { runCatching { PhotoRecognizer.loadSmall(context, uri, maxSide = 4000) }.getOrNull() }
                if (photo == null) { busy = false; error = context.getString(R.string.import_could_not_open_photo) } else analyse(photo, null)
            }
        }
    }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) camera = true else error = context.getString(R.string.grade_camera_failed)
    }
    fun goBack() {
        if (busy) return
        when {
            editingCentering != null -> editingCentering = null
            camera -> camera = false
            history -> history = false
            else -> onBack()
        }
    }
    androidx.activity.compose.BackHandler { goBack() }
    Scaffold(topBar = {
        TopAppBar(colors = appBarColors(), title = {
            Text(stringResource(when {
                editingCentering != null -> R.string.pre_center_screen
                history -> R.string.pre_quick_history
                else -> R.string.grade_title
            }))
        }, navigationIcon = {
            IconButton(onClick = ::goBack, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.card_back)) }
        }, actions = {
            if (!camera && editingCentering == null && step != Step.REVEAL) IconButton(onClick = { history = !history }, enabled = !busy) {
                Icon(Icons.Outlined.History, stringResource(R.string.pre_quick_history))
            }
        })
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                history -> SavedPreGrades()
                camera -> GradeCamera(
                    label = stringResource(if (step == Step.FRONT) R.string.grade_step_front else R.string.grade_step_back),
                    onPhoto = ::analyse,
                    onError = { camera = false; error = context.getString(R.string.grade_camera_failed) },
                )
                editingCentering != null -> {
                    val target = editingCentering!!
                    val side = (if (target == Step.FRONT) front else back)!!
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text(stringResource(if (target == Step.FRONT) R.string.grade_front else R.string.grade_back), style = MaterialTheme.typography.titleMedium)
                        ManualCenteringPanel(side, fullscreen = true, onCancel = { editingCentering = null }, onSkip = { editingCentering = null }, onApply = { c ->
                            val unchanged = side.centering?.samePlacement(c) == true
                            val fixed = side.copy(centering = c, outlineConfirmed = true, manualCentering = side.manualCentering || !unchanged, centeringSkipped = false)
                            val wasMissing = AutomaticPreGrade.assess(front, back) == null
                            if (target == Step.FRONT) front = fixed else back = fixed
                            editingCentering = null
                            if (step == Step.RESULT && wasMissing) finish()
                        })
                    }
                }
                step == Step.REVEAL && result != null -> PreGradeReveal(front!!.card, label, result, { step = Step.RESULT })
                step == Step.RESULT || step == Step.REVEAL -> AutomaticGradeResult(front, back, game, title,
                    onEdit = { editingCentering = it },
                    onRetake = { target -> if (target == Step.FRONT) front = null else back = null; error = null; step = target },
                    onRedo = { front = null; back = null; error = null; step = Step.FRONT }, sessionReport = report)
                else -> QuickCaptureStep(step, if (step == Step.FRONT) front else back, busy, error, game,
                    onGame = { game = it },
                    onCamera = { if (granted) camera = true else permission.launch(Manifest.permission.CAMERA) },
                    onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onEdit = { editingCentering = step },
                    onRetake = { if (step == Step.FRONT) front = null else back = null },
                    onNext = { if (step == Step.FRONT) step = Step.BACK else finish() },
                    onSkip = if (step == Step.BACK) ({ back = null; finish() }) else null)
            }
        }
    }
}

@Composable
private fun QuickCaptureStep(step: Step, side: PreGrader.Side?, busy: Boolean, error: String?, game: Game?,
                             onGame: (Game) -> Unit, onCamera: () -> Unit, onGallery: () -> Unit,
                             onEdit: () -> Unit, onRetake: () -> Unit, onNext: () -> Unit, onSkip: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (step == Step.FRONT) R.string.pre_quick_front else R.string.pre_quick_back),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { if (step == Step.FRONT) .33f else .66f }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.pre_quick_intro), style = MaterialTheme.typography.bodyMedium)
        when {
            busy -> {
                Spacer(Modifier.height(36.dp)); CircularProgressIndicator()
                Text(stringResource(R.string.pre_quick_analyzing))
            }
            side != null -> {
                Image(side.card.asImageBitmap(), null, Modifier.width(180.dp).aspectRatio(.716f))
                if (side.problems.isEmpty()) {
                    Text(stringResource(if (side.centering != null) R.string.pre_quick_ready else R.string.pre_quick_missing))
                    AppButton(stringResource(R.string.pre_quick_adjust), onEdit, Modifier.fillMaxWidth(), style = ActionStyle.SECONDARY)
                }
                side.problems.forEach { p -> Text(stringResource(when (p) {
                    PhotoCheck.Problem.BLURRY -> R.string.grade_problem_blurry
                    PhotoCheck.Problem.GLARE -> R.string.grade_problem_glare
                    PhotoCheck.Problem.TOO_SMALL -> R.string.grade_problem_small
                }), color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(stringResource(R.string.grade_retake), onRetake, Modifier.weight(1f), style = ActionStyle.SECONDARY)
                    AppButton(stringResource(if (step == Step.FRONT) R.string.grade_next_back else R.string.pre_quick_reveal),
                        onNext, Modifier.weight(1f), enabled = side.problems.isEmpty() && side.centering != null)
                }
            }
            else -> {
                if (step == Step.FRONT) GameChips(game, { it?.let(onGame) }, nullLabel = null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AppButton(stringResource(R.string.grade_take_photo), onCamera, Modifier.fillMaxWidth(), Icons.Outlined.CameraAlt)
                AppButton(stringResource(R.string.grade_choose_photo), onGallery, Modifier.fillMaxWidth(), Icons.Outlined.PhotoLibrary, style = ActionStyle.SECONDARY)
                Tips()
            }
        }
        if (!busy) onSkip?.let { TextButton(it) { Text(stringResource(R.string.grade_skip_back)) } }
        Text(stringResource(R.string.pre_quick_surface), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Tips() {
    ExpandablePanel(stringResource(R.string.grade_tips_title)) {
        listOf(R.string.grade_tip_sleeve, R.string.grade_tip_background, R.string.grade_tip_light, R.string.grade_tip_straight).forEach {
            Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The flattened card with the measured borders (green) and worn corners and edges (red) drawn on it. */
@Composable
private fun FlatCard(side: PreGrader.Side, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(side.card.width.toFloat() / side.card.height)) {
        Image(side.card.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / side.card.width
            val sy = size.height / side.card.height
            side.centering?.let { c ->
                val bounds = CenteringRotation.bounds(side.card.width, side.card.height, c.rotationDegrees)
                val l = c.cuts[0] + c.left; val r = bounds.width - c.cuts[1] - c.right
                val t = c.cuts[2] + c.top; val b = bounds.height - c.cuts[3] - c.bottom
                val points = listOf(l to t, r to t, r to b, l to b).map { (x, y) ->
                    val p = CenteringRotation.sourcePoint(x, y, side.card.width, side.card.height, c.rotationDegrees)
                    Offset(p.x.toFloat() * sx, p.y.toFloat() * sy)
                }
                points.indices.forEach { i -> drawLine(Color(0xFF00E676), points[i], points[(i + 1) % 4], 2.dp.toPx()) }
            }
            for (name in Wear.EDGES + Wear.CORNERS) {
                val z = side.wear.zones[name] ?: continue
                if (GradeModel.zoneLevel(z) < 2) continue
                val (x0, x1, y0, y1) = Wear.area(name, side.card.width, side.card.height)
                drawRect(Color(0xCCFF1744), Offset(x0 * sx, y0 * sy), Size((x1 - x0) * sx, (y1 - y0) * sy), style = Stroke(2.dp.toPx()))
            }
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset.Zero, size, CornerRadius(size.width * 0.05f), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
internal fun GradeResult(front: PreGrader.Side?, back: PreGrader.Side?, game: Game?, onGame: (Game) -> Unit, onRedo: () -> Unit, onEdit: (Step) -> Unit, onRetake: (Step) -> Unit = {}, onFinding: (Step, String, Wear.Finding) -> Unit = { _, _, _ -> }, onSurface: (Step, PreGrader.Side) -> Unit = { _, _ -> }, onSurfaceCamera: (Step) -> Unit = {}) {
    val centerUsable = front?.usableForCentering == true && back?.usableForCentering == true
    val potential = CenteringPotential.assess(front?.centering, back?.centering, centerUsable)
    val estimate = remember(front, back) { PreGradeEstimate.assess(front, back) }
    val missing = remember(front, back) { PreGradeEstimate.missing(front, back) }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InspectionStages(if (estimate == null) 2 else 3)
        GameChips(game, { it?.let(onGame) }, nullLabel = null)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.grade_title), style = MaterialTheme.typography.titleMedium)
                if (estimate == null) {
                    Text(stringResource(R.string.pre_more_evidence), style = MaterialTheme.typography.headlineSmall)
                    missing.forEach { Text("• ${preGradeMessage(it)}", style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text(stringResource(R.string.pre_range, estimate.low, estimate.high), style = MaterialTheme.typography.headlineSmall)
                    estimate.reasons.forEach { Text("• ${preGradeMessage(it)}", style = MaterialTheme.typography.bodySmall) }
                }
                Text(stringResource(R.string.pre_range_disclaimer), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pre_potential_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(when (potential) {
                    CenteringPotential.Status.WITHIN_10 -> R.string.pre_potential_10
                    CenteringPotential.Status.BORDERLINE_10 -> R.string.pre_potential_borderline
                    CenteringPotential.Status.BELOW_10 -> R.string.pre_potential_below
                    null -> if (front?.centering != null && back?.centering != null) R.string.pre_quality_blocked else R.string.pre_potential_incomplete
                }), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.pre_potential_scope), style = MaterialTheme.typography.bodySmall)
                listOf(Step.FRONT to front, Step.BACK to back).forEach { (target, side) ->
                    if (side != null) OutlinedButton(onClick = { onEdit(target) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (target == Step.FRONT) R.string.pre_edit_front else R.string.pre_edit_back))
                    }
                    if (side != null && side.problems.isNotEmpty()) TextButton(onClick = { onRetake(target) }) {
                        Text(stringResource(if (target == Step.FRONT) R.string.pre_retake_front else R.string.pre_retake_back))
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            val report = buildString {
                appendLine("CardNavo non-professional pre-grade")
                appendLine(estimate?.let { "Experimental range: ${it.low}–${it.high}/10" } ?: "Estimate withheld: ${missing.joinToString("; ")}")
                for ((label, side) in listOf("Front" to front, "Back" to back)) {
                    appendLine(label)
                    side?.centering?.let { appendLine("Centering: %.1f/%.1f horizontal; %.1f/%.1f vertical".format(it.leftRight, 100-it.leftRight, it.topBottom, 100-it.topBottom)) }
                    side?.wearFindings?.forEach { (region, finding) -> appendLine("$region: $finding") }
                    appendLine("Surface: ${side?.surfaceFinding}; ${side?.surfacePhotos?.size ?: 0} additional views")
                }
                appendLine("Not a grading certificate. Professional grades may differ. Hidden damage and authenticity are not assessed. Range is an unvalidated heuristic.")
            }
            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, report) }, "Share inspection report"))
        }, modifier = Modifier.fillMaxWidth()) { Text("Share inspection report") }
        // Centering
        ExpandablePanel(stringResource(R.string.grade_centering)) {
            CenteringLine(stringResource(R.string.grade_front), front?.centering, limit = 55.0)
            CenteringLine(stringResource(R.string.grade_back), back?.centering, limit = 75.0)
            if (front?.manualCentering == true || back?.manualCentering == true) {
                Text(stringResource(R.string.center_manual_notice), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.grade_centering_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

        }
        ExpandablePanel("Surface · multiple lighting angles") {
            listOf(Step.FRONT to front, Step.BACK to back).forEach { (target, side) ->
                if (side != null) {
                    SurfaceInspectionPanel(side, if (target == Step.FRONT) "Front" else "Back", { onSurfaceCamera(target) }, { onSurface(target, it) })
                    HorizontalDivider()
                }
            }
        }
        // Corners and edges
        Section(stringResource(R.string.grade_corners_edges)) {
            listOf(Step.FRONT to front, Step.BACK to back).forEach { (target, side) ->
                val label = stringResource(if (target == Step.FRONT) R.string.grade_front else R.string.grade_back)
                if (side != null) {
                    WearInspectionPanel(side, label) { name, finding -> onFinding(target, name, finding) }
                    HorizontalDivider()
                } else {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.wear_not_assessed), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(stringResource(R.string.pre_assessment_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppButton(stringResource(R.string.grade_again), onRedo, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CenteringLine(label: String, c: Centering.Result?, limit: Double) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                c?.let {
                    val lr = it.leftRight
                    val tb = it.topBottom
                    "↔ %.1f/%.1f · ↕ %.1f/%.1f".format(lr, 100 - lr, tb, 100 - tb)
                } ?: "–",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
        }
        val good = c != null && c.worst <= limit
        if (c != null && c.rotationDegrees != 0.0) Text(stringResource(R.string.center_rotation_saved, c.rotationDegrees), style = MaterialTheme.typography.bodySmall)
        Text(
            when {
                c == null -> stringResource(R.string.pre_center_missing)
                good -> stringResource(R.string.grade_centering_good)
                else -> stringResource(R.string.grade_centering_off)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (c != null && !good) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}



/** Camera with a card guide; takes a full-resolution photo of what the preview shows. */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GradeCamera(label: String, allowAuto: Boolean = true, onPhoto: (Bitmap, FloatArray) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewRef = remember { arrayOfNulls<PreviewView>(1) }
    val captureRef = remember { arrayOfNulls<ImageCapture>(1) }
    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val useCasesRef = remember { mutableListOf<androidx.camera.core.UseCase>() }
    val cameraRef = remember { arrayOfNulls<Camera>(1) }
    val checker = remember { CaptureQuality() }
    var quality by remember { mutableStateOf<CaptureQuality.Result?>(null) }
    var cardInGuide by remember { mutableStateOf(false) }
    var burst by remember { mutableStateOf(true) }
    var timer by remember { mutableStateOf(false) }
    var exposureLocked by remember { mutableStateOf(false) }
    var exposureSupported by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(0f to 0f) }
    var taking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { providerRef[0]?.unbind(*useCasesRef.toTypedArray()); executor.shutdown() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { point ->
                    val view = previewRef[0] ?: return@detectTapGestures
                    val action = FocusMeteringAction.Builder(view.meteringPointFactory.createPoint(point.x, point.y)).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                    cameraRef[0]?.cameraControl?.startFocusAndMetering(action)
                }
            },
            factory = { ctx ->
                // TextureView preview; grading photos come from the full-resolution ImageCapture use case.
                val view = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewRef[0] = view
                val future = ProcessCameraProvider.getInstance(ctx)
                view.post {
                    viewSize = view.width.toFloat() to view.height.toFloat()
                    future.addListener({
                        if (executor.isShutdown) return@addListener
                        val provider = future.get()
                        providerRef[0] = provider
                        // Capture a sensor-resolution still; the screen preview loses corner detail.
                        val selector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy(
                                    android.util.Size(1920, 1080),
                                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            ).build()
                        val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val stillResolution = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(android.util.Size(4000, 3000), androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
                        val capture = ImageCapture.Builder().setResolutionSelector(stillResolution).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                        captureRef[0] = capture
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(android.util.Size(640, 480)).build()
                        var lastCheck = 0L
                        analysis.setAnalyzer(executor) { image ->
                            try {
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastCheck >= 350 && !taking) {
                                    lastCheck = now
                                    val raw = image.toBitmap()
                                    val rect = android.graphics.Rect(image.cropRect).apply { intersect(0, 0, raw.width, raw.height) }
                                    val frame = Bitmap.createBitmap(raw, rect.left, rect.top, rect.width(), rect.height(), Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true)
                                    val g = guideFractions(frame.width.toFloat(), frame.height.toFloat())
                                    val x = (g[0] * frame.width).toInt(); val y = (g[1] * frame.height).toInt()
                                    val w = ((g[2] - g[0]) * frame.width).toInt().coerceAtLeast(1)
                                    val h = ((g[3] - g[1]) * frame.height).toInt().coerceAtLeast(1)
                                    val px = IntArray(w * h); frame.getPixels(px, 0, w, x, y, w, h)
                                    val result = checker.check(Pixels(w, h, px))
                                    val all = IntArray(frame.width * frame.height); frame.getPixels(all, 0, frame.width, 0, 0, frame.width, frame.height)
                                    val found = com.monkaydee.tcgcatalogue.grade.CardRectifier.findQuadIn(Pixels(frame.width, frame.height, all),
                                        com.monkaydee.tcgcatalogue.grade.Quad(com.monkaydee.tcgcatalogue.grade.Pt(x.toDouble(), y.toDouble()), com.monkaydee.tcgcatalogue.grade.Pt((x+w).toDouble(), y.toDouble()), com.monkaydee.tcgcatalogue.grade.Pt((x+w).toDouble(), (y+h).toDouble()), com.monkaydee.tcgcatalogue.grade.Pt(x.toDouble(), (y+h).toDouble())))
                                    ContextCompat.getMainExecutor(context).execute { quality = result; cardInGuide = found != null }
                                    if (frame !== raw) frame.recycle(); raw.recycle()
                                }
                            } catch (_: Exception) { ContextCompat.getMainExecutor(context).execute { quality = null; cardInGuide = false } }
                            finally { image.close() }
                        }
                        useCasesRef.clear(); useCasesRef.addAll(listOf(preview, capture, analysis))
                        runCatching {
                            provider.unbindAll()
                            val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture).addUseCase(analysis)
                            view.viewPort?.let { group.setViewPort(it) }
                            cameraRef[0] = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                            exposureSupported = runCatching { androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraRef[0]!!.cameraInfo)
                                .getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true }.getOrDefault(false)
                        }.onFailure { onError() }
                    }, ContextCompat.getMainExecutor(context))
                }
                view
            },
        )
        val tilt = rememberTilt()
        val level = tilt.degrees < LEVEL_DEGREES
        val ready = level && quality?.ready == true && cardInGuide
        val status = if (allowAuto && !level) stringResource(R.string.pre_camera_level) else if (allowAuto && !cardInGuide) stringResource(R.string.pre_camera_card) else preGradeMessage(quality?.message ?: "Checking focus and motion…")
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        LaunchedEffect(level) { if (level) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
        Canvas(Modifier.fillMaxSize()) {
            val g = guideFractions(size.width, size.height)
            val frame = if (level) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.9f)
            drawRoundRect(
                frame, Offset(g[0] * size.width, g[1] * size.height),
                Size((g[2] - g[0]) * size.width, (g[3] - g[1]) * size.height), CornerRadius(20f), style = Stroke(3.dp.toPx()),
            )
            // Spirit level: the bubble sits in the ring when the phone is flat above the card.
            val c = Offset(size.width / 2, size.height / 2)
            val r = 28.dp.toPx()
            drawCircle(frame, r, c, style = Stroke(2.dp.toPx()))
            val k = (r * 2.5f).coerceAtMost(size.minDimension / 3)
            val bubble = Offset((c.x + tilt.x * k).coerceIn(c.x - 3 * r, c.x + 3 * r), (c.y - tilt.y * k).coerceIn(c.y - 3 * r, c.y + 3 * r))
            drawCircle(frame.copy(alpha = 0.8f), 9.dp.toPx(), bubble)
        }
        Text(
            label + " · " + stringResource(R.string.grade_fill_guide),
            Modifier.align(Alignment.TopCenter).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White, style = MaterialTheme.typography.bodyMedium,
        )
        fun shoot() {
            if (taking) return
            val view = previewRef[0] ?: return
            val capture = captureRef[0] ?: return
            if (view.width == 0 || view.height == 0) return
            val guide = guideFractions(view.width.toFloat(), view.height.toFloat())
            taking = true
            scope.launch(Dispatchers.Main.immediate) {
                if (timer) kotlinx.coroutines.delay(2000)
                val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
                cameraRef[0]?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(5, TimeUnit.SECONDS).build())
                kotlinx.coroutines.delay(500)
                suspend fun take(): Result<Bitmap> = suspendCoroutine { continuation ->
                    capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val result = runCatching {
                                val full = image.toBitmap()
                                val crop = Rect(image.cropRect).apply { if (!intersect(0, 0, full.width, full.height)) set(0, 0, full.width, full.height) }
                                val frame = Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height(), Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true)
                                if (frame !== full) full.recycle()
                                frame
                            }
                            image.close(); continuation.resume(result)
                        }
                        override fun onError(exception: ImageCaptureException) { continuation.resume(Result.failure(exception)) }
                    })
                }
                var best: Bitmap? = null; var bestScore = Double.NEGATIVE_INFINITY
                repeat(if (burst) 3 else 1) {
                    val photo = take().getOrNull()
                    if (photo != null) {
                        val score = withContext(Dispatchers.Default) {
                            val small = Bitmap.createScaledBitmap(photo, 320, (320L * photo.height / photo.width).toInt().coerceAtLeast(3), true)
                            val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
                            val value = PhotoCheck.sharpness(Pixels(small.width, small.height, pixels))
                            if (small !== photo) small.recycle()
                            value
                        }
                        if (score > bestScore) { best?.recycle(); best = photo; bestScore = score } else photo.recycle()
                    }
                }
                taking = false
                best?.let { onPhoto(it, guide) } ?: onError()
            }
        }
        // Focus, motion, exposure, whole-card detection and tilt must stay acceptable.
        LaunchedEffect(ready, allowAuto, taking) {
            if (allowAuto && ready && !taking) { kotlinx.coroutines.delay(AUTO_SHOT_MS); shoot() }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 106.dp).background(Color.Black.copy(alpha = .7f)).padding(8.dp)) {
            Text(status, color = if (ready) Color.Green else Color.White, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.pre_camera_help), color = Color.White, style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = burst, onClick = { burst = !burst }, label = { Text(stringResource(R.string.pre_best_three)) })
                FilterChip(selected = timer, onClick = { timer = !timer }, label = { Text(stringResource(R.string.pre_timer)) })
                FilterChip(selected = exposureLocked, enabled = exposureSupported && !taking, onClick = {
                    exposureLocked = !exposureLocked
                    cameraRef[0]?.let { camera ->
                        val request = androidx.camera.camera2.interop.Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
                            androidx.camera.camera2.interop.CaptureRequestOptions.Builder().setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_LOCK, exposureLocked).build())
                        request.addListener({ if (runCatching { request.get() }.isFailure) exposureLocked = false }, ContextCompat.getMainExecutor(context))
                    }
                }, label = { Text(stringResource(R.string.pre_exposure_lock)) })
            }
        }
        FilledIconButton(
            onClick = { shoot() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).size(72.dp),
            shape = CircleShape,
        ) {
            if (taking) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White) else Icon(Icons.Outlined.CameraAlt, stringResource(R.string.grade_take_photo))
        }
    }
}

/** Tilt below this many degrees counts as flat (the frame turns green). */
private const val LEVEL_DEGREES = 3.0

/** How long the phone must stay flat before the photo is taken automatically. */
private const val AUTO_SHOT_MS = 900L

/** How far the phone is from lying flat: total angle, and the sideways/forward share of gravity (-1..1). */
private data class Tilt(val degrees: Double, val x: Float, val y: Float)

@Composable
private fun rememberTilt(): Tilt {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Tilt(90.0, 0f, 0f)) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val (x, y, z) = e.values
                val g = kotlin.math.sqrt(x * x + y * y + z * z).takeIf { it > 0.1f } ?: return
                val deg = Math.toDegrees(kotlin.math.acos((kotlin.math.abs(z) / g).toDouble().coerceAtMost(1.0)))
                tilt = Tilt(deg, x / g, y / g)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        else tilt = Tilt(0.0, 0f, 0f) // no sensor: don't show the phone as tilted
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}

@Composable
private fun preGradeMessage(message: String): String {
    val side = when { message.startsWith("Front") -> stringResource(R.string.grade_front); message.startsWith("Back") -> stringResource(R.string.grade_back); else -> null }
    if (side != null) {
        val key = when (message.removePrefix("Front").removePrefix("Back").trim().removePrefix(":").trim()) {
            "photo missing" -> R.string.pre_missing_photo
            "outline needs confirmation" -> R.string.pre_missing_outline
            "photo needs a retake" -> R.string.pre_missing_retake
            "centering incomplete" -> R.string.pre_missing_center
            "review all four corners and four edges" -> R.string.pre_missing_wear
            "add two lighting angles and review the surface" -> R.string.pre_missing_surface
            else -> null
        }
        if (key != null) return stringResource(key, side)
    }
    val key = when (message) {
        "Centering exceeds the published PSA 10 allowance" -> R.string.pre_reason_center
        "Centering is borderline; guide placement matters" -> R.string.pre_reason_borderline
        "Comparable printed frame unavailable; centering is unassessed" -> R.string.pre_reason_frame
        "Visible whitening/scuffing or surface scratches" -> R.string.pre_reason_scuff
        "Structural damage recorded; severity cannot be measured from these photos" -> R.string.pre_reason_structural
        "Some automatic colour checks are inconclusive; estimate uses your observations" -> R.string.pre_reason_inconclusive
        "Automatic anomalies remain despite clear manual observations" -> R.string.pre_reason_flags
        "Corner contour asymmetry needs closer inspection" -> R.string.pre_reason_contour
        "No damage recorded in the supplied views; unseen defects remain possible" -> R.string.pre_reason_clear
        "Level the phone over the card" -> R.string.pre_camera_level
        "Place the entire card inside the guide" -> R.string.pre_camera_card
        "Checking focus and motion…" -> R.string.pre_camera_check
        "Add diffuse light" -> R.string.pre_camera_light
        "Possible glare: change lighting or angle" -> R.string.pre_camera_glare
        "Focus on the card; move slightly farther away" -> R.string.pre_camera_focus
        "Hold still" -> R.string.pre_camera_still
        "Sharp and steady" -> R.string.pre_camera_ready
        else -> null
    }
    return key?.let { stringResource(it) } ?: message
}
