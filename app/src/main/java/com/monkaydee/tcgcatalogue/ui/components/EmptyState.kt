package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.ui.theme.Spacing

/** What an empty screen is missing; picks the illustration. */
enum class EmptyKind { COLLECTION, SEARCH, OFFLINE, LIST }

/** An empty screen: a friendly illustration, a [title], an explanation and optional [actions]. */
@Composable
fun EmptyIllustration(
    kind: EmptyKind,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyArt(kind, Modifier.size(160.dp))
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.s))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** The illustration alone, drawn on a 100 x 100 grid scaled to the available size. */
@Composable
fun EmptyArt(kind: EmptyKind, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val palette = ArtColors(
        panel = c.primaryContainer,
        onPanel = c.onPrimaryContainer,
        slot = c.surface,
        line = c.outline,
        accent = c.primary,
        spark = c.tertiary,
    )
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val u = size.minDimension / 100f
        when (kind) {
            EmptyKind.COLLECTION -> drawCollection(u, palette)
            EmptyKind.SEARCH -> drawSearch(u, palette)
            EmptyKind.OFFLINE -> drawOffline(u, palette)
            EmptyKind.LIST -> drawList(u, palette)
        }
    }
}

private class ArtColors(
    val panel: Color,
    val onPanel: Color,
    val slot: Color,
    val line: Color,
    val accent: Color,
    val spark: Color,
)

/** A four-pointed sparkle of radius [r] around ([cx], [cy]). */
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

private fun DrawScope.drawCollection(u: Float, p: ArtColors) {
    // The binder
    drawRoundRect(p.panel, Offset(10 * u, 10 * u), Size(80 * u, 80 * u), CornerRadius(8 * u))
    // Rings
    for (y in listOf(26f, 50f, 74f)) {
        drawRoundRect(p.accent, Offset(6 * u, (y - 4) * u), Size(10 * u, 8 * u), CornerRadius(4 * u))
    }
    // Empty slots; the first holds a card
    val slotW = 28f
    val slotH = 32f
    val xs = listOf(24f, 58f)
    val ys = listOf(18f, 54f)
    for ((row, y) in ys.withIndex()) {
        for ((col, x) in xs.withIndex()) {
            val tl = Offset(x * u, y * u)
            val sz = Size(slotW * u, slotH * u)
            drawRoundRect(p.slot, tl, sz, CornerRadius(3 * u))
            if (row == 0 && col == 0) {
                rotate(-8f, Offset((x + slotW / 2) * u, (y + slotH / 2) * u)) {
                    drawRoundRect(p.accent, Offset((x + 3) * u, (y + 3) * u), Size((slotW - 6) * u, (slotH - 6) * u), CornerRadius(3 * u))
                    drawRoundRect(p.slot.copy(alpha = 0.85f), Offset((x + 6) * u, (y + 6) * u), Size((slotW - 12) * u, 14 * u), CornerRadius(1.5f * u))
                    drawRoundRect(p.slot.copy(alpha = 0.6f), Offset((x + 6) * u, (y + 23) * u), Size(12 * u, 2.5f * u), CornerRadius(1.2f * u))
                }
            } else {
                drawRoundRect(
                    p.line.copy(alpha = 0.55f), tl, sz, CornerRadius(3 * u),
                    style = Stroke(1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * u, 3 * u))),
                )
            }
        }
    }
    sparkle(86 * u, 14 * u, 9 * u, p.spark)
    sparkle(12 * u, 92 * u, 4.5f * u, p.spark.copy(alpha = 0.7f))
}

private fun DrawScope.drawSearch(u: Float, p: ArtColors) {
    val center = Offset(42 * u, 42 * u)
    // Lens with an empty card inside
    drawCircle(p.panel, 27 * u, center)
    rotate(10f, center) {
        drawRoundRect(
            p.line.copy(alpha = 0.7f), Offset(32 * u, 29 * u), Size(20 * u, 28 * u), CornerRadius(3 * u),
            style = Stroke(2 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * u, 3 * u))),
        )
    }
    // Rim and handle
    drawCircle(p.accent, 27 * u, center, style = Stroke(6 * u))
    drawLine(p.accent, Offset(62 * u, 62 * u), Offset(86 * u, 86 * u), strokeWidth = 10 * u, cap = StrokeCap.Round)
    sparkle(80 * u, 18 * u, 9 * u, p.spark)
    sparkle(14 * u, 84 * u, 4.5f * u, p.spark.copy(alpha = 0.7f))
}

private fun DrawScope.drawOffline(u: Float, p: ArtColors) {
    // A cloud: three circles on a rounded base
    val cloud = Path().apply {
        addOval(Rect(Offset(20 * u, 44 * u), Size(30 * u, 30 * u)))
        addOval(Rect(Offset(34 * u, 26 * u), Size(40 * u, 40 * u)))
        addOval(Rect(Offset(58 * u, 42 * u), Size(26 * u, 32 * u)))
        addRoundRect(RoundRect(32 * u, 50 * u, 72 * u, 74 * u, CornerRadius(4 * u)))
    }
    drawPath(cloud, p.panel)
    // The slash cuts a gap into the cloud, then draws over it
    drawLine(Color.Black, Offset(20 * u, 20 * u), Offset(82 * u, 84 * u), strokeWidth = 16 * u, cap = StrokeCap.Round, blendMode = BlendMode.Clear)
    drawLine(p.accent, Offset(20 * u, 20 * u), Offset(82 * u, 84 * u), strokeWidth = 7 * u, cap = StrokeCap.Round)
    sparkle(84 * u, 16 * u, 6 * u, p.spark.copy(alpha = 0.8f))
}

private fun DrawScope.drawList(u: Float, p: ArtColors) {
    // A sheet with empty rows and a clip on top
    drawRoundRect(p.panel, Offset(20 * u, 14 * u), Size(60 * u, 76 * u), CornerRadius(8 * u))
    for (i in 0..3) {
        val y = (30 + i * 16) * u
        drawCircle(p.slot, 4.5f * u, Offset(33 * u, y))
        drawRoundRect(p.slot, Offset(43 * u, y - 3 * u), Size((if (i % 2 == 0) 28 else 20) * u, 6 * u), CornerRadius(3 * u))
    }
    drawRoundRect(p.accent, Offset(36 * u, 8 * u), Size(28 * u, 12 * u), CornerRadius(5 * u))
    drawRoundRect(p.panel, Offset(42 * u, 12 * u), Size(16 * u, 4 * u), CornerRadius(2 * u))
    sparkle(84 * u, 30 * u, 8 * u, p.spark)
    sparkle(12 * u, 70 * u, 4.5f * u, p.spark.copy(alpha = 0.7f))
}
