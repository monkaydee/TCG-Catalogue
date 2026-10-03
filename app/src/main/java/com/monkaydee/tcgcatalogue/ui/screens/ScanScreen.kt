package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.FilledTonalButton
import com.monkaydee.tcgcatalogue.data.remote.attempt
import com.monkaydee.tcgcatalogue.scan.PictureSearch
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.scan.CardTextParser
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.scan.CardGuide
import com.monkaydee.tcgcatalogue.scan.ScanFrame
import com.monkaydee.tcgcatalogue.scan.VisualMatcher
import com.monkaydee.tcgcatalogue.scan.ScanHit
import com.monkaydee.tcgcatalogue.scan.TextAnalyzer
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
import com.monkaydee.tcgcatalogue.ui.components.AddRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class ScanState(
    /** null = recognise any enabled game */
    val filter: Game? = null,
    /** Slab label read together with the card, if it is graded. */
    val grade: GradeInfo? = null,
    /** Text read but not yet confirmed by a second frame. */
    val reading: String? = null,
    val loading: Boolean = false,
    val candidates: List<CardCandidate> = emptyList(),
    val message: String? = null,
    val addedCount: Int = 0,
    /** Stack mode: scan card after card; clear ones are added at once, unclear ones wait in [session] for review. */
    val stack: Boolean = false,
    val session: List<SessionItem> = emptyList(),
    /** Bumped for every card added in stack mode, for a haptic tick. */
    val ticks: Int = 0,
    /** A card is in view but no number could be read for a while: offer to find it by its picture. */
    val canFindByPicture: Boolean = false,
)

/** A card scanned in stack mode: added (with its collection row, for undo) or waiting for review. */
data class SessionItem(
    val key: Long,
    val name: String,
    val number: String,
    val imageUrl: String?,
    val rowId: Long? = null,
    val candidates: List<CardCandidate> = emptyList(),
    val grade: GradeInfo? = null,
) {
    val needsReview: Boolean get() = rowId == null
}

class ScanViewModel(private val repo: CardRepository, private val context: android.content.Context) : ViewModel() {
    val state = MutableStateFlow(ScanState())
    private var lastKey: String? = null
    private var streak = 0
    private var cooldownKey: String? = null
    private var cooldownUntil = 0L

    /** Picture of the card from the latest frame that read the same card, for telling alt arts apart. */
    private var lastPicture: android.graphics.Bitmap? = null

    /** Camera frames are only analysed while nothing else is going on. */
    val scanning get() = state.value.let { !it.loading && it.candidates.isEmpty() }

    // Stack mode: the same card is only added again after it left the frame (no double adds while it is held up).
    private var lastAddedKey: String? = null
    private var gapSinceAdd = true
    private var nextSessionKey = 0L
    private var reviewKey: Long? = null

    fun setStack(on: Boolean) = state.update { it.copy(stack = on, message = null) }

    private var enabled: Set<Game> = Game.entries.toSet()

    init {
        viewModelScope.launch {
            repo.settings.flow.collect {
                enabled = it.enabledGames
                repo.prepareIndexes()
            }
        }
    }

    fun setFilter(f: Game?) = state.update { it.copy(filter = f) }

    private var lastHitAt = 0L
    private var lastAnyPicture: android.graphics.Bitmap? = null

    fun onFrame(frame: ScanFrame) {
        if (!scanning) return
        frame.card?.let { lastAnyPicture = it }
        val filter = state.value.filter
        val hit = CardTextParser.parse(frame.cardLines, filter, repo.indexMatchers(enabled))
            ?.takeIf { filter != null || it.game in enabled }
        if (hit == null || hit.key != lastAddedKey) gapSinceAdd = true
        val now0 = System.currentTimeMillis()
        if (hit != null) lastHitAt = now0
        val offer = hit == null && now0 - lastHitAt > 2500 && frame.card != null &&
            (filter?.let { it in PictureSearch.GAMES } ?: enabled.any { it in PictureSearch.GAMES })
        if (offer != state.value.canFindByPicture) state.update { it.copy(canFindByPicture = offer) }
        if (hit == null) return
        if (state.value.stack && hit.key == lastAddedKey && !gapSinceAdd) return
        frame.card?.let { lastPicture = it }
        // A slab label is read with the card (it sits above it); keep it while the same card stays in view.
        val grade = CardTextParser.parseGrade(frame.allLines)
        if (hit.key != lastKey || grade != null) state.update { it.copy(grade = grade ?: it.grade.takeIf { hit.key == lastKey }) }
        // Require two consecutive frames to agree before calling the API, to filter OCR misreads.
        streak = if (hit.key == lastKey) streak + 1 else 1
        lastKey = hit.key
        val now = System.currentTimeMillis()
        if (hit.key == cooldownKey && now < cooldownUntil) return
        state.update { it.copy(reading = describe(hit)) }
        if (streak >= 2) resolve(hit)
    }

