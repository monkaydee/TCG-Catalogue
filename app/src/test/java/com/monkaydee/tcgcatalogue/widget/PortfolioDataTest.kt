package com.monkaydee.tcgcatalogue.widget

import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.data.db.SealedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioDataTest {
    private fun card(price: Double, qty: Int = 1, currency: String = "EUR") = OwnedCard(
        game = Game.POKEMON, cardId = "x-$price", variant = "normal", variantLabel = "Normal", name = "n", number = "1",
        setId = "s", setName = "S", price = price, priceCurrency = currency, quantity = qty,
    )

    private val eur = AppSettings(currency = "EUR", usdToEur = 0.5)
    private val today = 20_000L

    @Test fun valueIncludesCardsAndSealed() {
        val sealed = SealedItem(game = Game.POKEMON, productId = 1, name = "Box", groupName = "G", quantity = 2, price = 10.0, priceCurrency = "USD")
        val d = PortfolioData.compute(listOf(card(10.0, 2), card(4.0, currency = "USD")), listOf(sealed), emptyList(), eur, today, 1L)
        // 20 EUR + 4 USD (= 2 EUR) + 2 sealed at 10 USD (= 10 EUR)
        assertEquals(32.0, d.value, 1e-9)
        assertEquals(3, d.cardCount)
    }

    @Test fun changeComparesWithOldestSnapshotOfLast30Days() {
        val snaps = listOf(
            PortfolioSnapshot(today - 45, 0.0, 1.0, 1),
            PortfolioSnapshot(today - 30, 0.0, 80.0, 1),
            PortfolioSnapshot(today - 10, 0.0, 90.0, 1),
            PortfolioSnapshot(today, 0.0, 100.0, 1),
        )
        val d = PortfolioData.compute(listOf(card(100.0)), emptyList(), snaps, eur, today, 1L)
        assertEquals(20.0, d.change!!, 1e-9)
        assertEquals(25.0, d.changePercent!!, 1e-9)
    }

    @Test fun noChangeWithoutOlderSnapshot() {
        val snaps = listOf(PortfolioSnapshot(today, 0.0, 100.0, 1), PortfolioSnapshot(today - 40, 0.0, 5.0, 1))
        val d = PortfolioData.compute(listOf(card(100.0)), emptyList(), snaps, eur, today, 1L)
        assertNull(d.change)
        assertNull(d.changePercent)
    }

    @Test fun usesUsdSnapshotsInUsd() {
        val snaps = listOf(PortfolioSnapshot(today - 5, 50.0, 25.0, 1))
        val d = PortfolioData.compute(listOf(card(60.0, currency = "USD")), emptyList(), snaps, AppSettings(currency = "USD"), today, 1L)
        assertEquals(10.0, d.change!!, 1e-9)
    }

    @Test fun updatedAtIsLastRefreshOrNow() {
        assertEquals(7L, PortfolioData.compute(emptyList(), emptyList(), emptyList(), eur, today, 7L).updatedAt)
        assertEquals(3L, PortfolioData.compute(emptyList(), emptyList(), emptyList(), eur.copy(lastPriceRefresh = 3L), today, 7L).updatedAt)
    }
}
