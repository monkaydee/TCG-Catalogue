package com.monkaydee.tcgcatalogue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorToolsTest {
    private fun quote(grader: String, grade: String, price: Double, qualifier: String? = null) = GradingValue.Outcome(grader, grade, qualifier, price, 0.0)

    @Test fun worthGradingSubtractsRawValueAndCostAndFindsTheBreakEvenGrade() {
        val out = GradingValue.outcomes(raw = 40.0, cost = 30.0, quotes = listOf(quote("PSA", "10", 200.0), quote("PSA", "9", 80.0), quote("PSA", "8", 60.0), quote("CGC", "9", 65.0)))
        assertEquals(listOf(-5.0, 130.0, 10.0, -10.0), out.map { it.gain })
        assertEquals(mapOf("CGC" to null, "PSA" to "9"), GradingValue.breakEven(out))
        // unknown raw value: no guess
        assertTrue(GradingValue.outcomes(null, 30.0, listOf(quote("PSA", "10", 200.0))).isEmpty())
    }

    @Test fun setCompletionCountsUnknownPricesApart() {
        assertEquals(SetCompletion.Cost(12.5, 2, 1), SetCompletion.cost(listOf(10.0, null, 2.5)))
    }

    @Test fun lotOfferIsAShareOfKnownMarketValue() {
        val r = LotValue.of(listOf(100.0, 50.0, null), 70)
        assertEquals(150.0, r.market, 1e-9); assertEquals(105.0, r.offer, 1e-9); assertEquals(1, r.unknown)
    }

    @Test fun displayCurrencyConvertsFormattedAmountsOnly() {
        try {
            Money.show("GBP", mapOf("GBP" to 0.85), usdToEur = 0.9)
            val (gbp, code) = Money.displayed(100.0, "EUR")!!
            assertEquals("GBP", code); assertEquals(85.0, gbp, 1e-9)
            // 100 USD = 90 EUR = 76.5 GBP
            assertEquals(76.5, Money.displayed(100.0, "USD")!!.first, 1e-9)
            assertNull(Money.displayed(10.0, "GBP"))
            assertTrue(Money.format(100.0, "EUR").contains("85"))
            // no rate for the chosen currency: amounts stay as stored
            Money.show("JPY", emptyMap(), 0.9)
            assertNull(Money.displayed(100.0, "EUR"))
        } finally { Money.show(null, emptyMap(), 0.9) }
    }
}
