package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fixes TCGdex's Cardmarket link for Pokémon cards that have look-alikes in their set.
 *
 * Cardmarket groups all prints of a card with the same name and attacks in a set (its
 * "metacard"): the regular Zekrom 51/113 and the gold Zekrom 115/113 are two products of one
 * metacard, and TCGdex sometimes points the gold one at the regular product (5.23 EUR instead
 * of 186.60 EUR). Cardmarket numbers those products in collector-number order, so the k-th
 * look-alike by number is the k-th product of the group. Prices come from Cardmarket's public
 * price guide via CARDMARKET_POKEMON.json on the data branch.
 */
class CardmarketPokemon(private val files: CardIndexApi, private val http: Http, private val tcgdex: TcgDexApi) {
    /** One Cardmarket product: normal and reverse-holo ("-holo") price fields. */
    class Row(val productId: Long, val group: Pair<Int, Long>, val normal: List<Double?>, val holo: List<Double?>)

    private val mutex = Mutex()
    private var rows: Map<Long, Row> = emptyMap()
    private var groups: Map<Pair<Int, Long>, List<Long>> = emptyMap()
    private var loadedFrom = 0L

    private suspend fun load() = mutex.withLock {
        val file = files.dailyFile(FILE) ?: return@withLock
        if (file.lastModified() == loadedFrom && rows.isNotEmpty()) return@withLock
        val parsed = withContext(Dispatchers.Default) {
            http.json.parseToJsonElement(file.readText())["cards"].arr().orEmpty().mapNotNull { r ->
                val a = r.arr() ?: return@mapNotNull null
                val pid = a.getOrNull(0).str()?.toLongOrNull() ?: return@mapNotNull null
                val group = (a.getOrNull(1).int() ?: 0) to (a.getOrNull(2).str()?.toLongOrNull() ?: 0L)
                Row(pid, group, (3..8).map { a.getOrNull(it).dbl() }, (9..14).map { a.getOrNull(it).dbl() })
            }
        }
        rows = parsed.associateBy { it.productId }
        groups = parsed.groupBy { it.group }.mapValues { (_, v) -> v.map { it.productId }.sorted() }
        loadedFrom = file.lastModified()
    }

    /** [card] with the Cardmarket prices of the right product, or unchanged when there's nothing to fix. */
    suspend fun fix(card: CardCandidate): CardCandidate {
        val linked = card.cardmarketId ?: return card
        load()
        rows[linked] ?: return card // no look-alikes on Cardmarket: TCGdex's link is unambiguous
        val sameName = tcgdex.setCards(card.setId).filter { it.second == card.name }
        if (sameName.size < 2) return card
        val lookAlikes = sameName.mapNotNull { (id, _) -> if (id == card.cardId) card else tcgdex.cachedCard(id) }
            .filter { it.attacks == card.attacks }
            .sortedWith(compareBy({ numberOf(it) }, { it.number }))
        if (lookAlikes.size < 2) return card
        // The Cardmarket group with exactly as many prints as there are look-alikes.
        val candidates = lookAlikes.mapNotNull { it.cardmarketId?.let { id -> rows[id]?.group } }.distinct()
            .map { groups[it].orEmpty() }
            .filter { it.size == lookAlikes.size }
        val group = candidates.singleOrNull() ?: return card
        val product = group[lookAlikes.indexOfFirst { it.cardId == card.cardId }]
        if (product == linked) return card
        val row = rows[product] ?: return card
        return card.copy(cardmarketId = product, variants = card.variants.map { v -> withPrices(v, row) })
    }

    private fun withPrices(v: Variant, row: Row): Variant {
        if (v.key == "firstEdition") return v // priced from its own product by TCGdex
        val f = if (v.key == "reverse") row.holo else row.normal
        val price = f[0]?.takeIf { it > 0 } ?: f[2]?.takeIf { it > 0 }
        val labels = listOf(
            R.string.price_label_trend, R.string.price_label_lowest_offer, R.string.price_label_average_sold,
            R.string.price_label_avg1, R.string.price_label_avg7, R.string.price_label_avg30,
        )
        val cm = labels.indices.mapNotNull { i -> f[i]?.takeIf { it > 0 }?.let { PricePoint(PriceSource.CARDMARKET, AppStrings.get(labels[i]), it) } }
        val prices = v.prices.toMutableMap().apply { if (price != null) put(PriceSource.CARDMARKET, price) else remove(PriceSource.CARDMARKET) }
        return v.copy(prices = prices, details = cm + v.details.filter { it.source != PriceSource.CARDMARKET })
    }

    private fun numberOf(c: CardCandidate) = c.number.substringBefore('/').filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

    companion object {
        const val FILE = "CARDMARKET_POKEMON.json"
    }
}
