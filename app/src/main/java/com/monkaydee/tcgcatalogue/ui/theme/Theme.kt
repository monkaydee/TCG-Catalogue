package com.monkaydee.tcgcatalogue.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(primary = Color(0xFF3D5AFE), secondary = Color(0xFFE53935), tertiary = Color(0xFFFFB300))
private val Dark = darkColorScheme(primary = Color(0xFF8C9EFF), secondary = Color(0xFFEF9A9A), tertiary = Color(0xFFFFD54F))

val Gain = Color(0xFF2E7D32)
val Loss = Color(0xFFC62828)

@Composable
fun TcgTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
