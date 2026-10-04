package com.monkaydee.tcgcatalogue.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.BinderPage
import com.monkaydee.tcgcatalogue.data.Look
import com.monkaydee.tcgcatalogue.data.Palette
import com.monkaydee.tcgcatalogue.data.ThemeMode
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.OnboardingScreen
import com.monkaydee.tcgcatalogue.ui.components.EmptyIllustration
import com.monkaydee.tcgcatalogue.ui.components.EmptyKind
import com.monkaydee.tcgcatalogue.ui.components.PageTurnState
import com.monkaydee.tcgcatalogue.ui.components.PageTurner
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.screens.BinderSheet
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens on the JVM and saves them to app/build/screenshots, so visual changes can be
 * reviewed as images (CI uploads them). Card images aren't loaded (no network), so their
 * placeholders show instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = android.app.Application::class)
class Screenshots {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun cards(n: Int, offset: Int = 0) = (1..n).map { i ->
        OwnedCard(
            id = (i + offset).toLong(), game = Game.POKEMON, cardId = "c$i", variant = "normal", variantLabel = "Normal", name = "Card $i",
            number = "$i/165", setId = "s", setName = "151", price = (i + offset) * 1.5, priceCurrency = "EUR", quantity = if (i % 4 == 0) 2 else 1,
            grader = if (i % 5 == 0) "PSA" else null, grade = if (i % 5 == 0) "10" else null,
        )
    }

    @Test fun binderPages() {
        var grid by mutableStateOf(3)
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(8.dp)) {
                    BinderSheet(BinderPage(cards(grid * grid - 2), "Scarlet & Violet 151"), grid, AppSettings(), {})
                }
            }
        }
        save("binder_3x3")
        rule.runOnUiThread { grid = 9 }
        save("binder_9x9")
    }

    @Test fun binderPageTurn() {
        val state = PageTurnState(1)
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = 32.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    PageTurner(state, 3, Modifier.fillMaxSize(), pageBack = { BinderSheet(null, 3, AppSettings(), {}) }) { i ->
                        BinderSheet(BinderPage(cards(9 - i * 3, i * 10), "Page ${i + 1}"), 3, AppSettings(), {})
                    }
                }
            }
        }
        for (t in listOf(0.25f, 0.45f)) {
            rule.runOnUiThread { state.progress = t }
            save("binder_turn_${(t * 100).toInt()}")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun themes() {
        var look by mutableStateOf(Look())
        rule.setContent { TcgTheme(look) { Sample() } }
        val cases = listOf(
            "theme_indigo_light" to Look(ThemeMode.LIGHT, Palette.INDIGO),
            "theme_indigo_dark" to Look(ThemeMode.DARK, Palette.INDIGO),
            "theme_forest_light" to Look(ThemeMode.LIGHT, Palette.FOREST),
            "theme_sunset_dark" to Look(ThemeMode.DARK, Palette.SUNSET),
        )
        for ((name, l) in cases) {
            rule.runOnUiThread { look = l }
            save(name)
        }
    }

    @Test fun onboarding() {
        rule.setContent { TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) { OnboardingScreen {} } }
        save("onboarding")
    }

    @Test fun emptyCollection() {
        rule.setContent {
            TcgTheme(Look(ThemeMode.LIGHT, Palette.INDIGO)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    EmptyIllustration(EmptyKind.COLLECTION, "Your collection is empty", "Scan a card to start.")
                }
            }
        }
        save("empty_collection")
    }

    @Test fun cardPageParts() {
        val today = java.time.LocalDate.now().toEpochDay()
        val history = (0 until 30).map { d ->
            com.monkaydee.tcgcatalogue.data.db.PriceHistory(1, today - 29 + d, 40.0 + 8 * kotlin.math.sin(d / 4.0) + d * 0.6, "EUR")
        }
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val tilt = androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.geometry.Offset(0.4f, -0.3f)) }
                    com.monkaydee.tcgcatalogue.ui.components.HoloCard(tilt, Modifier.size(180.dp, 251.dp)) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF3949AB)))
                    }
                    com.monkaydee.tcgcatalogue.ui.components.PriceHistoryCard(history, AppSettings(currency = "EUR"), Modifier.fillMaxWidth())
                }
            }
        }
        save("card_page_parts")
    }

    @Test fun launcherIcon() {
        rule.setContent {
            Box(Modifier.size(192.dp).clip(RoundedCornerShape(48.dp)).background(colorResource(R.color.cardnavo_navy))) {
                Image(painterResource(R.drawable.cardnavo_foreground), null, Modifier.fillMaxSize())
            }
        }
        save("launcher_icon")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sample() {
        Scaffold(topBar = { TopAppBar(title = { Text("TCG Catalogue") }, colors = appBarColors()) }) { p ->
            Column(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Portfolio value", style = MaterialTheme.typography.labelLarge)
                        Text("€1,234.56", style = MaterialTheme.typography.displaySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(true, {}, { Text("All") })
                    FilterChip(false, {}, { Text("Pokémon") })
                    FilterChip(false, {}, { Text("One Piece") })
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Scarlet & Violet 151", style = MaterialTheme.typography.titleSmall)
                        Text("Pokémon · 42/165 cards", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { 0.3f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({}) { Text("Scan a card") }
                    OutlinedButton({}) { Text("Import") }
                }
            }
        }
    }
}

