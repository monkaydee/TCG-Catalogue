package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.PriceChartingApi
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.data.remote.Pricing
import com.monkaydee.tcgcatalogue.data.remote.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingTest {
    private val cm = PriceSource.CARDMARKET
    private val tcg = PriceSource.TCGPLAYER

    @Test fun zekromGoldUsesTheHigherMarket() {
        // TCGdex links the gold Zekrom 115/113 to the regular Zekrom on Cardmarket (5.23 €) while TCGplayer has $411.
        val v = Variant("normal", "Normal", mapOf(cm to 5.23, tcg to 411.19))
        val p = Pricing.pick(v, "Secret Rare", cm, usdToEur = 0.89)!!
        assertEquals(tcg, p.source)
        assertEquals(411.19, p.amount, 0.001)
        assertNotNull(p.note)
    }

    @Test fun commonCardUsesTheLowerMarket() {
        val v = Variant("normal", "Normal", mapOf(cm to 0.10, tcg to 40.0))
        assertEquals(cm, Pricing.pick(v, "Common", tcg, 0.9)!!.source)
    }

    @Test fun agreeingMarketsKeepThePreference() {
        val v = Variant("normal", "Normal", mapOf(cm to 9.0, tcg to 12.0))
        val p = Pricing.pick(v, "Secret Rare", cm, 0.9)!!
        assertEquals(cm, p.source)
        assertNull(p.note)
    }

    @Test fun onlyPokemonAndMagicUseThePreference() {
        assertEquals(cm, Pricing.sourceFor(Game.MAGIC, cm))
        assertEquals(tcg, Pricing.sourceFor(Game.UNION_ARENA, cm))
    }

    private val table = PriceChartingApi.Table(
        "u", "Zekrom #115",
        mapOf("Ungraded" to 160.11, "Grade 8" to 411.79, "Grade 9" to 685.0, "Grade 9.5" to 754.0, "PSA 10" to 18131.4, "BGS 10" to 23571.0, "BGS 10 Black" to 117855.0, "CGC 10" to 10879.0),
    )

    @Test fun gradedPrices() {
        assertEquals(18131.4 to null, PriceChartingApi.priceFor(table, "PSA", "10", null))
        assertEquals(117855.0, PriceChartingApi.priceFor(table, "BGS", "10", "Black Label")!!.first, 0.0)
        assertEquals(685.0, PriceChartingApi.priceFor(table, "CGC", "9", null)!!.first, 0.0)
        // No half grade in the table: next lower grade, flagged as an estimate.
        val (price, note) = PriceChartingApi.priceFor(table, "PSA", "8.5", null)!!
        assertEquals(411.79, price, 0.0)
        assertTrue(note!!.startsWith("Estimate"))
        // Smaller graders have no own 10 column.
        assertEquals(754.0, PriceChartingApi.priceFor(table, "AOG", "10", null)!!.first, 0.0)
    }

    @Test fun parsesPriceChartingPages() {
        val html = """
            <title>Zekrom #115 Prices | Pokemon Legendary Treasures</title>
            <table id="full-prices"><tr><td>Ungraded</td><td>$160.11</td></tr>
            <tr><td>PSA 10</td><td>$18,131.40</td></tr><tr><td>TAG 10</td><td>-</td></tr></table>
        """.trimIndent()
        val t = PriceChartingApi.parseTable("u", html)!!
        assertEquals(18131.40, t.prices["PSA 10"]!!, 0.001)
        assertNull(t.prices["TAG 10"])
        assertEquals("Zekrom #115", t.title)

        val results = """
            <tr id="product-1" data-product="1"><td class="title"><a href="/game/pokemon-scarlet-&amp;-violet-151/pikachu-25">Pikachu #25</a></td></tr>
            <tr id="product-2" data-product="2"><td class="title"><a href="/game/pokemon-japanese-scarlet-&amp;-violet-151/pikachu-master-ball-25">Pikachu [Master Ball] #25</a></td></tr>
        """.trimIndent()
        val parsed = PriceChartingApi.parseResults(results)
        assertEquals(2, parsed.size)
        val card = CardCandidate(Game.POKEMON, "sv03.5-025", "Pikachu", "025/165", "sv03.5", "151", 165, "Common", null, emptyList())
        val best = parsed.filter { PriceChartingApi.matchesNumber(it.title, Game.POKEMON, "25") }
            .maxByOrNull { PriceChartingApi.score(it, card, Variant("normal", "Normal", emptyMap())) }!!
        assertEquals("Pikachu #25", best.title)
    }
}

