package com.monkaydee.tcgcatalogue.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.*
import com.monkaydee.tcgcatalogue.data.remote.*
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.AddRequest
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Cost ledger, backup merge/import and deletion cases from the 8 October review. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LedgerBackupRegressionTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val http = Http(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("offline").body("".toResponseBody()).build()
        }.build())
        val dir = File(context.cacheDir, "ledger-${java.util.UUID.randomUUID()}")
        val index = CardIndexApi(http, dir)
        val dex = TcgDexApi(http)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = CardRepository(db, dex, OnePieceApi(http), ScryfallApi(http), index, TcgPlayerApi(http),
            CardmarketApi(index, http), CardmarketPokemon(index, http, dex), FxApi(http), SettingsStore(context), server = PriceServerApi { null })
        init { AppStrings.init(context) }
        override fun close() { db.close(); dir.deleteRecursively() }
    }

    private fun card(id: Long = 0, quantity: Int = 1, purchase: Double? = 10.0) = OwnedCard(id = id, game = Game.POKEMON, cardId = "base1-4", variant = "holo",
        variantLabel = "Holo", name = "Charizard", number = "4/102", setId = "base1", setName = "Base Set", quantity = quantity,
        purchasePrice = purchase, priceCurrency = "EUR")

    private fun lot(row: Long, purchase: Double, id: String = "lot-a", quantity: Int = 1, updatedAt: Long = 0) =
        CostLot(id = id, cardRowId = row, quantity = quantity, purchase = purchase, grading = 0.0, shipping = 0.0, tax = 0.0, currency = "EUR", acquiredAt = 1, updatedAt = updatedAt)

    @Test fun editingThePurchasePriceCorrectsTheLedger() = runBlocking {
        Fixture().use { f ->
            val row = f.db.cards().insert(card())
            f.db.tools().put(lot(row, 10.0))
            val original = f.db.cards().get(row)!!
            val candidate = CardCandidate(Game.POKEMON, "base1-4", "Charizard", "4/102", "base1", "Base Set", 102, null, null,
                listOf(Variant("holo", "Holo", emptyMap())))
            f.repo.saveEdit(original, AddRequest(candidate, candidate.variants[0], 1, "NM", null, null, 40.0, language = "EN"))
            val lots = f.db.tools().lots(row)
            assertEquals(1, lots.size)
            assertEquals(40.0, lots[0].purchase!!, 0.01)
        }
    }

    @Test fun differentLotPricesAreLeftForTheLotEditor() = runBlocking {
        Fixture().use { f ->
            val row = f.db.cards().insert(card(quantity = 2))
            f.db.tools().put(lot(row, 10.0, "a")); f.db.tools().put(lot(row, 20.0, "b"))
            val candidate = CardCandidate(Game.POKEMON, "base1-4", "Charizard", "4/102", "base1", "Base Set", 102, null, null,
                listOf(Variant("holo", "Holo", emptyMap())))
            f.repo.saveEdit(f.db.cards().get(row)!!, AddRequest(candidate, candidate.variants[0], 2, "NM", null, null, 40.0, language = "EN"))
            assertEquals(listOf(10.0, 20.0), f.db.tools().lots(row).map { it.purchase })
        }
    }

    @Test fun mergeTakesTheNewerCorrectionOfALot() = runBlocking {
        Fixture().use { f ->
            val row = f.db.cards().insert(card())
            f.db.tools().put(lot(row, 10.0))
            val incoming = Backup(cards = listOf(card(id = 99)), snapshots = emptyList(), costLots = listOf(lot(99, 40.0, updatedAt = 5)))
            f.repo.mergeBackup(incoming)
            assertEquals(40.0, f.db.tools().lots(row).single().purchase!!, 0.0)
            // an older version from the file never undoes a local correction
            f.db.tools().put(lot(row, 50.0, updatedAt = 9))
            f.repo.mergeBackup(incoming)
            assertEquals(50.0, f.db.tools().lots(row).single().purchase!!, 0.0)
        }
    }

    @Test fun mergeKeepsTheSplitMadeOnTheOtherPhone() = runBlocking {
        Fixture().use { f ->
            val row = f.db.cards().insert(card(quantity = 2))
            f.db.tools().put(lot(row, 10.0, quantity = 2))
            f.repo.mergeBackup(Backup(cards = listOf(card(id = 99, quantity = 2)), snapshots = emptyList(),
                costLots = listOf(lot(99, 30.0, quantity = 1, updatedAt = 5), lot(99, 10.0, id = "lot-b", quantity = 1, updatedAt = 5))))
            assertEquals(listOf(30.0, 10.0), f.db.tools().lots(row).sortedBy { it.id }.map { it.purchase })
        }
    }

    @Test fun mergeBringsThePriceHistoryOfANewCard() = runBlocking {
        Fixture().use { f ->
            f.repo.mergeBackup(Backup(cards = listOf(card(id = 42)), snapshots = emptyList(), history = listOf(PriceHistory(42, 20000, 55.0, "EUR"))))
            val row = f.db.cards().getAll().single().id
            assertEquals(listOf(55.0), f.db.history().getAll().filter { it.cardRowId == row }.map { it.price })
        }
    }

    @Test fun replacementImportDropsThePreviousPortfolioChart() = runBlocking {
        Fixture().use { f ->
            f.db.snapshots().upsert(PortfolioSnapshot(day = 19000, valueUsd = 100.0, valueEur = 90.0, cardCount = 1))
            f.repo.importBackup(Backup(cards = emptyList(), snapshots = emptyList()))
            assertTrue(f.db.snapshots().getAll().isEmpty())
        }
    }

    @Test fun deletingACardRemovesItsSubmissions() = runBlocking {
        Fixture().use { f ->
            val row = f.db.cards().insert(card())
            f.db.tools().put(GradingSubmission(cardRowId = row, company = "PSA"))
            f.repo.delete(f.db.cards().get(row)!!)
            assertTrue(f.db.tools().submissions().isEmpty())
        }
    }
}
