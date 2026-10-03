package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * Which page a [PageTurner] shows, and how far a page is turned: [turn] > 0 while the page is
 * being turned over to the next one, < 0 while the previous page is turned back on top of it.
 */
@Stable
class PageTurnState(initialPage: Int) {
    var page by mutableIntStateOf(initialPage)
        internal set
    internal val turn = Animatable(0f)
    internal var pageCount = 0

    val turning: Boolean get() = turn.value != 0f

    internal suspend fun settle(target: Float, durationMillis: Int = 450) {
        val distance = abs(target - turn.value)
        turn.animateTo(target, tween((durationMillis * distance).toInt().coerceAtLeast(120), easing = FastOutSlowInEasing))
        when (target) {
            // Both changes land in the same frame, so the new page never flashes.
            1f -> { page += 1; turn.snapTo(0f) }
            -1f -> { page -= 1; turn.snapTo(0f) }
        }
    }

    /** Turns to the next page with the full animation. */
    suspend fun next() { if (page < pageCount - 1 && !turn.isRunning) settle(1f, 650) }

    suspend fun previous() { if (page > 0 && !turn.isRunning) settle(-1f, 650) }

    /** Jumps without animation (e.g. after the sort order changed). */
    fun jump(to: Int) { page = to.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) }
}

/** A [PageTurnState] that keeps its page across navigation and rotation. */
@Composable
fun rememberPageTurnState(initialPage: Int = 0): PageTurnState =
    rememberSaveable(saver = Saver(save = { it.page }, restore = { PageTurnState(it) })) { PageTurnState(initialPage) }

/**
 * Pages that turn like the pages of a real binder: drag a page to the left and it lifts at the
 * right edge and turns over around the rings on the left, uncovering the next page; drag to the
 * right and the previous page comes back. Released half way, it falls to the nearer side.
 */
@Composable
fun PageTurner(
    state: PageTurnState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    pageBack: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(Color(0xFF26262B))) },
    page: @Composable (Int) -> Unit,
) {
    state.pageCount = pageCount
    // Fewer pages after a filter or grid change: stay on the last one.
    LaunchedEffect(pageCount) { if (state.page > pageCount - 1) state.jump(pageCount - 1) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val count by rememberUpdatedState(pageCount)

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                var drag = 0f
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        // Catching a page that is still moving continues from where it is.
                        drag = -state.turn.value * width
                        scope.launch { state.turn.stop() }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val t = state.turn.value
                        val target = when {
                            t > 0f && (t > 0.35f || v < -800f) -> 1f
                            t < 0f && (t < -0.35f || v > 800f) -> -1f
                            else -> 0f
                        }
                        scope.launch { state.settle(target) }
                    },
                    onDragCancel = { scope.launch { state.settle(0f) } },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    drag += dx
                    val t = when {
                        drag < 0 && state.page < count - 1 -> (-drag / width).coerceIn(0f, 1f)
                        drag > 0 && state.page > 0 -> -(drag / width).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    scope.launchSnap(state, t)
                }
            },
    ) {
        val t = state.turn.value
        val current = state.page
        // The page underneath and the page that is turning, with its angle (0 = flat, -180 = turned over).
        val under: Int?
        val turning: Int?
        val angle: Float
        if (t >= 0f) {
            under = if (t > 0f && current + 1 < pageCount) current + 1 else null
            turning = current.takeIf { pageCount > 0 }
            angle = -180f * t
        } else {
            under = current
            turning = current - 1
            angle = -180f * (1f + t)
        }
        val lifted = abs(angle) / 180f

        if (under != null) {
            Box(Modifier.fillMaxSize()) {
                page(under)
                // The lifted page casts a shadow that fades as it turns away.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.45f * (1f - lifted)),
                            (1f - lifted).coerceIn(0.05f, 1f) to Color.Black.copy(alpha = 0.15f * (1f - lifted)),
                            1f to Color.Transparent,
                        ),
                    ),
                )
            }
        }
        if (turning != null && turning >= 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = angle
                        // Far enough away that the lifted edge never reaches the camera.
                        cameraDistance = 18f * density
                    },
            ) {
                if (angle > -90f) {
                    page(turning)
                    // Light falls off as the page stands up.
                    val shade = sin(Math.toRadians(abs(angle).toDouble())).toFloat()
                    if (shade > 0.01f) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = 0.1f * shade),
                                    1f to Color.Black.copy(alpha = 0.45f * shade),
                                ),
                            ),
                        )
                    }
                } else {
                    // The back of the sheet, mirrored so it reads the right way round.
                    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) { pageBack() }
                }
            }
        }
    }
}

private fun CoroutineScope.launchSnap(state: PageTurnState, t: Float) = launch { state.turn.snapTo(t) }