    private fun describe(hit: ScanHit) = describeHit(hit) + (state.value.grade?.let { " · ${it.label}" } ?: "")

    private fun resolve(hit: ScanHit) {
        streak = 0
        state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val picture = lastPicture
            val result = runCatching { VisualMatcher.rank(context, picture, repo.resolve(hit), VisualMatcher.Source.CAMERA) }
            val candidates = result.getOrDefault(emptyList())
            cooldownKey = hit.key
            cooldownUntil = System.currentTimeMillis() + 2500
            when {
                result.isFailure -> state.update { it.copy(loading = false, message = AppStrings.get(R.string.scan_lookup_failed)) }
                candidates.isEmpty() -> state.update { it.copy(loading = false, message = AppStrings.get(R.string.scan_no_card_found, describe(hit))) }
                else -> {
                    val s = repo.settings.current()
                    val top = candidates.first()
                    val grade = state.value.grade?.takeIf { it.grader != null && it.grade != null }
                    if (state.value.stack) {
                        stackResult(hit, candidates, grade, s.defaultCondition)
                    } else if (s.quickAdd && repo.isConfident(candidates)) {
                        repo.add(top, top.defaultVariant, 1, s.defaultCondition, grade)
                        cooldownUntil = System.currentTimeMillis() + 4000
                        state.update {
                            it.copy(loading = false, grade = null, message = grade?.let { g -> AppStrings.get(R.string.scan_added_auto_graded, top.name, top.number, g.label) } ?: AppStrings.get(R.string.scan_added_auto, top.name, top.number), addedCount = it.addedCount + 1)
                        }
                    } else {
                        state.update { it.copy(loading = false, candidates = candidates) }
                    }
                }
            }
        }
    }

    /** Stack mode: add a clear match at once, park an unclear one for review, and keep scanning. */
    private suspend fun stackResult(hit: ScanHit, candidates: List<CardCandidate>, grade: GradeInfo?, condition: String) {
        val top = candidates.first()
        val item = SessionItem(nextSessionKey++, top.name, top.number, top.defaultVariant.imageUrl ?: top.imageUrl, grade = grade)
        lastAddedKey = hit.key
        gapSinceAdd = false
        if (repo.isConfident(candidates)) {
            val row = runCatching { repo.add(top, top.defaultVariant, 1, condition, grade) }.getOrNull()
            state.update {
                it.copy(
                    loading = false, grade = null, reading = null,
                    session = listOf(item.copy(rowId = row, candidates = if (row == null) candidates else emptyList())) + it.session,
                    addedCount = it.addedCount + if (row != null) 1 else 0,
                    ticks = it.ticks + 1,
                    message = AppStrings.get(R.string.scan_added_auto, top.name, top.number),
                )
            }
        } else {
            state.update {
                it.copy(
                    loading = false, grade = null, reading = null,
                    session = listOf(item.copy(candidates = candidates)) + it.session,
                    ticks = it.ticks + 1,
                    message = AppStrings.get(R.string.stack_review_later, top.name),
                )
            }
        }
    }

    /** Looks the card in the guide up by its picture alone (for cards whose number can't be read). */
    fun findByPicture() {
        val picture = lastAnyPicture ?: return
        val games = state.value.filter?.let { setOf(it) } ?: enabled
        state.update {
            it.copy(
                loading = true, canFindByPicture = false,
                message = AppStrings.get(if (PictureSearch.isReady(context)) R.string.picture_searching else R.string.picture_preparing),
            )
        }
        viewModelScope.launch {
            val result = attempt {
                val crop = PictureSearch.cardCrop(picture, fromCamera = true)
                repo.candidatesFromPicture(PictureSearch.find(context, crop, games))
            }
            cooldownUntil = System.currentTimeMillis() + 2500
            val found = result.getOrDefault(emptyList())
            state.update {
                when {
                    result.isFailure -> it.copy(loading = false, message = AppStrings.get(R.string.picture_unavailable))
                    found.isEmpty() -> it.copy(loading = false, message = AppStrings.get(R.string.picture_none))
                    else -> it.copy(loading = false, message = null, candidates = found)
                }
            }
        }
    }

    /** Takes back one copy of a card added in stack mode. */
    fun undo(item: SessionItem) {
        val row = item.rowId ?: return removeFromSession(item)
        viewModelScope.launch {
            repo.card(row)?.let { repo.update(it.copy(quantity = it.quantity - 1)) }
            state.update { s -> s.copy(session = s.session.filterNot { it.key == item.key }, addedCount = (s.addedCount - 1).coerceAtLeast(0)) }
        }
    }

    fun removeFromSession(item: SessionItem) = state.update { s -> s.copy(session = s.session.filterNot { it.key == item.key }) }

    /** Opens the add sheet for a card waiting for review. */
    fun review(item: SessionItem) {
        reviewKey = item.key
        state.update { it.copy(candidates = item.candidates, grade = item.grade) }
    }

    fun add(r: AddRequest) {
        val c = r.card
        val qty = r.quantity
        val reviewed = reviewKey
        reviewKey = null
        viewModelScope.launch {
            if (reviewed != null) {
                val row = runCatching { repo.add(r) }.getOrNull()
                state.update { s ->
                    s.copy(
                        candidates = emptyList(), grade = null,
                        session = s.session.map { if (it.key == reviewed) it.copy(rowId = row, name = c.name, number = c.number, candidates = emptyList()) else it },
                        addedCount = s.addedCount + if (row != null) qty else 0,
                    )
                }
                cooldownUntil = System.currentTimeMillis() + 3000
                return@launch
            }
            runCatching { repo.add(r) }
                .onSuccess { state.update { s -> s.copy(candidates = emptyList(), grade = null, message = AppStrings.get(R.string.scan_added_qty, c.name, qty), addedCount = s.addedCount + qty) } }
                .onFailure { state.update { s -> s.copy(candidates = emptyList(), grade = null, message = AppStrings.get(R.string.scan_could_not_save, it.message.toString())) } }
            cooldownUntil = System.currentTimeMillis() + 3000
        }
    }

    fun dismiss() {
        reviewKey = null
        cooldownUntil = System.currentTimeMillis() + 3000
        state.update { it.copy(candidates = emptyList(), reading = null, grade = null) }
    }
}

