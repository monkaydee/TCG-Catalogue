package com.monkaydee.tcgcatalogue.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val PREFS = "ui_prefs"
private const val KEY_DONE = "onboarding_done"

/** Has the first-launch introduction been finished or skipped? */
fun isOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

fun setOnboardingDone(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
}

private enum class OnboardingPage(val title: Int, val text: Int) {
    SCAN(R.string.onboarding_scan_title, R.string.onboarding_scan_text),
    BINDER(R.string.onboarding_binder_title, R.string.onboarding_binder_text),
    PRICES(R.string.onboarding_prices_title, R.string.onboarding_prices_text),
}

/** Three swipeable intro pages with page dots, "Skip" and "Next" / "Get started". */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = OnboardingPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.size - 1

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    // A Surface so text gets the theme's colour on the background (not black in the dark theme).
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish, enabled = !last, modifier = Modifier.alpha(if (last) 0f else 1f)) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(260.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    OnboardingArt(page, Modifier.size(220.dp))
                }
                Spacer(Modifier.height(Spacing.xl))
                Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.m))
                Text(
                    stringResource(page.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val pageOf = stringResource(R.string.onboarding_page_of, pager.currentPage + 1, pages.size)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.semantics { contentDescription = pageOf },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { i ->
                    val selected = i == pager.currentPage
                    val width = animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
                    val color = animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "dotColor",
                    )
                    Box(Modifier.height(8.dp).width(width.value).clip(CircleShape).background(color.value))
                }
            }
            Button(onClick = {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }) {
                Text(stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next))
            }
        }
    }
    }
}

@Composable
private fun OnboardingArt(page: OnboardingPage, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val p = Palette(c.primary, c.primaryContainer, c.onPrimaryContainer, c.tertiary, c.surface, c.outline)
    Canvas(modifier) {
        val u = size.minDimension / 100f
        when (page) {
            OnboardingPage.SCAN -> drawScan(u, p)
            OnboardingPage.BINDER -> drawBinder(u, p)
            OnboardingPage.PRICES -> drawPrices(u, p)
        }
    }
}

private class Palette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val spark: Color,
    val paper: Color,
    val line: Color,
)

private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float, color: Color) {
    val k = r * 0.18f
    val path = Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx + k, cy - k, cx + r, cy)
        quadraticTo(cx + k, cy + k, cx, cy + r)
        quadraticTo(cx - k, cy + k, cx - r, cy)
        quadraticTo(cx - k, cy - k, cx, cy - r)
        close()
    }
    drawPath(path, color)
}

/** A card inside a camera viewfinder with a scan line. */
private fun DrawScope.drawScan(u: Float, p: Palette) {
    // Viewfinder corners
    val corner = Stroke(width = 4 * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(x, y + dy * 14 * u)
            lineTo(x, y)
            lineTo(x + dx * 14 * u, y)
        }
        drawPath(path, p.accent, style = corner)
    }
    bracket(10 * u, 6 * u, 1f, 1f)
    bracket(90 * u, 6 * u, -1f, 1f)
    bracket(10 * u, 94 * u, 1f, -1f)
    bracket(90 * u, 94 * u, -1f, -1f)
    // The card
    rotate(-8f, Offset(50 * u, 50 * u)) {
        drawRoundRect(p.container, Offset(28 * u, 18 * u), Size(44 * u, 64 * u), CornerRadius(5 * u))
        drawRoundRect(p.paper, Offset(33 * u, 23 * u), Size(34 * u, 30 * u), CornerRadius(3 * u))
        drawRoundRect(p.accent, Offset(33 * u, 58 * u), Size(24 * u, 4 * u), CornerRadius(2 * u))
        drawRoundRect(p.line.copy(alpha = 0.5f), Offset(33 * u, 66 * u), Size(16 * u, 4 * u), CornerRadius(2 * u))
        // Mountains in the artwork
        val art = Path().apply {
            moveTo(33 * u, 53 * u); lineTo(43 * u, 38 * u); lineTo(50 * u, 46 * u); lineTo(55 * u, 41 * u); lineTo(67 * u, 53 * u); close()
        }
        drawPath(art, p.accent.copy(alpha = 0.55f))
    }
    // Scan line
    drawRoundRect(p.spark, Offset(14 * u, 47 * u), Size(72 * u, 4 * u), CornerRadius(2 * u))
    drawRoundRect(p.spark.copy(alpha = 0.18f), Offset(14 * u, 38 * u), Size(72 * u, 9 * u))
    sparkle(80 * u, 24 * u, 7 * u, p.spark)
}

