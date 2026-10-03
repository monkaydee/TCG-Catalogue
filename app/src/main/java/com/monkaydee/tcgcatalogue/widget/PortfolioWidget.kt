package com.monkaydee.tcgcatalogue.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monkaydee.tcgcatalogue.MainActivity
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.TcgApp
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

/** The texts of one widget rendering, made outside the composition so they follow the app's language. */
private data class WidgetModel(
    val title: String,
    val value: String,
    val change: String?,
    val up: Boolean,
    val cards: String,
    val updated: String,
)

/**
 * "Portfolio" home-screen widget: the collection's total value, its change over the last 30 days,
 * the number of cards and when prices were last refreshed. Small sizes show the value only.
 */
class PortfolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = runCatching { load(context) }.getOrNull()
        provideContent {
            GlanceTheme {
                if (model == null) Body(WidgetModel(AppStrings.get(R.string.widget_title), "–", null, true, "", "")) else Body(model)
            }
        }
    }

    private suspend fun load(context: Context): WidgetModel = withContext(Dispatchers.IO) {
        val repo = (context.applicationContext as TcgApp).repository
        val settings = repo.settings.current()
        val data = PortfolioData.compute(
            cards = repo.cards.first(),
            sealed = repo.sealed.first(),
            snapshots = repo.snapshots.first(),
            settings = settings,
            today = LocalDate.now().toEpochDay(),
            now = System.currentTimeMillis(),
        )
        val change = data.change?.let { c ->
            val sign = if (c >= 0) "+" else "−"
            val percent = data.changePercent?.let { " (%s%.1f%%)".format(if (it >= 0) "+" else "−", abs(it)) }.orEmpty()
            AppStrings.get(R.string.widget_change_30d, sign + Money.format(abs(c), data.currency) + percent)
        }
        WidgetModel(
            title = AppStrings.get(R.string.widget_title),
            value = Money.format(data.value, data.currency),
            change = change ?: AppStrings.get(R.string.widget_no_history),
            up = (data.change ?: 0.0) >= 0,
            cards = AppStrings.get(R.string.widget_cards, data.cardCount),
            updated = AppStrings.get(R.string.widget_updated, stamp(context, data.updatedAt)),
        )
    }

    /** The time for today, the date otherwise. */
    private fun stamp(context: Context, millis: Long): String =
        if (DateUtils.isToday(millis)) DateFormat.getTimeFormat(context).format(millis)
        else DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

    companion object {
        private val SMALL = DpSize(100.dp, 48.dp)
        private val LARGE = DpSize(100.dp, 100.dp)

        private val green = ColorProvider(day = Color(0xFF1B7F3B), night = Color(0xFF6FD28A))
        private val red = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))

        /** Redraws every placed Portfolio widget with the current data (cheap when there is none). */
        suspend fun refresh(context: Context) {
            runCatching { PortfolioWidget().updateAll(context) }
        }
    }

    @Composable
    private fun Body(m: WidgetModel) {
        val large = LocalSize.current.height >= LARGE.height
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (large) Text(m.title, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
            Text(
                m.value,
                maxLines = 1,
                style = TextStyle(color = colors.onSurface, fontSize = if (large) 28.sp else 22.sp, fontWeight = FontWeight.Bold),
            )
            if (large) {
                m.change?.let {
                    Text(it, maxLines = 1, style = TextStyle(color = if (m.up) green else red, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(GlanceModifier.padding(top = 6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.cards, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(m.updated, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp))
                }
            }
        }
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()
}
