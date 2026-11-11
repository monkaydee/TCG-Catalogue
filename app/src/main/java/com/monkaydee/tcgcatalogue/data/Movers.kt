package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.PriceHistory

/**
 * Market movers from the app's own daily price history: each card's latest price per copy against
 * its last recorded price at least [days] days earlier. Cards without such an older point don't move.
 */
object Movers {
    data class Move(val rowId: Long, val before: Double, val now: Double) {
        val change: Double get() = now - before
        val percent: Double get() = if (before > 0) change / before * 100 else 0.0
    }

    fun moves(points: List<PriceHistory>, days: Int, convert: (Double, String) -> Double): Map<Long, Move> =
        points.groupBy { it.cardRowId }.mapNotNull { (row, list) ->
            val latest = list.maxBy { it.day }
            val base = list.filter { it.day <= latest.day - days }.maxByOrNull { it.day } ?: return@mapNotNull null
            val before = convert(base.price, base.currency); val now = convert(latest.price, latest.currency)
            if (before <= 0 || now <= 0) null else row to Move(row, before, now)
        }.toMap()

    /** Biggest risers and fallers by percent, ignoring cards worth less than [minPrice] then and now. */
    fun top(moves: Collection<Move>, n: Int = 5, minPrice: Double = 1.0): Pair<List<Move>, List<Move>> {
        val relevant = moves.filter { maxOf(it.before, it.now) >= minPrice && it.change != 0.0 }
        return relevant.filter { it.change > 0 }.sortedByDescending { it.percent }.take(n) to
            relevant.filter { it.change < 0 }.sortedBy { it.percent }.take(n)
    }
}