class ConditionPricingTest {
    private val zekrom = mapOf("Near Mint" to 402.38, "Lightly Played" to 264.57, "Moderately Played" to 158.8, "Heavily Played" to 89.7, "Damaged" to 73.56)

    @Test fun nearMintIsTheMarketPrice() {
        val base = com.monkaydee.tcgcatalogue.data.remote.Price(411.19, PriceSource.TCGPLAYER)
        assertEquals(base, Pricing.forCondition(base, "NM", zekrom))
    }

    @Test fun tcgplayerUsesTheRealConditionPrice() {
        val base = com.monkaydee.tcgcatalogue.data.remote.Price(411.19, PriceSource.TCGPLAYER)
        assertEquals(264.57, Pricing.forCondition(base, "LP", zekrom)!!.amount, 0.001)
        assertEquals(73.56, Pricing.forCondition(base, "DMG", zekrom)!!.amount, 0.001)
    }

    @Test fun cardmarketUsesTheTcgplayerRatio() {
        val base = com.monkaydee.tcgcatalogue.data.remote.Price(186.6, PriceSource.CARDMARKET)
        val mp = Pricing.forCondition(base, "MP", zekrom)!!
        assertEquals(186.6 * 158.8 / 402.38, mp.amount, 0.01)
        assertEquals(PriceSource.CARDMARKET, mp.source)
        assertTrue(mp.note!!.contains("39% of NM"))
    }

    @Test fun noSalesDataUsesTypicalDiscount() {
        val base = com.monkaydee.tcgcatalogue.data.remote.Price(100.0, PriceSource.CARDMARKET)
        val hp = Pricing.forCondition(base, "HP", mapOf("Near Mint" to 120.0))!!
        assertEquals(50.0, hp.amount, 0.001)
        assertTrue(hp.note!!.contains("estimated"))
        assertEquals(85.0, Pricing.forCondition(base, "LP", null)!!.amount, 0.001)
    }

    @Test fun playedNeverAboveNearMint() {
        // One odd LP sale above the NM price (seen on TCGplayer for low-volume cards).
        val base = com.monkaydee.tcgcatalogue.data.remote.Price(80.11, PriceSource.TCGPLAYER)
        assertEquals(80.11, Pricing.forCondition(base, "LP", mapOf("Near Mint" to 80.11, "Lightly Played" to 84.44))!!.amount, 0.001)
        val cm = com.monkaydee.tcgcatalogue.data.remote.Price(70.0, PriceSource.CARDMARKET)
        assertEquals(70.0, Pricing.forCondition(cm, "LP", mapOf("Near Mint" to 80.11, "Lightly Played" to 84.44))!!.amount, 0.001)
    }

    @Test fun worseConditionNeverCostsMore() {
        val base = com.monkaydee.tcgcatalogue.data.remote.Price(0.15, PriceSource.CARDMARKET)
        val pikachu = mapOf("Near Mint" to 0.27, "Lightly Played" to 0.24, "Moderately Played" to 0.2, "Heavily Played" to 0.23, "Damaged" to 0.15)
        val mp = Pricing.forCondition(base, "MP", pikachu)!!.amount
        val hp = Pricing.forCondition(base, "HP", pikachu)!!.amount
        assertTrue("HP $hp should not exceed MP $mp", hp <= mp)
    }

    @Test fun printingMatch() {
        val prices = com.monkaydee.tcgcatalogue.data.remote.TcgPlayerApi.ConditionPrices(
            mapOf(
                "Normal" to mapOf("Near Mint" to 0.27),
                "Reverse Holofoil" to mapOf("Near Mint" to 1.14),
                "1st Edition Holofoil" to mapOf("Near Mint" to 3000.0),
                "Unlimited Holofoil" to mapOf("Near Mint" to 900.0),
            ),
        )
        val f = com.monkaydee.tcgcatalogue.data.remote.TcgPlayerApi.Companion::forPrinting
        assertEquals(1.14, f(prices, "Reverse Holofoil")!!["Near Mint"]!!, 0.0)
        assertEquals(3000.0, f(prices, "1st Edition Holofoil")!!["Near Mint"]!!, 0.0)
        assertEquals(900.0, f(prices, "Holofoil")!!["Near Mint"]!!, 0.0)
        // TCGdex calls the gold Zekrom "normal" while TCGplayer only lists it as Holofoil.
        val single = com.monkaydee.tcgcatalogue.data.remote.TcgPlayerApi.ConditionPrices(mapOf("Holofoil" to mapOf("Near Mint" to 402.0)))
        assertEquals(402.0, f(single, "Normal")!!["Near Mint"]!!, 0.0)
    }
}
