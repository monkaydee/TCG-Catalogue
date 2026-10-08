package com.monkaydee.tcgcatalogue.data

import androidx.annotation.StringRes
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/** Collector challenges worked out from the collection on the phone; nothing is uploaded or stored. */
object Badges {
    data class Badge(val id: String, @StringRes val title: Int, @StringRes val description: Int, val progress: Int, val goal: Int) {
        val earned: Boolean get() = progress >= goal
    }

    data class Input(
        val cards: List<OwnedCard>,
        val sets: Map<Pair<Game, String>, CardSet>,
        val binders: Int,
        val sold: Int,
        val sealed: Int,
        /** Collection value in EUR (known prices). */
        val valueEur: Double,
    )

    fun all(i: Input): List<Badge> {
        val copies = i.cards.sumOf { it.quantity }
        val slabs = i.cards.filter { it.graded }
        val tens = slabs.filter { it.grade?.toDoubleOrNull() == 10.0 }.sumOf { it.quantity }
        val japanese = i.cards.filter { it.language == "JA" }.sumOf { it.quantity }
        val languages = i.cards.map { it.language }.toSet().size
        val games = i.cards.map { it.game }.toSet().size
        // A set counts as complete when every printed number up to its total is owned.
        val bestSet = i.cards.groupBy { it.game to it.setId }.maxOfOrNull { (key, list) ->
            val total = i.sets[key]?.total ?: 0
            if (total <= 0) 0 else (list.map { Binder.numberKey(it) }.toSet().size * 100 / total).coerceAtMost(100)
        } ?: 0
        val graders = slabs.mapNotNull { it.grader }.toSet().size
        fun b(id: String, title: Int, desc: Int, progress: Int, goal: Int) = Badge(id, title, desc, progress.coerceAtMost(goal), goal)
        return listOf(
            b("first_card", R.string.badge_first_card, R.string.badge_first_card_text, copies, 1),
            b("cards_100", R.string.badge_cards_100, R.string.badge_cards_100_text, copies, 100),
            b("cards_1000", R.string.badge_cards_1000, R.string.badge_cards_1000_text, copies, 1000),
            b("set_complete", R.string.badge_set_complete, R.string.badge_set_complete_text, bestSet, 100),
            b("first_slab", R.string.badge_first_slab, R.string.badge_first_slab_text, slabs.sumOf { it.quantity }, 1),
            b("gem_mint", R.string.badge_gem_mint, R.string.badge_gem_mint_text, tens, 1),
            b("graders", R.string.badge_graders, R.string.badge_graders_text, graders, 3),
            b("japanese", R.string.badge_japanese, R.string.badge_japanese_text, japanese, 10),
            b("languages", R.string.badge_languages, R.string.badge_languages_text, languages, 3),
            b("games", R.string.badge_games, R.string.badge_games_text, games, 3),
            b("binder", R.string.badge_binder, R.string.badge_binder_text, i.binders, 1),
            b("sealed", R.string.badge_sealed, R.string.badge_sealed_text, i.sealed, 1),
            b("first_sale", R.string.badge_first_sale, R.string.badge_first_sale_text, i.sold, 1),
            b("value_1000", R.string.badge_value_1000, R.string.badge_value_1000_text, i.valueEur.toInt(), 1000),
            b("value_10000", R.string.badge_value_10000, R.string.badge_value_10000_text, i.valueEur.toInt(), 10000),
        )
    }
}
