package com.monkaydee.tcgcatalogue.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.data.*
import com.monkaydee.tcgcatalogue.data.db.*
import com.monkaydee.tcgcatalogue.data.remote.SealedProduct
import com.monkaydee.tcgcatalogue.ui.components.*
import com.monkaydee.tcgcatalogue.ui.screens.*
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** User flows affected by the redesign, rendered without provider or catalogue requests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = android.app.Application::class)
class DesignScreenshots {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val settings = AppSettings(currency = "EUR")
    private val card = OwnedCard(game = Game.POKEMON, cardId = "sv10-193", variant = "holo", variantLabel = "Holo",
        name = "Misty’s Psyduck", setId = "sv10", setName = "Destined Rivals", number = "193/182",
        grader = "CGC", grade = "9", language = "DE", price = 59.5, priceCurrency = "EUR",
        priceSource = "eBay graded listings (asking, shipping excluded)", certNumber = "12345678",
        priceNote = "Previous quote retained; latest refresh unavailable")

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(image)) }
        File("build/screenshots", "$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Test fun cardActionsKeepEditSellTradeAlertsAndRemoveReachable() {
        var owned by mutableStateOf(card)
        var edited = 0; var sold = 0; var alerted = 0; var removed = 0
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.CARDNAVO)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(card.name, style = MaterialTheme.typography.headlineSmall)
                        CardPriceSummary(owned, settings)
                        CardActionBar(owned, false, { edited++ }, { sold++ }, { owned = owned.copy(forTrade = it) }, { alerted++ }, { removed++ })
                    }
                }
            }
        }
        rule.onNodeWithText("Edit").performClick(); rule.onNodeWithText("Sell").performClick()
        assertEquals(1, edited); assertEquals(1, sold)
        rule.onNodeWithText("Remove").assertDoesNotExist()
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("For trade").performClick()
        assertTrue(owned.forTrade)
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("Alert").performClick()
        assertEquals(1, alerted)
        rule.onNodeWithText(card.priceNote!!).assertIsDisplayed()
        save("design_card_summary")
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("Remove").performClick()
        assertEquals(1, removed)
        rule.onNodeWithText("Price evidence").performClick()
        rule.onNodeWithText(card.priceSource!!).assertIsDisplayed()
    }

    @Test fun sealedFiltersResetUnsupportedGermanAndKeepJapaneseSelectable() {
        var game by mutableStateOf<Game?>(Game.POKEMON)
        var language by mutableStateOf("DE")
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.CARDNAVO)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Add sealed product", style = MaterialTheme.typography.headlineSmall)
                        SealedFilters(game, language, { game = it }, { language = it })
                        SealedSearchRow(SealedProduct(Game.ONE_PIECE, -9000000010142, "The Azure Sea’s Seven Japanese Booster Box",
                            "One Piece Japanese Booster Boxes", 114.95, currency = "EUR", language = "JA", priceScope = "language-specific-asking",
                            quoteListings = 3, quoteEvidence = "limited"), settings) {}
                        SealedSearchRow(SealedProduct(Game.POKEMON, -824107, "Black Bolt Booster Bundle", "Schwarze Blitze", null,
                            language = "DE", quoteReason = "not_found"), settings) {}
                    }
                }
            }
        }
        rule.onNodeWithText("Pokémon").performClick()
        rule.onNodeWithText("One Piece").performClick()
        assertEquals("EN", language)
        rule.onNodeWithText("English").performClick()
        rule.onNodeWithText("Deutsch").assertIsNotEnabled()
        rule.onNodeWithText("日本語 (JP)").performClick()
        assertEquals("JA", language)
        rule.onNodeWithText("No matching price").assertIsDisplayed()
        rule.onNodeWithText("Limited reference").assertIsDisplayed()
        save("design_sealed_search")
    }

    @Test fun captureStartsWithVisiblePhotoActionsAndExpandableTips() {
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.CARDNAVO)) {
                PreGradeFlow(null, {}, initialGame = Game.POKEMON)
            }
        }
        rule.onNodeWithText("Take photo").assertIsDisplayed()
        rule.onNodeWithText("Choose a photo").assertIsDisplayed()
        rule.onNodeWithText("Step 1 of 4 · Capture").assertIsDisplayed()
        save("design_pregrade_capture")
    }

    @Test @Config(qualifiers = "de-rDE-w320dp-h640dp-xhdpi")
    fun germanActionsAndSelectorsRemainReachableOnSmallPhones() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
            TcgTheme(Look(ThemeMode.LIGHT, Palette.CARDNAVO)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GameChips(Game.ONE_PIECE, {}, nullLabel = null)
                        CardActionBar(card, false, {}, {}, {}, {}, {})
                        AppSelector("Ansicht", 3, listOf(SelectorOption(3, "3 × 3"), SelectorOption(6, "6 × 6"), SelectorOption(9, "9 × 9")), {})
                    }
                }
            }
        }
        }
        rule.onNodeWithText("Bearbeiten").assertIsDisplayed()
        rule.onNodeWithText("Verkaufen").assertIsDisplayed()
        rule.onNodeWithContentDescription("Weitere Aktionen").performClick()
        rule.onNodeWithText("Entfernen").assertIsDisplayed()
        save("design_german_small_phone")
    }
}