/** "025/165 · Pikachu", "OP05-060", "DMU 107" */
fun describeHit(hit: ScanHit): String = when (hit) {
    is ScanHit.OnePiece -> hit.code
    is ScanHit.Pokemon -> "${hit.number}/${hit.total}" + (hit.nameGuess?.let { " · $it" } ?: "")
    is ScanHit.Magic -> listOfNotNull(hit.set?.uppercase(), hit.number).joinToString(" ").ifEmpty { hit.nameGuess.orEmpty() }
    is ScanHit.Indexed -> hit.code
}

@Composable
fun ScanScreen(repo: CardRepository, onManual: () -> Unit, onPhotos: () -> Unit) {
    val context = LocalContext.current
    val vm: ScanViewModel = viewModel { ScanViewModel(repo, context.applicationContext) }
    val state by vm.state.collectAsState()
    val settings by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.scan_camera_rationale), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scan_allow_camera)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPhotos) { Text(stringResource(R.string.scan_import_instead)) }
        }
        return
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    // A light tick for every card taken in stack mode, so you can keep your eyes on the cards.
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(state.ticks) {
        if (state.ticks > 0) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(isEnabled = { vm.scanning }, onFrame = vm::onFrame, onCamera = { camera = it })
        CardFrameOverlay()

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GameChips(
                        selected = state.filter,
                        onSelect = vm::setFilter,
                        games = Game.entries.filter { it in settings.enabledGames },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.5f), labelColor = Color.White),
                    )
                }
                FilledTonalIconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) {
                    Icon(if (torch) Icons.Default.FlashOff else Icons.Default.FlashOn, stringResource(R.string.scan_torch))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.stack,
                    onClick = { vm.setStack(!state.stack) },
                    label = { Text(stringResource(R.string.stack_mode)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.5f), labelColor = Color.White),
                )
                if (!state.stack) {
                    FilterChip(
                        selected = settings.quickAdd,
                        onClick = { scope.launch { repo.settings.setQuickAdd(!settings.quickAdd) } },
                        label = { Text(if (settings.quickAdd) stringResource(R.string.scan_quick_add_on) else stringResource(R.string.scan_quick_add_off)) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.5f), labelColor = Color.White),
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
            Text(
                state.message ?: state.reading?.let { stringResource(R.string.scan_reading, it) } ?: stringResource(R.string.scan_hint),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.addedCount > 0) Text(pluralStringResource(R.plurals.scan_added_this_session, state.addedCount, state.addedCount), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            if (state.stack && state.session.isNotEmpty()) StackStrip(state.session, onUndo = vm::undo, onReview = vm::review)
            if (state.canFindByPicture && !state.loading) {
                FilledTonalButton(onClick = vm::findByPicture) {
                    Icon(Icons.Default.ImageSearch, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.picture_find))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPhotos) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.scan_from_photos))
                }
                Button(onClick = onManual) {
                    Icon(Icons.Default.Keyboard, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.scan_type_it_in))
                }
            }
        }
    }

    AddCardSheet(state.candidates, settings, repo, initialGrade = state.grade, onAdd = vm::add, onDismiss = vm::dismiss)
}

