package com.monkaydee.tcgcatalogue.data

/** A lot priced at a share of its market value, as dealers buy and sell bulk. Unknown values are counted, not guessed. */
object LotValue {
    data class Result(val market: Double, val unknown: Int, val offer: Double)

    fun of(values: List<Double?>, percent: Int): Result {
        val market = values.filterNotNull().filter { it.isFinite() && it >= 0 }.sum()
        return Result(market, values.count { it == null }, market * percent.coerceIn(0, 200) / 100.0)
    }
}
