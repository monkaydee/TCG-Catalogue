package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.DeckCard
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/**
 * Basic deck checks per game: card count and copies per card. Format legality (banned cards,
 * rotation) is not checked.
 */
object DeckCheck {
    sealed class Problem {
        /** The deck must have exactly [needed] cards. */
        data class Total(val needed: Int, val has: Int) : Problem()
        /** The deck needs at least [needed] cards. */
        data class Minimum(val needed: Int, val has: Int) : Problem()
        /** More than [max] copies of these cards. */
        data class Copies(val max: Int, val names: List<String>) : Problem()
    }

    private val BASIC_ENERGY = Regex("""(?i)^(basic\s+)?(grass|fire|water|lightning|psychic|fighting|darkness|metal|fairy)\s+energy$""")
    private val BASIC_LAND = Regex("""(?i)^(snow-covered\s+)?(plains|island|swamp|mountain|forest|wastes)$""")

    fun problems(game: Game, cards: List<DeckCard>): List<Problem> {
        val total = cards.sumOf { it.quantity }
        fun copies(max: Int, key: (DeckCard) -> String, exempt: (String) -> Boolean): Problem? =
            cards.groupBy(key).filter { (k, list) -> !exempt(list.first().name) && list.sumOf { it.quantity } > max }
                .map { it.value.first().name }.takeIf { it.isNotEmpty() }?.let { Problem.Copies(max, it.sorted()) }
        return when (game) {
            Game.POKEMON -> listOfNotNull(
                Problem.Total(60, total).takeIf { total != 60 },
                copies(4, { it.name.lowercase() }) { BASIC_ENERGY.matches(it) },
            )
            Game.MAGIC -> listOfNotNull(
                Problem.Minimum(60, total).takeIf { total < 60 },
                copies(4, { it.name.lowercase() }) { BASIC_LAND.matches(it) },
            )
            // 50 cards plus the Leader.
            Game.ONE_PIECE -> listOfNotNull(
                Problem.Total(51, total).takeIf { total != 51 },
                copies(4, { it.number.uppercase() }) { false },
            )
            else -> emptyList()
        }
    }

    data class Summary(val cards: Int, val value: Double, val unknownPrices: Int, val missing: Int, val missingCost: Double, val missingUnknown: Int)

    /** Deck value and what is still missing compared with the collection (owned copies of the same card in any condition). */
    fun summary(cards: List<DeckCard>, owned: List<OwnedCard>, game: Game, price: (DeckCard) -> Double?): Summary {
        val have = owned.filter { it.game == game }.groupBy { it.cardId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        var value = 0.0; var unknown = 0; var missing = 0; var missingCost = 0.0; var missingUnknown = 0
        for (c in cards) {
            val p = price(c)
            if (p == null) unknown += c.quantity else value += p * c.quantity
            val lack = (c.quantity - (have[c.cardId] ?: 0)).coerceAtLeast(0)
            missing += lack
            if (lack > 0) { if (p == null) missingUnknown += lack else missingCost += p * lack }
        }
        return Summary(cards.sumOf { it.quantity }, value, unknown, missing, missingCost, missingUnknown)
    }

    /** "4 Pikachu ex (Scarlet & Violet) 63" lines, for sharing or another app. */
    fun text(cards: List<DeckCard>): String = cards.sortedBy { it.name }.joinToString("\n") { c ->
        "${c.quantity} ${c.name}" + (if (c.setName.isNotBlank()) " (${c.setName})" else "") + (if (c.number.isNotBlank()) " ${c.number}" else "")
    }
}