/** A binder with a grid of cards, some slots still empty. */
private fun DrawScope.drawBinder(u: Float, p: Palette) {
    drawRoundRect(p.container, Offset(10 * u, 8 * u), Size(80 * u, 84 * u), CornerRadius(8 * u))
    for (y in listOf(24f, 50f, 76f)) {
        drawRoundRect(p.accent, Offset(5 * u, (y - 4) * u), Size(11 * u, 8 * u), CornerRadius(4 * u))
    }
    val xs = listOf(24f, 45f, 66f)
    val ys = listOf(16f, 41f, 66f)
    val filled = setOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 2)
    for ((r, y) in ys.withIndex()) {
        for ((cIdx, x) in xs.withIndex()) {
            val tl = Offset(x * u, y * u)
            val sz = Size(18 * u, 22 * u)
            if ((cIdx to r) in filled) {
                val shade = when ((cIdx + r) % 3) { 0 -> p.accent; 1 -> p.spark; else -> p.accent.copy(alpha = 0.6f) }
                drawRoundRect(shade, tl, sz, CornerRadius(2.5f * u))
                drawRoundRect(p.paper.copy(alpha = 0.8f), Offset((x + 3) * u, (y + 3) * u), Size(12 * u, 10 * u), CornerRadius(1.5f * u))
            } else {
                drawRoundRect(p.paper.copy(alpha = 0.7f), tl, sz, CornerRadius(2.5f * u))
            }
        }
    }
    sparkle(88 * u, 12 * u, 7 * u, p.spark)
}

/** A rising price curve with a price tag. */
private fun DrawScope.drawPrices(u: Float, p: Palette) {
    // Axes
    drawLine(p.line.copy(alpha = 0.5f), Offset(12 * u, 14 * u), Offset(12 * u, 84 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
    drawLine(p.line.copy(alpha = 0.5f), Offset(12 * u, 84 * u), Offset(90 * u, 84 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
    val pts = listOf(18f to 70f, 32f to 60f, 44f to 66f, 58f to 46f, 70f to 50f, 84f to 26f)
    val line = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
    }
    val area = Path().apply {
        addPath(line)
        lineTo(84 * u, 84 * u)
        lineTo(18 * u, 84 * u)
        close()
    }
    drawPath(area, p.container)
    drawPath(line, p.accent, style = Stroke(width = 4 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    pts.forEach { (x, y) -> drawCircle(p.paper, 3.2f * u, Offset(x * u, y * u)); drawCircle(p.accent, 3.2f * u, Offset(x * u, y * u), style = Stroke(2 * u)) }
    // Price tag at the peak
    rotate(-8f, Offset(70 * u, 14 * u)) {
        drawRoundRect(p.spark, Offset(56 * u, 6 * u), Size(28 * u, 16 * u), CornerRadius(5 * u))
        drawCircle(p.paper, 2 * u, Offset(61 * u, 14 * u))
        drawRoundRect(p.paper, Offset(66 * u, 11 * u), Size(14 * u, 3 * u), CornerRadius(1.5f * u))
        drawRoundRect(p.paper.copy(alpha = 0.7f), Offset(66 * u, 16 * u), Size(9 * u, 3 * u), CornerRadius(1.5f * u))
    }
    sparkle(18 * u, 22 * u, 6 * u, p.spark)
}
