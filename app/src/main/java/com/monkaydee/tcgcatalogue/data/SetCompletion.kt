package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.remote.CardCandidate

/** What the missing cards of a set would cost: known prices summed, cards without a price counted apart. */
object SetCompletion {
    data class Cost(val total: Double, val priced: Int, val unknown: Int)

    fun cheapest(card: CardCandidate, currency: String, usdToEur: Double): Double? =
        card.variants.flatMap { v -> v.prices.map { (source, amount) -> Money.convert(amount, source.currency, currency, usdToEur) } }
            .filter { it.isFinite() && it > 0 }.minOrNull()

    fun cost(prices: List<Double?>): Cost = Cost(prices.filterNotNull().sum(), prices.count { it != null }, prices.count { it == null })
}
