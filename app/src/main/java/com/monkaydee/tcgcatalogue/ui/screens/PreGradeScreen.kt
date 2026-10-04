package com.monkaydee.tcgcatalogue.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.monkaydee.tcgcatalogue.grade.Centering
import com.monkaydee.tcgcatalogue.grade.GradeModel
import com.monkaydee.tcgcatalogue.grade.PhotoCheck
import com.monkaydee.tcgcatalogue.grade.PreGrader
import com.monkaydee.tcgcatalogue.grade.Wear
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

private enum class Step { FRONT, BACK, RESULT }

/**
 * Pre-grading (Standard): photos of the front and the back; centering, corners and edges are
 * measured on the phone and turned into a likely grade range. Surface (scratches, dents) is not
 * checked here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreGradeScreen(title: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.FRONT) }
    var front by remember { mutableStateOf<PreGrader.Side?>(null) }
    var back by remember { mutableStateOf<PreGrader.Side?>(null) }
    var camera by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun accept(outcome: PreGrader.Outcome) {
        busy = false
        when (outcome) {
            PreGrader.Outcome.NoCard -> error = context.getString(R.string.grade_no_card)
            is PreGrader.Outcome.Ok -> {
                error = null
                if (step == Step.FRONT) front = outcome.side else back = outcome.side
            }
        }
    }

    fun analyse(photo: Bitmap, guide: FloatArray?) {
        busy = true
        camera = false
        scope.launch {
            val outcome = withContext(Dispatchers.Default) { runCatching { PreGrader.analyse(photo, guide) }.getOrDefault(PreGrader.Outcome.NoCard) }
            accept(outcome)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val photo = withContext(Dispatchers.IO) { runCatching { PhotoRecognizer.loadSmall(context, uri, maxSide = 3000) }.getOrNull() }
                if (photo == null) { busy = false; error = context.getString(R.string.import_could_not_open_photo) } else analyse(photo, null)
            }
        }
    }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (it) camera = true }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = {
                    Column {
                        Text(stringResource(R.string.grade_title))
                        title?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (camera) camera = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.card_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                camera -> GradeCamera(
                    label = stringResource(if (step == Step.FRONT) R.string.grade_step_front else R.string.grade_step_back),
                    onPhoto = { photo, guide -> analyse(photo, guide) },
                    onError = { camera = false; error = context.getString(R.string.grade_camera_failed) },
                )
                step == Step.RESULT -> GradeResult(front, back, onRedo = { front = null; back = null; step = Step.FRONT })
                else -> {
                    val side = if (step == Step.FRONT) front else back
                    CaptureStep(
                        step = step,
                        side = side,
                        busy = busy,
                        error = error,
                        onCamera = { if (granted) camera = true else permission.launch(Manifest.permission.CAMERA) },
                        onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRetake = { if (step == Step.FRONT) front = null else back = null },
                        onNext = {
                            error = null
                            step = if (step == Step.FRONT) Step.BACK else Step.RESULT
                        },
                        onSkip = if (step == Step.BACK) ({ back = null; step = Step.RESULT }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureStep(
    step: Step,
    side: PreGrader.Side?,
    busy: Boolean,
    error: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRetake: () -> Unit,
    onNext: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (step == Step.FRONT) R.string.grade_step_front else R.string.grade_step_back),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(progress = { if (step == Step.FRONT) 0.33f else 0.66f }, modifier = Modifier.fillMaxWidth())
        when {
            busy -> {
                Spacer(Modifier.height(40.dp))
                CircularProgressIndicator()
                Text(stringResource(R.string.grade_analysing), style = MaterialTheme.typography.bodyMedium)
            }
            side != null -> {
                FlatCard(side, Modifier.fillMaxWidth(0.8f))
                side.problems.forEach { p ->
                    Text(
                        stringResource(
                            when (p) {
                                PhotoCheck.Problem.BLURRY -> R.string.grade_problem_blurry
                                PhotoCheck.Problem.GLARE -> R.string.grade_problem_glare
                                PhotoCheck.Problem.TOO_SMALL -> R.string.grade_problem_small
                            },
                        ),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    )
                }
                Text(stringResource(R.string.grade_check_outline), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetake) { Text(stringResource(R.string.grade_retake)) }
                    Button(onClick = onNext) { Text(stringResource(if (step == Step.FRONT) R.string.grade_next_back else R.string.grade_show_result)) }
                }
            }
            else -> {
                Tips()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.grade_take_photo))
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.grade_choose_photo))
                }
                onSkip?.let { TextButton(onClick = it) { Text(stringResource(R.string.grade_skip_back)) } }
            }
        }
    }
}

@Composable
private fun Tips() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.grade_tips_title), style = MaterialTheme.typography.titleSmall)
            listOf(R.string.grade_tip_sleeve, R.string.grade_tip_background, R.string.grade_tip_light, R.string.grade_tip_straight).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall)
            }
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
                val l = (c.cuts[0] + c.left).toFloat() * sx
                val r = size.width - (c.cuts[1] + c.right).toFloat() * sx
                val t = (c.cuts[2] + c.top).toFloat() * sy
                val b = size.height - (c.cuts[3] + c.bottom).toFloat() * sy
                drawRect(Color(0xFF00E676), Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
            }
            for (name in Wear.EDGES + Wear.CORNERS) {
                val z = side.wear.zones.getValue(name)
                if (GradeModel.zoneLevel(z) < 2) continue
                val (x0, x1, y0, y1) = Wear.area(name, side.card.width, side.card.height)
                drawRect(Color(0xCCFF1744), Offset(x0 * sx, y0 * sy), Size((x1 - x0) * sx, (y1 - y0) * sy), style = Stroke(2.dp.toPx()))
            }
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset.Zero, size, CornerRadius(size.width * 0.05f), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun GradeResult(front: PreGrader.Side?, back: PreGrader.Side?, onRedo: () -> Unit) {
    val estimate = remember(front, back) { GradeModel.estimate(front, back) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Likely grade
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.grade_likely), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (estimate.low == estimate.high) "PSA ${estimate.low}" else "PSA ${estimate.low} – ${estimate.high}",
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.grade_most_likely, estimate.mostLikely, (estimate.probabilities.getValue(estimate.mostLikely) * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                estimate.probabilities.entries.sortedByDescending { it.key }.forEach { (g, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$g", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(5.dp))) {
                            Box(Modifier.fillMaxWidth(p.toFloat().coerceIn(0f, 1f)).height(10.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
                        }
                        Text("${(p * 100).toInt()} %", Modifier.width(48.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(stringResource(R.string.grade_limiting, stringResource(estimate.limiting)), style = MaterialTheme.typography.bodySmall)
            }
        }
        // Centering
        Section(stringResource(R.string.grade_centering)) {
            CenteringLine(stringResource(R.string.grade_front), front?.centering)
            CenteringLine(stringResource(R.string.grade_back), back?.centering)
            HorizontalDivider()
            Centering.Company.entries.forEach { co ->
                Text(
                    stringResource(R.string.grade_centering_allows, co.label, co.bestGrade(front?.centering?.worst, back?.centering?.worst)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        // Corners and edges
        Section(stringResource(R.string.grade_corners_edges)) {
            listOfNotNull(front?.let { stringResource(R.string.grade_front) to it }, back?.let { stringResource(R.string.grade_back) to it }).forEach { (label, side) ->
                Text(label, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    FlatCard(side, Modifier.width(110.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZoneLine(stringResource(R.string.grade_corners), side.wear.corners)
                        ZoneLine(stringResource(R.string.grade_edges), side.wear.edges)
                    }
                }
            }
        }
        Text(stringResource(R.string.grade_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.grade_again)) }
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
private fun CenteringLine(label: String, c: Centering.Result?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            c?.let {
                val lr = it.leftRight
                val tb = it.topBottom
                "%.0f/%.0f · %.0f/%.0f".format(lr, 100 - lr, tb, 100 - tb)
            } ?: stringResource(R.string.grade_not_measurable),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ZoneLine(label: String, zones: List<Wear.Zone>) {
    val levels = zones.map { GradeModel.zoneLevel(it) }
    val worst = levels.maxOrNull() ?: 0
    Text(
        "$label: " + stringResource(
            when (worst) {
                0 -> R.string.grade_zone_clean
                1 -> R.string.grade_zone_light
                2 -> R.string.grade_zone_visible
                else -> R.string.grade_zone_heavy
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (worst >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/** Camera with a card guide; takes a full-resolution photo of what the preview shows. */
@Composable
private fun GradeCamera(label: String, onPhoto: (Bitmap, FloatArray) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val capture = remember { arrayOfNulls<ImageCapture>(1) }
    var viewSize by remember { mutableStateOf(0f to 0f) }
    var taking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val future = ProcessCameraProvider.getInstance(ctx)
                view.post {
                    viewSize = view.width.toFloat() to view.height.toFloat()
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                        capture[0] = ic
                        runCatching {
                            provider.unbindAll()
                            val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(ic)
                            view.viewPort?.let { group.setViewPort(it) }
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                        }.onFailure { onError() }
                    }, ContextCompat.getMainExecutor(context))
                }
                view
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            val g = guideFractions(size.width, size.height)
            drawRoundRect(
                Color.White.copy(alpha = 0.9f), Offset(g[0] * size.width, g[1] * size.height),
                Size((g[2] - g[0]) * size.width, (g[3] - g[1]) * size.height), CornerRadius(20f), style = Stroke(3.dp.toPx()),
            )
        }
        Text(
            label + " · " + stringResource(R.string.grade_fill_guide),
            Modifier.align(Alignment.TopCenter).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White, style = MaterialTheme.typography.bodyMedium,
        )
        FilledIconButton(
            onClick = {
                val ic = capture[0] ?: return@FilledIconButton
                if (taking) return@FilledIconButton
                taking = true
                scope.launch {
                    val photo = takePhoto(ic, executor)
                    taking = false
                    if (photo == null) onError() else onPhoto(photo, guideFractions(viewSize.first, viewSize.second))
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).size(72.dp),
            shape = CircleShape,
        ) {
            if (taking) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White) else Icon(Icons.Default.CameraAlt, stringResource(R.string.grade_take_photo))
        }
    }
}

/** A photo from [ic], upright and cropped to what the preview showed. */
private suspend fun takePhoto(ic: ImageCapture, executor: java.util.concurrent.Executor): Bitmap? = suspendCoroutine { cont ->
    ic.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val result = runCatching {
                val full = image.toBitmap()
                val crop = image.cropRect
                val cropped = Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height())
                val rotation = image.imageInfo.rotationDegrees
                if (rotation == 0) cropped else Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            }.getOrNull()
            image.close()
            cont.resume(result)
        }

        override fun onError(exception: ImageCaptureException) = cont.resume(null)
    })
}
