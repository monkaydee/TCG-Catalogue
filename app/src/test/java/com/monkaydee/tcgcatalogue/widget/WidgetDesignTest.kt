package com.monkaydee.tcgcatalogue.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetDesignTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val model get() = WidgetModel("Collection value", "€658.15", null, true, "20 Cards", "3 Sealed", "6 Unpriced", "Known values only", "Updated 19:26")

    @Test fun eachPlacedWidgetRetainsIndependentAppearanceAndDefaults() {
        val store = WidgetAppearanceStore(context)
        val defaults = WidgetAppearance(WidgetStyle.COLLECTOR, true, Color.BLACK)
        val one = WidgetAppearance(WidgetStyle.VALUE, false, Color.WHITE)
        val two = WidgetAppearance(WidgetStyle.DASHBOARD, true, Color.MAGENTA)
        store.save(0, defaults); store.save(1001, one); store.save(1002, two)
        val reloaded = WidgetAppearanceStore(context)
        assertEquals(one, reloaded.load(1001))
        assertEquals(two, reloaded.load(1002))
        assertEquals(defaults, reloaded.load(1003))
        reloaded.delete(1001)
        assertEquals(defaults, reloaded.load(1001))
        assertEquals(two, reloaded.load(1002))
    }

    @Test fun threeRealWidgetLayoutsRenderAcrossSizesWithTrueTransparencyAndChosenTextColor() {
        AppStrings.init(context)
        val output = File("build/screenshots").apply { mkdirs() }
        for (style in WidgetStyle.entries) {
            for ((width, height) in listOf(110 to 60, 180 to 180, 320 to 180)) {
                val bitmap = WidgetRenderer.render(context, model, WidgetAppearance(style), width, height)
                File(output, "widget_${style.name.lowercase()}_${width}x$height.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertEquals(255, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
                bitmap.recycle()
            }
            val transparent = WidgetRenderer.render(context, model, WidgetAppearance(style, true, Color.MAGENTA), 180, 180)
            assertEquals(0, Color.alpha(transparent.getPixel(1, 1)))
            assertEquals(0, Color.alpha(transparent.getPixel(transparent.width / 2, 2)))
            val pixels = IntArray(transparent.width * transparent.height)
            transparent.getPixels(pixels, 0, transparent.width, 0, 0, transparent.width, transparent.height)
            assertTrue("Custom font color must reach launcher output", pixels.count { Color.alpha(it) > 200 && Color.red(it) > 200 && Color.green(it) < 50 && Color.blue(it) > 200 } > 100)
            File(output, "widget_${style.name.lowercase()}_transparent.png").outputStream().use {
                transparent.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            transparent.recycle()
        }
    }

    @Test fun configurationAppliesSelectedLayoutTransparencyAndColorWithoutChangingOtherWidgets() {
        AppStrings.init(context)
        var applied: WidgetAppearance? = null
        rule.setContent { TcgTheme { WidgetConfiguration(WidgetAppearance(textColor = Color.MAGENTA), model, false,
            onCancel = {}, onApply = { applied = it }) } }
        rule.onNodeWithText("Dashboard").performScrollTo().performClick()
        rule.onNodeWithText("Transparent background").performScrollTo()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Apply widget").performClick()
        assertEquals(WidgetAppearance(WidgetStyle.DASHBOARD, true, Color.MAGENTA), applied)
    }
}
