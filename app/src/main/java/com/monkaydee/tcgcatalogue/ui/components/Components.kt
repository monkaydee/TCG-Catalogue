package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import com.monkaydee.tcgcatalogue.data.db.Game
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.monkaydee.tcgcatalogue.R

/** One chip per game (plus "Auto"/"All" when [nullLabel] is set), scrolling sideways. */
@Composable
fun GameChips(
    selected: Game?,
    onSelect: (Game?) -> Unit,
    games: List<Game> = Game.entries,
    nullLabel: String? = stringResource(R.string.common_auto),
    colors: SelectableChipColors? = null,
) {
    val options = buildList<SelectorOption<Game?>> {
        if (nullLabel != null) add(SelectorOption(null, nullLabel))
        games.forEach { add(SelectorOption(it, it.short)) }
    }
    AppSelector(stringResource(R.string.design_game), selected, options, onSelect,
        modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Collections)
}

/** Low-resolution URL for list thumbnails (TCGdex serves several sizes). */
fun thumbUrl(url: String?): String? = url?.replace("/high.webp", "/low.webp")?.replace("_hires.png", ".png")

/**
 * Picture addresses to try in turn: the picture itself, the full-size one when a thumbnail fails,
 * and pokemontcg.io's copy of an English TCGdex picture (some TCGdex pictures are missing or fail).
 */
fun imageChain(url: String?, thumb: Boolean): List<String> {
    if (url == null) return emptyList()
    val tcgdex = Regex("assets\\.tcgdex\\.net/en/[^/]+/([^/]+)/([^/]+)/").find(url)
    return listOfNotNull(
        if (thumb) thumbUrl(url) else url,
        url.takeIf { thumb },
        tcgdex?.let { com.monkaydee.tcgcatalogue.data.remote.pokemonTcgImage("${it.groupValues[1]}-${it.groupValues[2]}", large = !thumb) },
    ).distinct()
}

/** A card-shaped placeholder with a soft moving highlight, shown while content loads. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.onSurface, 0.1f)
    val t by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer",
    )
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val w = size.width.coerceAtLeast(1f)
                val band = w * 0.6f
                val x = -band + (w + band) * t
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(x, 0f),
                        end = Offset(x + band, size.height * 0.3f),
                    ),
                )
            },
    )
}

@Composable
fun CardImage(url: String?, modifier: Modifier = Modifier, thumb: Boolean = false) {
    val chain = remember(url, thumb) { imageChain(url, thumb) }
    var attempt by remember(chain) { mutableIntStateOf(0) }
    val model = chain.getOrNull(attempt)
    val shape = RoundedCornerShape(if (thumb) 4.dp else 12.dp)
    // Loading until Coil reports a result; an error leaves the plain background.
    var loading by remember(model) { mutableStateOf(model != null) }
    var loaded by remember(model) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(200), label = "cardFade")
    Box(
        modifier
            .aspectRatio(63f / 88f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (loading) SkeletonBox(Modifier.fillMaxSize(), shape)
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { state ->
                // a failed picture: try the next address of the chain
                if (state is AsyncImagePainter.State.Error && attempt < chain.size - 1) attempt++
                loaded = state is AsyncImagePainter.State.Success
                loading = model != null && (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty)
            },
            modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        )
    }
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, min: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.common_less))
        }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange(value + 1) }) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.common_more))
        }
    }
}

/** A minimal line chart of the portfolio value. */
@Composable
fun ValueChart(values: List<Double>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        fun y(v: Double) = (size.height - ((v - min) / span * size.height * 0.9) - size.height * 0.05).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0f))))
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(line, radius = 4.dp.toPx(), center = Offset(size.width, y(values.last())))
    }
}
