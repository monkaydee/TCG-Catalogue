package com.monkaydee.tcgcatalogue.data

import androidx.annotation.StringRes
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/** Orders of the scrollable collection list. */
enum class CollectionSort(@StringRes val label: Int) {
    VALUE_DESC(R.string.binder_sort_value_desc),
    VALUE_ASC(R.string.binder_sort_value_asc),
    NAME_ASC(R.string.binder_sort_name_asc),
    NAME_DESC(R.string.binder_sort_name_desc),
    SET(R.string.collection_sort_set),
    RECENT(R.string.collection_sort_recent),
    GRADE(R.string.collection_sort_grade),
    RISERS(R.string.collection_sort_risers),
    FALLERS(R.string.collection_sort_fallers),
}

object CollectionList {
    /** "PSA 10", "BGS 9.5 Black Label"; null for a raw card. */
    fun slab(card: OwnedCard): String? = if (!card.graded) null else listOfNotNull(card.grader, card.grade, card.gradeQualifier).joinToString(" ")

    /** Cards in list order. Cards without a known price count as 0 and come last when sorted by value. */
    fun sorted(cards: List<OwnedCard>, sort: CollectionSort, sets: Map<Pair<Game, String>, CardSet>,
               change: (OwnedCard) -> Double? = { null }, value: (OwnedCard) -> Double?): List<OwnedCard> {
        val name = compareBy<OwnedCard, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { Binder.numberKey(it) }
        return when (sort) {
            CollectionSort.VALUE_DESC -> cards.sortedWith(compareBy<OwnedCard> { value(it) == null }.thenByDescending { value(it) ?: 0.0 }.then(name))
            CollectionSort.VALUE_ASC -> cards.sortedWith(compareBy<OwnedCard> { value(it) == null }.thenBy { value(it) ?: 0.0 }.then(name))
            CollectionSort.NAME_ASC -> cards.sortedWith(name)
            CollectionSort.NAME_DESC -> cards.sortedWith(name.reversed())
            CollectionSort.SET -> cards.sortedWith(
                compareBy<OwnedCard> { sets[it.game to it.setId]?.releaseDate ?: "9999" }.thenBy { it.setName }.thenBy { Binder.numberKey(it) }.then(name))
            CollectionSort.RECENT -> cards.sortedWith(compareByDescending<OwnedCard> { it.addedAt }.then(name))
            // Slabs first, highest grade first; raw cards after them by value.
            // Price change in percent over the last week; cards without enough history last.
            CollectionSort.RISERS -> cards.sortedWith(compareBy<OwnedCard> { change(it) == null }.thenByDescending { change(it) ?: 0.0 }.then(name))
            CollectionSort.FALLERS -> cards.sortedWith(compareBy<OwnedCard> { change(it) == null }.thenBy { change(it) ?: 0.0 }.then(name))
            CollectionSort.GRADE -> cards.sortedWith(
                compareBy<OwnedCard> { !it.graded }.thenByDescending { it.grade?.replace(',', '.')?.toDoubleOrNull() ?: 0.0 }
                    .thenByDescending { value(it) ?: 0.0 }.then(name))
        }
    }
}
