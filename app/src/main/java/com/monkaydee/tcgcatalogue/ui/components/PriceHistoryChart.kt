package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.PriceHistory
import com.monkaydee.tcgcatalogue.ui.theme.Gain
import com.monkaydee.tcgcatalogue.ui.theme.Loss
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A card's market price over time, converted to the display currency, with its high and low.
 * Draws nothing when there are fewer than two days of history.
 */
@Composable
fun PriceHistoryCard(history: List<PriceHistory>, s: AppSettings, modifier: Modifier = Modifier) {
    val points = remember(history, s.currency, s.usdToEur) {
        history.sortedBy { it.day }.map { it.day to Money.convert(it.price, it.currency, s.currency, s.usdToEur) }
    }
    if (points.size < 2) return
    val values = points.map { it.second }
    val first = values.first()
    val change = values.last() - first
    val dates = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.history_change, signedMoney(change, s.currency), if (first > 0) change / first * 100 else 0.0),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (change >= 0) Gain else Loss,
                )
            }
            Text(stringResource(R.string.history_max, Money.format(values.max(), s.currency)), style = MaterialTheme.typography.labelSmall)
            PriceLine(points, Modifier.fillMaxWidth().height(120.dp))
            Text(stringResource(R.string.history_min, Money.format(values.min(), s.currency)), style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dates.format(LocalDate.ofEpochDay(points.first().first)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dates.format(LocalDate.ofEpochDay(points.last().first)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The line itself: x follows the calendar (gaps where the refresh didn't run), y the price,
 * with a soft fill below, dotted guides at the high and low and dots on both.
 */
@Composable
private fun PriceLine(points: List<Pair<Long, Double>>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    val gainInk = Gain
    val lossInk = Loss
    Canvas(modifier) {
        val min = points.minOf { it.second }
        val max = points.maxOf { it.second }
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val firstDay = points.first().first
        val days = (points.last().first - firstDay).coerceAtLeast(1)
        val inset = 4.dp.toPx()
        fun x(day: Long) = inset + (day - firstDay).toFloat() / days * (size.width - 2 * inset)
        // A flat price sits in the middle instead of on the bottom edge.
        fun y(v: Double) = if (max == min) size.height / 2 else (inset + (1 - (v - min) / span) * (size.height - 2 * inset)).toFloat()

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        drawLine(guide, Offset(0f, y(max)), Offset(size.width, y(max)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        drawLine(guide, Offset(0f, y(min)), Offset(size.width, y(min)), strokeWidth = 1.dp.toPx(), pathEffect = dash)

        val path = Path()
        points.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
        val fill = Path().apply {
            addPath(path)
            lineTo(x(points.last().first), size.height)
            lineTo(x(firstDay), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.3f), line.copy(alpha = 0f))))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (max > min) {
            val hi = points.maxBy { it.second }
            val lo = points.minBy { it.second }
            drawCircle(gainInk, radius = 3.5.dp.toPx(), center = Offset(x(hi.first), y(hi.second)))
            drawCircle(lossInk, radius = 3.5.dp.toPx(), center = Offset(x(lo.first), y(lo.second)))
        }
        drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(points.last().first), y(points.last().second)))
    }
}
