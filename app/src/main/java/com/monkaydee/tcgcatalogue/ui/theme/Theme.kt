package com.monkaydee.tcgcatalogue.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.ColorUtils
import com.monkaydee.tcgcatalogue.data.Area
import com.monkaydee.tcgcatalogue.data.Look
import com.monkaydee.tcgcatalogue.data.Palette
import com.monkaydee.tcgcatalogue.data.ThemeMode

val Gain = Color(0xFF2E7D32)
val Loss = Color(0xFFC62828)

/**
 * The parts of the look that aren't in Material's colour scheme: bar and binder colours, and the
 * user's background pictures. Screens read it through [LocalLook].
 */
@Immutable
data class AppLook(
    val dark: Boolean = false,
    val topBar: Color = Color.Unspecified,
    val onTopBar: Color = Color.Unspecified,
    val bottomBar: Color = Color.Unspecified,
    val onBottomBar: Color = Color.Unspecified,
    val binderPage: Color = Color(0xFF2B2D33),
    val onBinderPage: Color = Color.White,
    val homeImage: String? = null,
    val binderImage: String? = null,
    val imageDim: Float = 0.45f,
)

val LocalLook = staticCompositionLocalOf { AppLook() }

@Composable
fun TcgTheme(look: Look = Look(), content: @Composable () -> Unit) {
    val dark = when (look.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = look.colors[Area.ACCENT]?.let { Color(it) }
    val base = when {
        accent != null -> schemeFrom(accent, dark)
        look.palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        else -> schemeFrom(Color(look.palette.seed), dark)
    }
    val scheme = base.withAreas(look, dark)
    val extra = AppLook(
        dark = dark,
        topBar = look.colors[Area.TOP_BAR]?.let { Color(it) } ?: scheme.surface,
        onTopBar = look.colors[Area.TOP_BAR]?.let { onColor(Color(it)) } ?: scheme.onSurface,
        bottomBar = look.colors[Area.BOTTOM_BAR]?.let { Color(it) } ?: scheme.surfaceContainer,
        onBottomBar = look.colors[Area.BOTTOM_BAR]?.let { onColor(Color(it)) } ?: scheme.onSurfaceVariant,
        binderPage = look.colors[Area.BINDER_PAGE]?.let { Color(it) } ?: if (dark) Color(0xFF2B2D33) else Color(0xFF3A3D45),
        onBinderPage = look.colors[Area.BINDER_PAGE]?.let { onColor(Color(it)) } ?: Color.White,
        homeImage = look.homeImage,
        binderImage = look.binderImage,
        imageDim = look.imageDim,
    )
    CompositionLocalProvider(LocalLook provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

/** Slightly tighter, bolder headings than the defaults, for a cleaner look. */
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Black or white, whichever reads better on [c]. */
fun onColor(c: Color): Color = if (c.luminance() > 0.45f) Color(0xFF111318) else Color.White

/** [c] with its lightness set to [l] and saturation scaled by [s] (HSL). */
private fun tone(c: Color, l: Float, s: Float = 1f, hueShift: Float = 0f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[0] = (hsl[0] + hueShift + 360f) % 360f
    hsl[1] = (hsl[1] * s).coerceIn(0f, 1f)
    hsl[2] = l.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * A full Material colour scheme from one accent colour: tinted neutrals for backgrounds and
 * cards, a related secondary and tertiary, and on-colours with enough contrast.
 */
fun schemeFrom(seed: Color, dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = tone(seed, 0.74f, 0.9f),
        onPrimary = tone(seed, 0.16f),
        primaryContainer = tone(seed, 0.28f, 0.7f),
        onPrimaryContainer = tone(seed, 0.9f),
        secondary = tone(seed, 0.75f, 0.45f, -25f),
        onSecondary = tone(seed, 0.16f, 0.4f, -25f),
        secondaryContainer = tone(seed, 0.27f, 0.35f, -25f),
        onSecondaryContainer = tone(seed, 0.9f, 0.4f, -25f),
        tertiary = tone(seed, 0.76f, 0.6f, 60f),
        onTertiary = tone(seed, 0.16f, 0.5f, 60f),
        tertiaryContainer = tone(seed, 0.28f, 0.45f, 60f),
        onTertiaryContainer = tone(seed, 0.9f, 0.5f, 60f),
        background = tone(seed, 0.07f, 0.18f),
        onBackground = tone(seed, 0.92f, 0.1f),
        surface = tone(seed, 0.07f, 0.18f),
        onSurface = tone(seed, 0.92f, 0.1f),
        surfaceVariant = tone(seed, 0.22f, 0.14f),
        onSurfaceVariant = tone(seed, 0.8f, 0.12f),
        surfaceContainerLowest = tone(seed, 0.05f, 0.18f),
        surfaceContainerLow = tone(seed, 0.09f, 0.16f),
        surfaceContainer = tone(seed, 0.11f, 0.16f),
        surfaceContainerHigh = tone(seed, 0.14f, 0.15f),
        surfaceContainerHighest = tone(seed, 0.17f, 0.14f),
        outline = tone(seed, 0.55f, 0.1f),
        outlineVariant = tone(seed, 0.3f, 0.12f),
        inverseSurface = tone(seed, 0.9f, 0.1f),
        inverseOnSurface = tone(seed, 0.15f, 0.1f),
        inversePrimary = tone(seed, 0.4f),
    )
} else {
    lightColorScheme(
        primary = tone(seed, 0.42f),
        onPrimary = Color.White,
        primaryContainer = tone(seed, 0.9f, 0.85f),
        onPrimaryContainer = tone(seed, 0.14f),
        secondary = tone(seed, 0.4f, 0.4f, -25f),
        onSecondary = Color.White,
        secondaryContainer = tone(seed, 0.9f, 0.4f, -25f),
        onSecondaryContainer = tone(seed, 0.14f, 0.4f, -25f),
        tertiary = tone(seed, 0.4f, 0.6f, 60f),
        onTertiary = Color.White,
        tertiaryContainer = tone(seed, 0.9f, 0.5f, 60f),
        onTertiaryContainer = tone(seed, 0.14f, 0.5f, 60f),
        background = tone(seed, 0.985f, 0.25f),
        onBackground = tone(seed, 0.1f, 0.15f),
        surface = tone(seed, 0.985f, 0.25f),
        onSurface = tone(seed, 0.1f, 0.15f),
        surfaceVariant = tone(seed, 0.91f, 0.18f),
        onSurfaceVariant = tone(seed, 0.3f, 0.12f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tone(seed, 0.965f, 0.22f),
        surfaceContainer = tone(seed, 0.95f, 0.22f),
        surfaceContainerHigh = tone(seed, 0.935f, 0.2f),
        surfaceContainerHighest = tone(seed, 0.92f, 0.2f),
        outline = tone(seed, 0.48f, 0.1f),
        outlineVariant = tone(seed, 0.82f, 0.12f),
        inverseSurface = tone(seed, 0.18f, 0.1f),
        inverseOnSurface = tone(seed, 0.95f, 0.1f),
        inversePrimary = tone(seed, 0.8f),
    )
}

/** The user's own background and card colours on top of the theme. */
private fun ColorScheme.withAreas(look: Look, dark: Boolean): ColorScheme {
    var s = this
    look.colors[Area.BACKGROUND]?.let { Color(it) }?.let { bg ->
        val on = onColor(bg)
        s = s.copy(
            background = bg, onBackground = on, surface = bg, onSurface = on,
            onSurfaceVariant = on.copy(alpha = 0.78f).compositeOver(bg),
            surfaceContainer = shift(bg, dark, 0.03f), surfaceContainerLow = shift(bg, dark, 0.015f),
            surfaceContainerLowest = bg,
        )
    }
    look.colors[Area.CARDS]?.let { Color(it) }?.let { chosen ->
        // Text on cards uses the background's text colour, so a card colour keeps the background's
        // lightness side (a light colour on a light theme) to stay readable.
        val card = if (onColor(chosen) == onColor(s.surface)) chosen else tone(chosen, if (onColor(s.surface) == Color.White) 0.2f else 0.88f)
        s = s.copy(
            surfaceContainerHighest = card, surfaceContainerHigh = card, surfaceVariant = card,
        )
    }
    return s
}

private fun Color.compositeOver(bg: Color): Color = Color(ColorUtils.compositeColors(toArgb(), bg.toArgb()))

/** [c] a little lighter (dark theme) or darker (light theme). */
private fun shift(c: Color, dark: Boolean, by: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[2] = (hsl[2] + if (dark || hsl[2] < 0.5f) by else -by).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}
