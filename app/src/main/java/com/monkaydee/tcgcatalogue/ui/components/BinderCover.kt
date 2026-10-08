package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.data.CoverDesign
import com.monkaydee.tcgcatalogue.data.CoverPattern
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

/**
 * A closed binder seen from the front: the spine with its rings on the left and the cover, either a
 * preset [design] or the user's own [image], with a label plate showing [title] and [subtitle].
 * [compact] draws the smaller shelf version.
 */
@Composable
fun BinderCover(design: CoverDesign, image: String?, title: String, subtitle: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    val top = Color(design.top)
    val bottom = Color(design.bottom)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .border(1.dp, Color.Black.copy(alpha = 0.35f), shape),
    ) {
        val picture = image?.let(::File)?.takeIf { it.isFile }
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) { drawPattern(design.pattern) }
        }
        // Spine: a darker band with the ring rivets, and a crease where the cover bends.
        val spine = if (compact) 14.dp else 26.dp
        Box(
            Modifier.width(spine).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.White.copy(alpha = 0.08f)))),
        )
        Canvas(Modifier.width(spine).fillMaxHeight()) {
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawCircle(Color(0xFFB9BDC4), radius = size.width * 0.18f, center = Offset(size.width * 0.5f, size.height * y))
                drawCircle(Color(0xFF6E737B), radius = size.width * 0.1f, center = Offset(size.width * 0.5f, size.height * y))
            }
        }
        // Stitching around the cover.
        Canvas(Modifier.fillMaxSize().padding(start = spine + (if (compact) 4.dp else 8.dp), top = 8.dp, end = 8.dp, bottom = 8.dp)) {
            drawRoundRect(
                Color.White.copy(alpha = 0.28f), cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        if (title.isBlank() && subtitle == null) return@Box
        Column(
            Modifier.fillMaxSize().padding(start = spine + (if (compact) 6.dp else 12.dp), end = if (compact) 6.dp else 12.dp, top = if (compact) 10.dp else 24.dp, bottom = if (compact) 10.dp else 12.dp),
            verticalArrangement = if (compact) Arrangement.Bottom else Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 6.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun DrawScope.drawPattern(pattern: CoverPattern) {
    when (pattern) {
        CoverPattern.LEATHER -> {
            // Fine grain and a soft light from the top left.
            val random = Random(7)
            repeat(900) {
                drawCircle(Color.Black.copy(alpha = 0.07f), radius = 1.2f, center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
            }
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.15f), radius = size.maxDimension * 0.8f))
        }
        CoverPattern.WAVES -> repeat(9) { i ->
            val path = androidx.compose.ui.graphics.Path()
            val base = size.height * (0.15f + i * 0.1f)
            path.moveTo(0f, base)
            var x = 0f
            while (x <= size.width) {
                path.lineTo(x, base + sin(x / size.width * 6.283f * 1.5f + i) * size.height * 0.025f)
                x += 6f
            }
            drawPath(path, Color.White.copy(alpha = 0.07f + 0.01f * i), style = Stroke(width = 2.dp.toPx()))
        }
        CoverPattern.STARS -> {
            val random = Random(42)
            repeat(140) {
                val r = random.nextFloat() * 2.2f + 0.4f
                drawCircle(Color.White.copy(alpha = random.nextFloat() * 0.7f + 0.2f), radius = r, center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
            }
            drawRect(Brush.radialGradient(listOf(Color(0x557A5CFF), Color.Transparent), center = Offset(size.width * 0.7f, size.height * 0.35f), radius = size.maxDimension * 0.5f))
        }
        CoverPattern.FOIL -> {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f)),
                start = Offset(0f, 0f), end = Offset(size.width, size.height)))
            val step = 18.dp.toPx()
            var d = -size.height
            while (d < size.width) {
                drawLine(Color.White.copy(alpha = 0.06f), Offset(d, 0f), Offset(d + size.height, size.height), strokeWidth = 1.dp.toPx())
                d += step
            }
        }
        CoverPattern.CARBON -> {
            val cell = 8.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else cell / 2
                while (x < size.width) {
                    drawRoundRect(Color.White.copy(alpha = 0.05f), topLeft = Offset(x, y), size = Size(cell / 2, cell / 2), cornerRadius = CornerRadius(2f))
                    x += cell
                }
                y += cell / 2; row++
            }
        }
    }
}
