package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R

private val Swatches = listOf(
    0xFFD32F2F, 0xFFE64A19, 0xFFF57C00, 0xFFFFB300, 0xFFC0A062, 0xFF7CB342,
    0xFF2E7D32, 0xFF00897B, 0xFF00ACC1, 0xFF0277BD, 0xFF3D5AFE, 0xFF5E35B1,
    0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFF212121, 0xFFF5F5F5,
).map { Color(it) }

/**
 * Picks a colour: quick swatches, hue / saturation / brightness, or a hex code. "Theme colour"
 * ([onPick] null) goes back to the colour theme's own colour for that part of the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Color?, themeColor: Color, onPick: (Color?) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV((initial ?: themeColor).toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, sat, value)
    var hex by remember { mutableStateOf(toHex(color)) }

    fun set(c: Color) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        hex = toHex(c)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(initial ?: themeColor))
                    Text("→")
                    Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(color))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Swatches.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { set(c) },
                        )
                    }
                }
                GradientSlider(
                    stringResource(R.string.color_hue), hue / 360f,
                    Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                ) { hue = it * 360f; hex = toHex(Color.hsv(hue, sat, value)) }
                GradientSlider(
                    stringResource(R.string.color_saturation), sat,
                    Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))),
                ) { sat = it; hex = toHex(Color.hsv(hue, sat, value)) }
                GradientSlider(
                    stringResource(R.string.color_brightness), value,
                    Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f))),
                ) { value = it; hex = toHex(Color.hsv(hue, sat, value)) }
                OutlinedTextField(
                    hex,
                    { text ->
                        hex = text
                        parseHex(text)?.let { c ->
                            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
                            hue = hsv[0]
                            sat = hsv[1]
                            value = hsv[2]
                        }
                    },
                    label = { Text(stringResource(R.string.color_hex)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.ui_ok)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.color_theme_default)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            }
        },
    )
}

@Composable
private fun GradientSlider(label: String, value: Float, track: Brush, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(track))
            Slider(
                value = value,
                onValueChange = onChange,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            )
        }
    }
}

private fun toHex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

private fun parseHex(text: String): Color? {
    val h = text.trim().removePrefix("#")
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
