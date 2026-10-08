package com.monkaydee.tcgcatalogue.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.Look
import com.monkaydee.tcgcatalogue.data.ThemeMode
import com.monkaydee.tcgcatalogue.ui.components.ColorPickerDialog
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isWidget = intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)
        if (isWidget && (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
                    AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider != ComponentName(this, PortfolioWidgetReceiver::class.java))) {
            finish(); return
        }
        val store = WidgetAppearanceStore(this)
        setContent {
            var model by remember { mutableStateOf(emptyWidgetModel()) }
            LaunchedEffect(Unit) { runCatching { loadWidgetModel(this@WidgetConfigureActivity) }.onSuccess { model = it } }
            TcgTheme(Look(mode = ThemeMode.DARK)) {
                WidgetConfiguration(store.load(id), model, !isWidget, onCancel = { finish() }) { appearance ->
                    lifecycleScope.launch {
                        store.save(id, appearance)
                        if (isWidget) {
                            runCatching { PortfolioWidget().update(this@WidgetConfigureActivity,
                                GlanceAppWidgetManager(this@WidgetConfigureActivity).getGlanceIdBy(id)) }
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        } else PortfolioWidget.refresh(this@WidgetConfigureActivity)
                        finish()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetConfiguration(initial: WidgetAppearance, model: WidgetModel, defaults: Boolean,
                                 onCancel: () -> Unit, onApply: (WidgetAppearance) -> Unit) {
    var style by rememberSaveable { mutableStateOf(initial.style) }
    var transparent by rememberSaveable { mutableStateOf(initial.transparent) }
    var color by rememberSaveable { mutableIntStateOf(initial.textColor) }
    var choosingColor by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val appearance = WidgetAppearance(style, transparent, color)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.widget_customize)) }, navigationIcon = {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.card_cancel)) }
        })
    }, bottomBar = {
        Surface(shadowElevation = 4.dp) {
            Button(onClick = { saving = true; onApply(appearance) }, enabled = !saving,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.widget_apply))
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.widget_design_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            WidgetStyle.entries.forEach { option ->
                val selected = style == option
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .border(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(20.dp))
                    .clickable { style = option }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, { style = option })
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(option.description), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    WidgetPreview(model, appearance.copy(style = option), Modifier.fillMaxWidth().height(174.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_transparent), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(transparent, { transparent = it })
            }
            OutlinedButton({ choosingColor = true }, Modifier.fillMaxWidth()) {
                Box(Modifier.size(20.dp).background(Color(color), RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.widget_text_color))
                Spacer(Modifier.weight(1f))
                Text("#%06X".format(color and 0xFFFFFF))
            }
            if (defaults) Text(stringResource(R.string.widget_default_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (choosingColor) ColorPickerDialog(stringResource(R.string.widget_text_color), Color(color), Color(WidgetAppearance().textColor),
        onPick = { color = (it ?: Color(WidgetAppearance().textColor)).toArgb(); choosingColor = false },
        onDismiss = { choosingColor = false })
}

@Composable
internal fun WidgetPreview(model: WidgetModel, appearance: WidgetAppearance, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF716477), Color(0xFF374F60), Color(0xFF466A65))))) {
        val bitmap = remember(model, appearance, maxWidth, maxHeight) {
            WidgetRenderer.render(context, model, appearance, maxWidth.value.toInt(), maxHeight.value.toInt())
        }
        Image(bitmap.asImageBitmap(), stringResource(R.string.widget_preview_label), Modifier.fillMaxSize())
    }
}
