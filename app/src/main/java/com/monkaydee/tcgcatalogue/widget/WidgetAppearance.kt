package com.monkaydee.tcgcatalogue.widget

import android.content.Context
import androidx.annotation.StringRes
import com.monkaydee.tcgcatalogue.R

enum class WidgetStyle(@StringRes val title: Int, @StringRes val description: Int) {
    VALUE(R.string.widget_style_value, R.string.widget_style_value_desc),
    DASHBOARD(R.string.widget_style_dashboard, R.string.widget_style_dashboard_desc),
    COLLECTOR(R.string.widget_style_collector, R.string.widget_style_collector_desc),
}

data class WidgetAppearance(
    val style: WidgetStyle = WidgetStyle.VALUE,
    val transparent: Boolean = false,
    val textColor: Int = 0xFFF2F7F6.toInt(),
)

/** Each launcher instance has its own appearance. Older widgets follow the default until configured. */
class WidgetAppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget-appearance", Context.MODE_PRIVATE)
    fun load(id: Int): WidgetAppearance {
        val key = if (id > 0 && prefs.contains("$id.style")) id.toString() else "default"
        return WidgetAppearance(
            style = prefs.getString("$key.style", null)?.let { name -> WidgetStyle.entries.firstOrNull { it.name == name } } ?: WidgetStyle.VALUE,
            transparent = prefs.getBoolean("$key.transparent", false),
            textColor = prefs.getInt("$key.text", WidgetAppearance().textColor) or 0xFF000000.toInt(),
        )
    }
    fun save(id: Int, appearance: WidgetAppearance) {
        val key = if (id > 0) id.toString() else "default"
        prefs.edit().putString("$key.style", appearance.style.name)
            .putBoolean("$key.transparent", appearance.transparent)
            .putInt("$key.text", appearance.textColor or 0xFF000000.toInt()).apply()
    }
    fun delete(id: Int) {
        prefs.edit().remove("$id.style").remove("$id.transparent").remove("$id.text").apply()
    }
}
