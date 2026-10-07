package com.monkaydee.tcgcatalogue.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
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

class PortfolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val appearance = WidgetAppearanceStore(context).load(appWidgetId)
        val model = runCatching { loadWidgetModel(context) }.getOrElse { emptyWidgetModel() }
        provideContent { Body(model, appearance, appWidgetId) }
    }

    @Composable
    private fun Body(model: WidgetModel, appearance: WidgetAppearance, id: Int) {
        val context = LocalContext.current
        val size = LocalSize.current
        val bitmap = remember(model, appearance, size) {
            WidgetRenderer.render(context, model, appearance, size.width.value.toInt(), size.height.value.toInt())
        }
        Box(GlanceModifier.fillMaxSize().appWidgetBackground()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))) {
            Image(ImageProvider(bitmap), model.description, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Image(ImageProvider(R.drawable.ic_widget_customize), AppStrings.get(R.string.widget_customize),
                    GlanceModifier.size(44.dp).padding(12.dp).clickable(actionStartActivity(
                        Intent(context, WidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))),
                    colorFilter = ColorFilter.tint(androidx.glance.color.ColorProvider(
                        day = androidx.compose.ui.graphics.Color(appearance.textColor), night = androidx.compose.ui.graphics.Color(appearance.textColor))))
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) { runCatching { PortfolioWidget().updateAll(context) } }
    }
}

internal suspend fun loadWidgetModel(context: Context): WidgetModel = withContext(Dispatchers.IO) {
    val repo = (context.applicationContext as TcgApp).repository
    val settings = repo.settings.current()
    val data = PortfolioData.compute(repo.cards.first(), repo.sealed.first(), repo.snapshots.first(), settings,
        LocalDate.now().toEpochDay(), System.currentTimeMillis())
    val change = data.change?.let { change ->
        val sign = if (change >= 0) "+" else "−"
        val percent = data.changePercent?.let { " (%s%.1f%%)".format(if (it >= 0) "+" else "−", abs(it)) }.orEmpty()
        sign + Money.format(abs(change), data.currency) + percent
    }
    val stamp = if (DateUtils.isToday(data.updatedAt)) DateFormat.getTimeFormat(context).format(data.updatedAt)
        else DateUtils.formatDateTime(context, data.updatedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    WidgetModel(
        AppStrings.get(R.string.widget_title),
        if (data.hasKnownValue) Money.format(data.value, data.currency) else "—",
        change, (data.change ?: 0.0) >= 0,
        "${data.cardCount} " + AppStrings.get(R.string.widget_short_cards),
        "${data.sealedCount} " + AppStrings.get(R.string.widget_short_sealed),
        "${data.missingCopies} " + AppStrings.get(R.string.widget_short_missing),
        AppStrings.get(when { !data.hasKnownValue -> R.string.widget_waiting; data.missingCopies > 0 -> R.string.widget_known_only; else -> R.string.widget_all_priced }),
        AppStrings.get(R.string.widget_updated, stamp),
    )
}

internal fun emptyWidgetModel() = WidgetModel(AppStrings.get(R.string.widget_title), "—", null, true,
    "0 " + AppStrings.get(R.string.widget_short_cards), "0 " + AppStrings.get(R.string.widget_short_sealed),
    "0 " + AppStrings.get(R.string.widget_short_missing), AppStrings.get(R.string.widget_waiting), "")

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetAppearanceStore(context).delete(it) }
        super.onDeleted(context, appWidgetIds)
    }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val store = WidgetAppearanceStore(context)
        val appearances = oldWidgetIds.map { store.load(it) }
        oldWidgetIds.forEach(store::delete)
        newWidgetIds.forEachIndexed { index, id -> appearances.getOrNull(index)?.let { store.save(id, it) } }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }
}
