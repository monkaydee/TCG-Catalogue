package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.*

object CostLedger {
    fun basis(lot: CostLot, currency: String, rate: Double): Double? {
        val costs = listOf(lot.purchase, lot.grading, lot.shipping, lot.tax)
        if (costs.any { it == null }) return null
        return Money.convert(costs.filterNotNull().sum(), lot.currency, currency, rate) * lot.quantity
    }
    fun pnl(card: OwnedCard, lots: List<CostLot>, currency: String, rate: Double): Double? {
        if (card.manualPrice == null && card.price == null) return null
        if (lots.sumOf { it.quantity } != card.quantity) return null
        val basis = lots.map { basis(it, currency, rate) ?: return null }.sum()
        return Money.value(card, currency, rate) - basis
    }
    /** FIFO allocation; splitting never changes costs or acquisition currency. */
    fun allocate(lots: List<CostLot>, quantity: Int): Pair<List<CostLot>, List<CostLot>> {
        require(quantity > 0 && quantity <= lots.sumOf { it.quantity })
        var left = quantity
        val sold = mutableListOf<CostLot>(); val retained = mutableListOf<CostLot>()
        for (lot in lots.sortedWith(compareBy<CostLot> { it.acquiredAt }.thenBy { it.id })) {
            val take = minOf(left, lot.quantity)
            if (take > 0) sold += lot.copy(quantity = take)
            if (take < lot.quantity) retained += lot.copy(quantity = lot.quantity - take)
            left -= take
        }
        return sold to retained
    }
}
