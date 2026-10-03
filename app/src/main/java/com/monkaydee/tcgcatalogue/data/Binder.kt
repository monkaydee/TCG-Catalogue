package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/** How the cards are ordered in the virtual binder. */
enum class BinderSort(val label: String) {
    VALUE_DESC("Value · highest first"),
    VALUE_ASC("Value · lowest first"),
    NAME_ASC("Name · A–Z"),
    NAME_DESC("Name · Z–A"),
    SET("By set"),
}

/** Order of the cards within a set when the binder is sorted by set. */
enum class SetOrder(val label: String) {
    NUMBER("Card number"),
    VALUE_DESC("Value · highest first"),
    VALUE_ASC("Value · lowest first"),
    NAME_ASC("Name · A–Z"),
    NAME_DESC("Name · Z–A"),
}

/** One binder page: its cards (at most rows × columns) and, sorted by set, the set it shows. */
data class BinderPage(val cards: List<OwnedCard>, val title: String? = null)

object Binder {
    val GRIDS = listOf(3, 6, 9)

    /** Sort key of a collector number: "TG05/TG30" -> "TG00005", "OP05-060" -> "00060", "25/102" -> "00025". */
    fun numberKey(c: OwnedCard): String {
        val n = c.number.substringBefore('/').substringAfterLast('-').substringAfterLast(' ')
        val digits = n.filter { it.isDigit() }.padStart(5, '0')
        return n.filter { it.isLetter() } + digits
    }

    /** Cards in binder order, without page breaks. */
    fun sorted(
        cards: List<OwnedCard>,
        sort: BinderSort,
        within: SetOrder,
        sets: Map<Pair<Game, String>, CardSet>,
        value: (OwnedCard) -> Double,
    ): List<OwnedCard> = pages(cards, sort, within, Int.MAX_VALUE, sets, value).flatMap { it.cards }

    /**
     * The binder's pages with [perPage] pockets each. Sorted by set, every set starts on a new
     * page, oldest set first (like filling a binder set by set).
     */
    fun pages(
        cards: List<OwnedCard>,
        sort: BinderSort,
        within: SetOrder,
        perPage: Int,
        sets: Map<Pair<Game, String>, CardSet>,
        value: (OwnedCard) -> Double,
    ): List<BinderPage> {
        val name = compareBy<OwnedCard, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { numberKey(it) }
        if (sort != BinderSort.SET) {
            val ordered = when (sort) {
                BinderSort.VALUE_DESC -> cards.sortedWith(compareByDescending(value).then(name))
                BinderSort.VALUE_ASC -> cards.sortedWith(compareBy(value).then(name))
                BinderSort.NAME_ASC -> cards.sortedWith(name)
                else -> cards.sortedWith(name.reversed())
            }
            return ordered.chunked(perPage).map { BinderPage(it) }
        }
        val inSet: Comparator<OwnedCard> = when (within) {
            SetOrder.NUMBER -> compareBy<OwnedCard> { numberKey(it) }.then(name)
            SetOrder.VALUE_DESC -> compareByDescending(value).then(name)
            SetOrder.VALUE_ASC -> compareBy(value).then(name)
            SetOrder.NAME_ASC -> name
            SetOrder.NAME_DESC -> name.reversed()
        }
        // Sets without a known release date go last.
        val groups = cards.groupBy { it.game to it.setId }.entries.sortedWith(
            compareBy<Map.Entry<Pair<Game, String>, List<OwnedCard>>> { sets[it.key]?.releaseDate ?: "9999" }
                .thenBy { it.key.first.ordinal }
                .thenBy { setName(it.key, it.value, sets) },
        )
        return groups.flatMap { (key, list) ->
            val title = setName(key, list, sets)
            list.sortedWith(inSet).chunked(perPage).map { BinderPage(it, title) }
        }
    }

    private fun setName(key: Pair<Game, String>, cards: List<OwnedCard>, sets: Map<Pair<Game, String>, CardSet>) =
        sets[key]?.name?.takeIf { it.isNotBlank() } ?: cards.first().setName.ifBlank { key.second }
}
