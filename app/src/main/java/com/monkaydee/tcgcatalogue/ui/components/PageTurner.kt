package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which page a [PageTurner] shows, and how far a page is turned: [progress] > 0 while the page is
 * being turned over to the next one, < 0 while the previous page is turned back on top of it.
 */
@Stable
class PageTurnState(initialPage: Int) {
    var page by mutableIntStateOf(initialPage)
        private set

    /** -1..1; read only while drawing, so a turn redraws the pages without rebuilding them. */
    internal var progress by mutableFloatStateOf(0f)
    internal var pageCount = 0

    /** Called once when a turn has finished and the page really changed. */
    internal var onPageChanged: (() -> Unit)? = null
    private var job: Job? = null

    internal fun stop() {
        job?.cancel()
        job = null
    }

    /** Lets the page go: it falls to [target] (-1, 0 or 1) at the finger's [velocity] (per second). */
    internal fun settle(scope: CoroutineScope, target: Float, velocity: Float = 0f, spec: AnimationSpec<Float> = FLING) {
        stop()
        job = scope.launch {
            animate(progress, target, velocity, spec) { value, _ -> progress = value }
            // The new page and the flat turn land in the same frame, so nothing flashes.
            val before = page
            when (target) {
                1f -> page += 1
                -1f -> page -= 1
            }
            progress = 0f
            if (page != before) onPageChanged?.invoke()
        }
    }

    /** Turns to the next page with the full animation. */
    fun next(scope: CoroutineScope) {
        if (page < pageCount - 1 && progress == 0f) settle(scope, 1f, spec = TURN)
    }

    fun previous(scope: CoroutineScope) {
        if (page > 0 && progress == 0f) settle(scope, -1f, spec = TURN)
    }

    /** Jumps without animation (e.g. after the sort order changed). */
    fun jump(to: Int) {
        stop()
        progress = 0f
        page = to.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    private companion object {
        // No bounce: paper doesn't spring back past where it lands.
        val FLING = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 170f, visibilityThreshold = 0.001f)
        val TURN = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 95f, visibilityThreshold = 0.001f)
    }
}

/** A [PageTurnState] that keeps its page across navigation and rotation. */
@Composable
fun rememberPageTurnState(initialPage: Int = 0): PageTurnState =
    rememberSaveable(saver = Saver(save = { it.page }, restore = { PageTurnState(it) })) { PageTurnState(initialPage) }

/**
 * Pages that turn like the pages of a real binder: drag a page to the left and it lifts at the
 * right edge and turns over around the rings on the left, uncovering the next page; drag to the
 * right and the previous page comes back. Let go and it falls to the side it is moving to.
 *
 * The previous, current and next pages stay composed; a turn only changes how they are drawn, so
 * it runs at full frame rate even with 81 cards on a page.
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
    state.onPageChanged = rememberTick()
    // Fewer pages after a filter or grid change: stay on the last one.
    LaunchedEffect(pageCount) { if (state.page > pageCount - 1) state.jump(pageCount - 1) }
    val scope = rememberCoroutineScope()
    val count by rememberUpdatedState(pageCount)
    // Which way a page is turning only changes when a turn starts, not on every frame.
    val backwards by remember { derivedStateOf { state.progress < 0f } }
    val current = state.page

    Box(
        modifier.pointerInput(Unit) {
            var drag = 0f
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    // Catching a page that is still moving continues from where it is.
                    state.stop()
                    drag = -state.progress * size.width
                },
                onDragEnd = {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val velocity = -tracker.calculateVelocity().x / width
                    val t = state.progress
                    val target = when {
                        t > 0f && (velocity > 0.6f || velocity > -0.6f && t > 0.4f) -> 1f
                        t < 0f && (velocity < -0.6f || velocity < 0.6f && t < -0.4f) -> -1f
                        else -> 0f
                    }
                    state.settle(scope, target, velocity)
                },
                onDragCancel = { state.settle(scope, 0f) },
            ) { change, dx ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                drag += dx
                val width = size.width.toFloat().coerceAtLeast(1f)
                state.progress = when {
                    drag < 0 && state.page < count - 1 -> (-drag / width).coerceIn(0f, 1f)
                    drag > 0 && state.page > 0 -> -(drag / width).coerceIn(0f, 1f)
                    else -> 0f
                }
            }
        },
    ) {
        for (i in (current - 1)..(current + 1)) {
            if (i !in 0 until pageCount) continue
            // Turning forward: the current page lifts off the next one. Backward: the previous page
            // comes back over the current one. Other pages wait hidden, ready for the next turn.
            val role = when {
                !backwards && i == current -> Role.TURNING
                !backwards && i == current + 1 -> Role.UNDER
                backwards && i == current - 1 -> Role.TURNING
                backwards && i == current -> Role.UNDER
                else -> Role.HIDDEN
            }
            key(i) {
                Sheet(state, role, pageBack) { page(i) }
            }
        }
    }
}

private enum class Role { TURNING, UNDER, HIDDEN }

/** How far the turning page is turned over: 0 = flat on the right, 1 = flat on the left. */
private fun turned(progress: Float) = if (progress >= 0f) progress else 1f + progress

@Composable
private fun Sheet(state: PageTurnState, role: Role, pageBack: @Composable () -> Unit, content: @Composable () -> Unit) {
    val z = when (role) { Role.TURNING -> 2f; Role.UNDER -> 1f; Role.HIDDEN -> 0f }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(z)
            .graphicsLayer {
                val p = state.progress
                when (role) {
                    Role.HIDDEN -> alpha = 0f
                    // Flat pages need no turning layer; only the moving page is rotated.
                    Role.UNDER -> alpha = if (p == 0f) 0f else 1f
                    Role.TURNING -> {
                        val a = turned(p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -180f * a
                        cameraDistance = 14f * density
                        // Once turned over, the sheet's back fades as it lies down on the left.
                        alpha = if (a <= 0.5f) 1f else 1f - (a - 0.5f) * 1.7f
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val p = state.progress
                if (p == 0f) return@drawWithContent
                val a = turned(p)
                val lift = sin(a * PI).toFloat()
                when (role) {
                    Role.UNDER -> {
                        // The page beneath is in the lifted page's shade, deepest along the edge
                        // the lifted page throws its shadow from.
                        val edge = size.width * cos(a * PI / 2).toFloat().coerceAtLeast(0f)
                        drawRect(Color.Black.copy(alpha = 0.18f * (1f - a)))
                        if (a < 0.5f) {
                            val reach = size.width * 0.22f * lift
                            drawRect(
                                Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = 0.35f * lift),
                                    1f to Color.Transparent,
                                    startX = edge,
                                    endX = edge + reach + 1f,
                                ),
                                topLeft = Offset(edge, 0f),
                                size = Size(reach + 1f, size.height),
                            )
                        }
                    }
                    Role.TURNING -> if (a < 0.5f) {
                        // Light falls off towards the lifted edge, with a soft sheen across the paper.
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.04f * lift),
                                0.55f to Color.White.copy(alpha = 0.07f * lift),
                                1f to Color.Black.copy(alpha = 0.32f * lift),
                            ),
                        )
                    }
                    Role.HIDDEN -> Unit
                }
            },
    ) {
        content()
        if (role == Role.TURNING) {
            // The back of the sheet shows once it is turned past upright (mirrored to read right).
            val showBack by remember(state) { derivedStateOf { turned(state.progress) > 0.5f } }
            if (showBack) {
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) { pageBack() }
            }
        }
    }
}