@Composable
private fun CameraPreview(isEnabled: () -> Boolean, onFrame: (ScanFrame) -> Unit, onCamera: (Camera) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val enabled by rememberUpdatedState(isEnabled)
    val callback by rememberUpdatedState(onFrame)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { TextAnalyzer({ enabled() }, { frame -> callback(frame) }) }
    val analysisRef = remember { arrayOfNulls<ImageAnalysis>(1) }

    DisposableEffect(Unit) {
        onDispose {
            analysisRef[0]?.clearAnalyzer()
            analyzer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            var bound: Camera? = null
            // Tap to focus (and expose) on that spot, like the phone's camera app.
            view.setOnTouchListener { v, e ->
                if (e.action == android.view.MotionEvent.ACTION_UP) {
                    val point = view.meteringPointFactory.createPoint(e.x, e.y)
                    bound?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                            .build(),
                    )
                    v.performClick()
                }
                true
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            // Bind once the view is laid out, so the analysis frames can be cropped to what the preview shows.
            view.post { future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            // High resolution so the small collector number and set code stay sharp.
                            .setResolutionStrategy(ResolutionStrategy(Size(2560, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer); analysisRef[0] = it }
                runCatching {
                    provider.unbindAll()
                    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                    view.viewPort?.let { group.setViewPort(it) }
                    val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                    bound = camera
                    // Focus on the card guide from the start.
                    val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
                    camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    onCamera(camera)
                }
            }, ContextCompat.getMainExecutor(context)) }
            view
        },
    )
}

/** Draws a card-shaped guide (63×88 mm) in the middle of the preview. */
@Composable
private fun CardFrameOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val guide = CardGuide.rect(size.width, size.height)
        val h = guide.height()
        val cw = guide.width()
        val topLeft = Offset(guide.left, guide.top)
        drawRoundRect(Color.White.copy(alpha = 0.9f), topLeft, GSize(cw, h), CornerRadius(24f), style = Stroke(width = 3.dp.toPx()))
        // Highlight where the numbers are printed.
        val band = h * 0.12f
        drawRoundRect(Color(0x553D5AFE), Offset(topLeft.x, topLeft.y + h - band), GSize(cw, band), CornerRadius(24f))
    }
}

/** The cards taken in stack mode, newest first: added ones can be undone, unclear ones reviewed. */
@Composable
private fun StackStrip(items: List<SessionItem>, onUndo: (SessionItem) -> Unit, onReview: (SessionItem) -> Unit) {
    val review = items.count { it.needsReview }
    if (review > 0) {
        Text(pluralStringResource(R.plurals.stack_to_review, review, review), color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelMedium)
    }
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items.size, key = { items[it].key }) { i ->
            val item = items[i]
            Box(Modifier.size(width = 64.dp, height = 104.dp)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (item.needsReview) Color(0x55FFD54F) else Color.White.copy(alpha = 0.12f))
                        .then(if (item.needsReview) Modifier.clickable { onReview(item) } else Modifier)
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.monkaydee.tcgcatalogue.ui.components.CardImage(item.imageUrl, Modifier.width(48.dp), thumb = true)
                    Text(item.name, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        if (item.needsReview) stringResource(R.string.stack_tap_to_review) else item.number,
                        color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    )
                }
                IconButton(onClick = { onUndo(item) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                    Icon(Icons.Default.Close, stringResource(if (item.needsReview) R.string.stack_discard else R.string.stack_undo), tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
