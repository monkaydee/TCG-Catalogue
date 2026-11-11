package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionListTest {
    private fun card(id: Long, name: String, price: Double?, grader: String? = null, grade: String? = null, added: Long = id) = OwnedCard(
        id = id, game = Game.POKEMON, cardId = "c$id", variant = "holo", variantLabel = "Holo", name = name, number = "$id/100",
        setId = "s", setName = "Set", price = price, priceCurrency = "EUR", grader = grader, grade = grade, addedAt = added)

    private val cards = listOf(card(1, "Charizard", 300.0), card(2, "Abra", null), card(3, "Mew", 50.0, "PSA", "9"), card(4, "Zapdos", 80.0, "CGC", "10"))
    private fun ids(sort: CollectionSort) = CollectionList.sorted(cards, sort, emptyMap()) { it.price }.map { it.id }

    @Test fun sortsByValueWithUnknownPricesLast() {
        assertEquals(listOf(1L, 4L, 3L, 2L), ids(CollectionSort.VALUE_DESC))
        assertEquals(listOf(3L, 4L, 1L, 2L), ids(CollectionSort.VALUE_ASC))
    }

    @Test fun sortsByNameGradeAndDate() {
        assertEquals(listOf(2L, 1L, 3L, 4L), ids(CollectionSort.NAME_ASC))
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(CollectionSort.GRADE))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(CollectionSort.RECENT))
    }

    @Test fun labelsSlabsOnly() {
        assertEquals("PSA 9", CollectionList.slab(cards[2]))
        assertEquals(null, CollectionList.slab(cards[0]))
    }

    @Test fun classicLayoutIsFourRowsOfThree() {
        assertEquals(3, Binder.columns(Binder.CLASSIC)); assertEquals(4, Binder.rows(Binder.CLASSIC))
        assertEquals(12, Binder.perPage(Binder.CLASSIC)); assertEquals("4 × 3", Binder.label(Binder.CLASSIC))
        assertEquals(Binder.CLASSIC, AppSettings().binderGrid)
    }
}
