package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (nullLabel != null) {
            FilterChip(selected == null, { onSelect(null) }, { Text(nullLabel) }, colors = colors ?: FilterChipDefaults.filterChipColors())
        }
        games.forEach { g ->
            FilterChip(selected == g, { onSelect(g) }, { Text(g.short) }, colors = colors ?: FilterChipDefaults.filterChipColors())
        }
    }
}

/** Low-resolution URL for list thumbnails (TCGdex serves several sizes). */
fun thumbUrl(url: String?): String? = url?.replace("/high.webp", "/low.webp")

@Composable
fun CardImage(url: String?, modifier: Modifier = Modifier, thumb: Boolean = false) {
    Box(
        modifier
            .aspectRatio(63f / 88f)
            .clip(RoundedCornerShape(if (thumb) 4.dp else 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = if (thumb) thumbUrl(url) else url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, min: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.common_less))
        }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange(value + 1) }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_more))
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
