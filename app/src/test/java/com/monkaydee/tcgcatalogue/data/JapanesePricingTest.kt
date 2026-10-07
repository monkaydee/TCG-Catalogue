package com.monkaydee.tcgcatalogue.data

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.*
import com.monkaydee.tcgcatalogue.data.remote.*
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
import com.monkaydee.tcgcatalogue.ui.components.AddRequest
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
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
class JapanesePricingTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        var japaneseAvailable = true
        var onCardRequest: (() -> Unit)? = null
        val http = Http(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            requests += path
            if (path.endsWith("/cards/me01-135")) onCardRequest?.invoke()
            val resource = listOf("me01-135", "me01-150", "M1L-066", "M1L-073").firstOrNull { path.endsWith("/cards/$it") }
            val body = when {
                resource != null && (japaneseAvailable || !path.contains("/ja/")) ->
                    javaClass.getResource("/japanese-pricing/$resource.json")!!.readText()
                path.contains("/price/history/") -> """{"result":[{"language":"English","variant":"Holofoil","condition":"Near Mint","buckets":[{"marketPrice":500.0}]},{"language":"Japanese","variant":"Holofoil","condition":"Near Mint","buckets":[{"marketPrice":2.31}]},{"language":"Japanese","variant":"Holofoil","condition":"Lightly Played","buckets":[{"marketPrice":1.5}]}]}"""
                else -> null
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (body == null) 404 else 200)
                .message("fixture").body((body ?: "").toResponseBody()).build()
        }.build())
        val tcgdex = TcgDexApi(http)
        val tcgplayer = TcgPlayerApi(http)
        val directory = File(context.cacheDir, "jp-pricing-${java.util.UUID.randomUUID()}").apply {
            mkdirs()
            resolve("JAPANESE_NAME_ALIASES.json").writeText("""{"sets":{"M1L":{"cards":{"5":["Exeggutor"],"66":["Exeggutor"],"45":["Steelix"],"73":["Steelix"]},"setAliases":["Mega Brave","メガブレイブ"]}}}""")
        }
        val index = CardIndexApi(http, directory)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsStore(context)
        val repo = CardRepository(db, tcgdex, OnePieceApi(http), ScryfallApi(http), index, tcgplayer,
            CardmarketApi(index, http), CardmarketPokemon(index, http, tcgdex), FxApi(http), settings)
        init { AppStrings.init(context) }
        override fun close() { db.close(); directory.deleteRecursively() }
        suspend fun english(id: String) = tcgdex.card(id)!!
        suspend fun japanese(id: String) = tcgdex.forLanguage("ja").card("ja:$id")!!
    }

    @Test fun languageOnlyChangeCannotPriceEnglishNumbersAsJapanese() = runBlocking {
        Fixture().use { f ->
            val english = f.english("me01-150")
            // International catalogue metadata must leave language detection to the scan.
            assertNull(english.language)
            assertFalse(f.repo.isConfident(listOf(english.copy(language = "JA", score = 1.0))))
            assertTrue(f.repo.isConfident(listOf(english.copy(language = "DE", score = 1.0))))
            assertNull(f.repo.conditionPrice(english, english.defaultVariant, "NM", f.settings.current(), language = "JA"))
            assertTrue(f.repo.gradedLookup(english, english.defaultVariant, GradeInfo("PSA", "9"), "JA").problem!!.contains("Japanese set"))
            val legacy = OwnedCard(game = Game.POKEMON, cardId = english.cardId, variant = "holo", variantLabel = "Holo",
                name = english.name, setId = english.setId, setName = english.setName, number = english.number,
                language = "JA", price = 7.6, priceCurrency = "EUR", purchasePrice = 12.0, manualPrice = 70.0, manualCurrency = "EUR")
            val id = f.db.cards().insert(legacy)
            assertFalse(f.repo.refreshPrice(id))
            val row = f.db.cards().get(id)!!
            assertNull(row.price)
            assertEquals(70.0, row.manualPrice!!, 0.001)
            assertEquals(12.0, row.purchasePrice!!, 0.001)
            assertTrue(row.priceNote!!.contains("open Edit"))
        }
    }

    @Test fun nativeJapaneseCardsUseTheirOwnGuideAndProductPhotos() = runBlocking {
        Fixture().use { f ->
            for ((id, amount, product) in listOf(Triple("M1L-066", 2.19, 647175L), Triple("M1L-073", 2.21, 647182L))) {
                val card = f.japanese(id)
                assertEquals("JA", card.language)
                assertTrue(f.repo.isConfident(listOf(card)))
                assertEquals(product, card.defaultVariant.tcgplayerId)
                assertTrue(card.imageUrl!!.contains("/$product"))
                val quote = f.repo.conditionPrice(card, card.defaultVariant, "NM", f.settings.current(), language = "JA")!!
                assertEquals(amount, quote.amount, 0.001)
                assertEquals("EUR", quote.currency)
                assertTrue(quote.note!!.contains("condition"))
            }
            val options = f.repo.japanesePrintings(f.english("me01-135"))
            assertEquals(listOf("ja:M1L-066"), options.map { it.cardId })
            assertEquals("066/063", options.single().number)
        }
    }

    @Test fun JapaneseAndEnglishConditionPricesHaveSeparateCachesAndLanguageEvidence() = runBlocking {
        Fixture().use { f ->
            val en = f.tcgplayer.conditionPrices(647182, "EN")!!
            val jp = f.tcgplayer.conditionPrices(647182, "JA")!!
            assertEquals(500.0, en.byPrinting.getValue("Holofoil").getValue("Near Mint"), 0.001)
            assertEquals(2.31, jp.byPrinting.getValue("Holofoil").getValue("Near Mint"), 0.001)
            assertEquals(1.5, jp.byPrinting.getValue("Holofoil").getValue("Lightly Played"), 0.001)
            assertEquals(2, f.requests.count { it.contains("/price/history/") })
            val japanese = f.japanese("M1L-073")
            val row = OwnedCard(game = Game.POKEMON, cardId = japanese.cardId, variant = "holo", variantLabel = "Holo",
                name = japanese.name, number = japanese.number, setId = japanese.setId, setName = japanese.setName, language = "JA")
            val overview = f.repo.priceOverview(row)
            val sales = overview.last()
            assertEquals(2.31, sales.lines.first { it.label == "NM" }.amount, 0.001)
            assertNull(sales.problem)
        }
    }

    @Test fun choosingJapanesePrintingRepairsSavedIdentityAndKeepsCostsAndManualValue() = runBlocking {
        Fixture().use { f ->
            val english = f.english("me01-135")
            val native = f.repo.japanesePrintings(english).single()
            val old = OwnedCard(game = Game.POKEMON, cardId = english.cardId, variant = "holo", variantLabel = "Holo",
                name = english.name, number = english.number, setId = english.setId, setName = english.setName,
                quantity = 2, language = "JA", purchasePrice = 12.0, priceCurrency = "EUR", manualPrice = 70.0, manualCurrency = "EUR")
            val id = f.db.cards().insert(old)
            f.repo.saveEdit(f.db.cards().get(id)!!, AddRequest(native, native.defaultVariant, 2, "NM", null, null, 12.0, 70.0, "JA"))
            val saved = f.db.cards().get(id)!!
            assertEquals("ja:M1L-066", saved.cardId)
            assertEquals("ja:M1L", saved.setId)
            assertEquals("066/063", saved.number)
            assertEquals(2.19, saved.price!!, 0.001)
            assertEquals(12.0, saved.purchasePrice!!, 0.001)
            assertEquals(70.0, saved.manualPrice!!, 0.001)
            assertTrue(saved.imageUrl!!.contains("/647175"))
        }
    }

    @Test fun backgroundRefreshCannotRevertAJapanesePrintingEdit() = runBlocking {
        Fixture().use { f ->
            val english = f.english("me01-135")
            val japanese = f.japanese("M1L-066")
            val old = OwnedCard(game = Game.POKEMON, cardId = english.cardId, variant = "holo", variantLabel = "Holo",
                name = english.name, number = english.number, setId = english.setId, setName = english.setName,
                language = "JA", quantity = 1, price = 5.13, priceCurrency = "EUR", purchasePrice = 12.0)
            val id = f.db.cards().insert(old)
            val edited = old.copy(id = id, cardId = japanese.cardId, name = japanese.name, number = japanese.number,
                setId = japanese.setId, setName = japanese.setName, imageUrl = japanese.imageUrl,
                quantity = 3, price = 2.19, manualPrice = 70.0, manualCurrency = "EUR")
            f.onCardRequest = { runBlocking { f.db.cards().update(edited) } }
            assertEquals(0, f.repo.refreshPrices())
            assertEquals(edited, f.db.cards().get(id))
        }
    }

    @Test fun JapaneseChoiceRequiresNativeIdentityBeforeSave() {
        Fixture().use { f ->
            val english = runBlocking { f.english("me01-135") }
            var request: AddRequest? = null
            rule.setContent { TcgTheme { AddCardSheet(listOf(english), AppSettings(), f.repo,
                onAdd = { request = it }, onDismiss = {}) } }
            rule.onNodeWithText("Japanese (JP)").performScrollTo().performClick()
            rule.onNodeWithText("Add").performScrollTo().assertIsNotEnabled()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Japanese printing").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Japanese printing").performScrollTo().performClick()
            rule.onNodeWithText("M1L · 066/063 · ナッシー").performClick()
            rule.onNodeWithText("ナッシー").performScrollTo().assertIsDisplayed()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("€2.19").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("066/063 · Illustration rare").assertIsDisplayed()
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(android.graphics.Canvas(image)) }
            File("build/screenshots", "japanese_printing_selected.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
            rule.onNodeWithText("Add").performScrollTo().assertIsEnabled().performClick()
            assertEquals("ja:M1L-066", request!!.card.cardId)
            assertEquals("JA", request!!.language)
            assertEquals("holo", request!!.variant.key)
        }
    }
}
