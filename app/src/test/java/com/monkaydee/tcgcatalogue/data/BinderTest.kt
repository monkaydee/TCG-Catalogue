package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import org.junit.Assert.assertEquals
import org.junit.Test

class BinderTest {
    private fun card(id: Long, name: String, number: String, set: String, price: Double) = OwnedCard(
        id = id, game = Game.POKEMON, cardId = "$set-$number", variant = "normal", variantLabel = "Normal",
        name = name, number = number, setId = set, setName = set.uppercase(), price = price,
    )

    private val cards = listOf(
        card(1, "Pikachu", "025/165", "sv03.5", 2.0),
        card(2, "Charizard ex", "199/165", "sv03.5", 120.0),
        card(3, "Bulbasaur", "001/165", "sv03.5", 0.5),
        card(4, "Zekrom", "115/113", "bw11", 180.0),
        card(5, "alakazam", "1/102", "base1", 40.0),
    )
    private val sets = mapOf(
        (Game.POKEMON to "sv03.5") to CardSet(Game.POKEMON, "sv03.5", "151", 165, releaseDate = "2023-09-22"),
        (Game.POKEMON to "bw11") to CardSet(Game.POKEMON, "bw11", "Legendary Treasures", 113, releaseDate = "2013-11-08"),
        (Game.POKEMON to "base1") to CardSet(Game.POKEMON, "base1", "Base Set", 102, releaseDate = "1999-01-09"),
    )
    private val value = { c: OwnedCard -> c.price ?: 0.0 }

    private fun ids(sort: BinderSort, within: SetOrder = SetOrder.NUMBER) = Binder.sorted(cards, sort, within, sets, value).map { it.id }

    @Test fun byValue() {
        assertEquals(listOf(4L, 2, 5, 1, 3), ids(BinderSort.VALUE_DESC))
        assertEquals(listOf(3L, 1, 5, 2, 4), ids(BinderSort.VALUE_ASC))
    }

    @Test fun byNameIgnoresCase() {
        assertEquals(listOf(5L, 3, 2, 1, 4), ids(BinderSort.NAME_ASC))
        assertEquals(listOf(4L, 1, 2, 3, 5), ids(BinderSort.NAME_DESC))
    }

    @Test fun bySetOldestFirstThenWithinSet() {
        assertEquals(listOf(5L, 4, 3, 1, 2), ids(BinderSort.SET, SetOrder.NUMBER))
        assertEquals(listOf(5L, 4, 2, 1, 3), ids(BinderSort.SET, SetOrder.VALUE_DESC))
        assertEquals(listOf(5L, 4, 3, 2, 1), ids(BinderSort.SET, SetOrder.NAME_ASC))
    }

    @Test fun everySetStartsOnANewPage() {
        val pages = Binder.pages(cards, BinderSort.SET, SetOrder.NUMBER, 2, sets, value)
        assertEquals(listOf("Base Set", "Legendary Treasures", "151", "151"), pages.map { it.title })
        assertEquals(listOf(1, 1, 2, 1), pages.map { it.cards.size })
    }

    @Test fun pagesHoldTheGrid() {
        val many = (1..20L).map { card(it, "Card $it", "$it/165", "sv03.5", it.toDouble()) }
        val pages = Binder.pages(many, BinderSort.VALUE_DESC, SetOrder.NUMBER, 9, sets, value)
        assertEquals(listOf(9, 9, 2), pages.map { it.cards.size })
    }

    @Test fun numberKeys() {
        assertEquals(Binder.numberKey(card(1, "a", "TG05/TG30", "x", 0.0)), "TG00005")
        assertEquals(Binder.numberKey(card(1, "a", "OP05-060", "x", 0.0)), "00060")
        assertEquals(Binder.numberKey(card(1, "a", "DMU 107", "x", 0.0)), "00107")
    }
}
