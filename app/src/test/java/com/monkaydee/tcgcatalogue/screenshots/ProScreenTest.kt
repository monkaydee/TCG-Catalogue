package com.monkaydee.tcgcatalogue.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.monkaydee.tcgcatalogue.data.Pro
import com.monkaydee.tcgcatalogue.ui.screens.ProContent
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = Application::class)
class ProScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(Pro.State(enforced = true, price = "24,99 €", store = Pro.Store.READY))
    private var buys = 0
    private var restores = 0

    private fun show() = rule.setContent { TcgTheme { ProContent(state, onBack = {}, onBuy = { buys++ }, onRestore = { restores++ }) } }

    private fun save(name: String) {
        rule.waitForIdle(); val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun freeUserSeesThePlayPriceAndCanBuyOrRestore() {
        show(); save("pro_paywall")
        rule.onNodeWithText("Unlock for 24,99 €").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("pro_buy").performScrollTo().performClick()
        rule.onNodeWithTag("pro_restore").performScrollTo().performClick()
        assertEquals(1, buys); assertEquals(1, restores)
    }

    @Test fun ownerSeesNoBuyButton() {
        state = state.copy(owned = true, notice = Pro.Notice.PURCHASED)
        show(); save("pro_owned")
        rule.onNodeWithTag("pro_owned").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("pro_buy").assertDoesNotExist()
    }

    @Test fun pendingPaymentExplainsTheWaitAndBlocksASecondPurchase() {
        state = state.copy(pending = true)
        show()
        rule.onNodeWithTag("pro_notice").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("pro_buy").performScrollTo().assertIsNotEnabled()
    }

    @Test fun priceNotLoadedYetStillOffersTheUnlock() {
        state = state.copy(price = null, store = Pro.Store.UNAVAILABLE)
        show()
        rule.onNodeWithText("Unlock Pro").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("pro_notice").performScrollTo().assertIsDisplayed()
    }
}
