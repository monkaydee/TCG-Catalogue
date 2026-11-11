package com.monkaydee.tcgcatalogue.data

import androidx.annotation.StringRes
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/** How the cards are ordered in the virtual binder. */
enum class BinderSort(@StringRes val label: Int) {
    VALUE_DESC(R.string.binder_sort_value_desc),
    VALUE_ASC(R.string.binder_sort_value_asc),
    NAME_ASC(R.string.binder_sort_name_asc),
    NAME_DESC(R.string.binder_sort_name_desc),
    SET(R.string.binder_sort_set),
}

/** Order of the cards within a set when the binder is sorted by set. */
enum class SetOrder(@StringRes val label: Int) {
    NUMBER(R.string.binder_order_number),
    VALUE_DESC(R.string.binder_sort_value_desc),
    VALUE_ASC(R.string.binder_sort_value_asc),
    NAME_ASC(R.string.binder_sort_name_asc),
    NAME_DESC(R.string.binder_sort_name_desc),
}

/**
 * One binder page: its cards (at most rows × columns) and, sorted by set, the set it shows. An own
 * binder's page also has [pockets], one entry per pocket with gaps as null; [firstSlot] is the
 * number of its first pocket.
 */
data class BinderPage(val cards: List<OwnedCard>, val title: String? = null, val pockets: List<OwnedCard?>? = null, val firstSlot: Int = 0)

object Binder {
    /** The usual 12-pocket page: 4 rows of 3 cards. The default layout. */
    const val CLASSIC = 12
    val GRIDS = listOf(CLASSIC, 3, 6, 9)

    /** Pockets per row and rows per page of a layout ([CLASSIC], or n for n × n). */
    fun columns(grid: Int) = if (grid == CLASSIC) 3 else grid
    fun rows(grid: Int) = if (grid == CLASSIC) 4 else grid
    fun perPage(grid: Int) = columns(grid) * rows(grid)
    fun label(grid: Int) = "${rows(grid)} × ${columns(grid)}"

    /** One entry per copy when every copy has its own pocket, otherwise the rows as they are. */
    fun copies(cards: List<OwnedCard>, spread: Boolean): List<OwnedCard> =
        if (!spread) cards else cards.flatMap { c -> List(c.quantity.coerceAtLeast(1)) { c.copy(quantity = 1) } }

    /**
     * The pages of an own binder from its pockets: up to the page with the last card, plus one empty
     * page to put new cards on. With [spread] each pocket holds one copy; otherwise the whole stack.
     */
    fun pocketPages(slots: List<com.monkaydee.tcgcatalogue.data.db.BinderCard>, cards: Map<Long, OwnedCard>, perPage: Int, spread: Boolean): List<BinderPage> {
        val filled = slots.mapNotNull { s -> cards[s.cardRowId]?.let { s.slot to (if (spread) it.copy(quantity = 1) else it) } }.toMap()
        val pageCount = (filled.keys.maxOrNull()?.let { it / perPage + 1 } ?: 0) + 1
        return List(pageCount) { p ->
            val pockets = List(perPage) { i -> filled[p * perPage + i] }
            BinderPage(pockets.filterNotNull(), pockets = pockets, firstSlot = p * perPage)
        }
    }

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

/** Preset binder covers: a base colour pair and a pattern drawn over it. */
enum class CoverDesign(val key: String, @StringRes val label: Int, val top: Long, val bottom: Long, val pattern: CoverPattern) {
    MIDNIGHT("midnight", R.string.cover_midnight, 0xFF1F2A44, 0xFF0D1220, CoverPattern.LEATHER),
    CRIMSON("crimson", R.string.cover_crimson, 0xFF9C1C2B, 0xFF4A0911, CoverPattern.LEATHER),
    FOREST("forest", R.string.cover_forest, 0xFF2E6B4A, 0xFF10291C, CoverPattern.LEATHER),
    OCEAN("ocean", R.string.cover_ocean, 0xFF1D8FB8, 0xFF0B3550, CoverPattern.WAVES),
    GALAXY("galaxy", R.string.cover_galaxy, 0xFF3B1E6E, 0xFF0A0618, CoverPattern.STARS),
    SUNSET("sunset", R.string.cover_sunset, 0xFFF08A3C, 0xFF8E2C5E, CoverPattern.WAVES),
    GOLD("gold", R.string.cover_gold, 0xFFD9B45A, 0xFF7A5A17, CoverPattern.FOIL),
    CARBON("carbon", R.string.cover_carbon, 0xFF3A3D42, 0xFF15171A, CoverPattern.CARBON),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: MIDNIGHT
    }
}

enum class CoverPattern { LEATHER, WAVES, STARS, FOIL, CARBON }
