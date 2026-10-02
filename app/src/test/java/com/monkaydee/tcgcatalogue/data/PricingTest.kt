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
