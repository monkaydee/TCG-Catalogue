package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.DeckCard
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckAndShareTest {
    private fun dc(id: String, name: String, qty: Int, price: Double? = 1.0, number: String = id) = DeckCard(1, id, name, number, "Set", null, qty, price, "EUR")

    @Test fun pokemonDecksNeedSixtyCardsAndFourCopiesExceptBasicEnergy() {
        val deck = listOf(dc("a", "Pikachu", 4), dc("b", "Pikachu", 1, number = "b2"), dc("e", "Basic Lightning Energy", 20), dc("f", "Lightning Energy", 10))
        val problems = DeckCheck.problems(Game.POKEMON, deck)
        assertTrue(problems.contains(DeckCheck.Problem.Total(60, 35)))
        assertTrue(problems.contains(DeckCheck.Problem.Copies(4, listOf("Pikachu"))))
        assertEquals(2, problems.size)
        assertTrue(DeckCheck.problems(Game.MAGIC, listOf(dc("p", "Plains", 30), dc("s", "Snow-Covered Island", 30))).isEmpty())
        assertTrue(DeckCheck.problems(Game.NARUTO, deck).isEmpty())
    }

    @Test fun summaryComparesWithOwnedCopies() {
        val owned = listOf(OwnedCard(game = Game.POKEMON, cardId = "a", variant = "n", variantLabel = "N", name = "Pikachu", number = "a", setId = "s", setName = "S", quantity = 3))
        val s = DeckCheck.summary(listOf(dc("a", "Pikachu", 4, 2.0), dc("z", "Mew", 2, null)), owned, Game.POKEMON) { it.price }
        assertEquals(6, s.cards); assertEquals(8.0, s.value, 1e-9); assertEquals(2, s.unknownPrices)
        assertEquals(3, s.missing); assertEquals(2.0, s.missingCost, 1e-9); assertEquals(2, s.missingUnknown)
        assertEquals("2 Mew (Set) z\n4 Pikachu (Set) a", DeckCheck.text(listOf(dc("a", "Pikachu", 4), dc("z", "Mew", 2))))
    }

    @Test fun populationOrderAndGemRate() {
        val by = mapOf("9" to 30, "10" to 10, "8.5" to 5, "Authentic" to 1, "9 (Q)" to 2)
        assertEquals(listOf("10", "9", "9 (Q)", "8.5", "Authentic"), Population.ordered(by).map { it.first })
        assertEquals(20.0, Population.gemRate(50, by)!!, 1e-9)
    }

    @Test fun sharedPageEscapesTextAndCanLeaveOutPrices() {
        val card = OwnedCard(game = Game.POKEMON, cardId = "a", variant = "n", variantLabel = "N", name = "<script>Mew</script>", number = "151", setId = "s",
            setName = "Base & Co", imageUrl = "https://img.example/a.png", grader = "PSA", grade = "10")
        val withPrices = CollectionShare.html("My cards", "Cards: 1", listOf(card), { "€5.00" }, "€5.00")
        assertFalse(withPrices.contains("<script>Mew")); assertTrue(withPrices.contains("&lt;script&gt;Mew"))
        assertTrue(withPrices.contains("Base &amp; Co")); assertTrue(withPrices.contains("PSA 10")); assertTrue(withPrices.contains("€5.00"))
        assertFalse(CollectionShare.html("My cards", "Cards: 1", listOf(card), null, null).contains("€"))
    }
}
