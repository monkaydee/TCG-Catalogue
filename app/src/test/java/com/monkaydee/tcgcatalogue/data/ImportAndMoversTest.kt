package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PriceHistory
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.scan.SlabCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndMoversTest {
    @Test fun readsManaBoxCsvWithQuotesFoilAndPrice() {
        val rows = CsvImport.parse("""
            Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition,Language,Purchase price currency
            "Jace, the Mind Sculptor",WWK,Worldwake,31,foil,mythic,2,1,abc,"12.50",false,false,near_mint,en,EUR
            Lightning Bolt,M10,Magic 2010,146,normal,common,4,2,def,,false,false,lightly_played,de,EUR
        """.trimIndent())
        assertEquals(2, rows.size)
        val jace = rows[0]
        assertEquals("Jace, the Mind Sculptor", jace.name); assertEquals("WWK", jace.setCode); assertEquals("31", jace.number)
        assertEquals(2, jace.quantity); assertTrue(jace.foil); assertEquals(12.5, jace.purchase!!, 1e-9); assertEquals("EUR", jace.purchaseCurrency)
        assertEquals("NM", jace.condition); assertEquals("EN", jace.language)
        assertEquals("LP", rows[1].condition); assertEquals("DE", rows[1].language); assertFalse(rows[1].foil)
    }

    @Test fun readsTcgplayerAndSemicolonExportsWithEuropeanNumbers() {
        val tcg = CsvImport.parse("Quantity,Name,Simple Name,Set,Card Number,Set Code,Printing,Condition,Language,Rarity,Product ID,SKU,Price,Price Each\n" +
            "1,Charizard,Charizard,Base Set,4/102,BS,Holofoil,Near Mint,English,Holo Rare,42382,1,\$350.00,\$350.00\n")
        assertEquals("Charizard", tcg[0].name); assertEquals("Base Set", tcg[0].set); assertEquals("4/102", tcg[0].number); assertTrue(tcg[0].foil)
        val ds = CsvImport.parse("Folder Name;Quantity;Card Name;Set Name;Card Number;Condition;Printing;Language;Price Bought\nBinder;3;Glurak;Basis;4;Excellent;Normal;German;\"1.234,56\"\n")
        assertEquals(3, ds[0].quantity); assertEquals("LP", ds[0].condition); assertEquals("DE", ds[0].language); assertEquals(1234.56, ds[0].purchase!!, 1e-9)
    }

    @Test fun readsPlainCardLists() {
        val rows = CsvImport.parse("4 Lightning Bolt (M10) 146\n2x Charizard 4/102\nPikachu\n// sideboard\n1 Black Lotus *F*")
        assertEquals(listOf("Lightning Bolt", "Charizard", "Pikachu", "Black Lotus"), rows.map { it.name })
        assertEquals("M10", rows[0].setCode); assertEquals("146", rows[0].number); assertEquals(4, rows[0].quantity)
        assertEquals("4/102", rows[1].number); assertEquals(2, rows[1].quantity); assertTrue(rows[3].foil)
    }

    @Test fun normalisesCollectorNumbersAndPrintings() {
        assertEquals("4", CsvImport.numberKey("004/102")); assertEquals("TG5", CsvImport.numberKey("TG05")); assertEquals("OP5-60", CsvImport.numberKey("OP05-060"))
        val card = CardCandidate(Game.POKEMON, "base1-4", "Charizard", "4/102", "base1", "Base Set", 102, null, null,
            listOf(Variant("normal", "Normal", emptyMap()), Variant("reverse", "Reverse Holofoil", emptyMap()), Variant("holo", "Holofoil", emptyMap())))
        assertEquals("holo", card.printingFor(ImportRow(1, "Charizard", foil = true)).key)
        assertEquals("reverse", card.printingFor(ImportRow(1, "Charizard", foil = true, printing = "Reverse Foil")).key)
        assertEquals("normal", card.printingFor(ImportRow(1, "Charizard")).key)
    }

    @Test fun readsSlabCodes() {
        assertEquals(SlabCode.Result(null, "74004211"), SlabCode.parse("74004211"))
        assertEquals(SlabCode.Result("PSA", "74004211"), SlabCode.parse("https://www.psacard.com/cert/74004211"))
        assertEquals(SlabCode.Result("CGC", "4049821001"), SlabCode.parse("https://www.cgccards.com/certlookup/4049821001/"))
        assertNull(SlabCode.parse("hello"))
        assertEquals("10", SlabCode.psaGrade("GEM MT 10")); assertEquals("8", SlabCode.psaGrade("NM-MT 8")); assertNull(SlabCode.psaGrade("AUTHENTIC"))
    }

    @Test fun moversCompareWithTheLastPriceAWeekEarlier() {
        val points = listOf(PriceHistory(1, 100, 10.0, "EUR"), PriceHistory(1, 104, 11.0, "EUR"), PriceHistory(1, 108, 15.0, "EUR"),
            PriceHistory(2, 100, 20.0, "USD"), PriceHistory(2, 108, 10.0, "USD"), PriceHistory(3, 106, 5.0, "EUR"), PriceHistory(3, 108, 9.0, "EUR"))
        val moves = Movers.moves(points, 7) { p, c -> if (c == "USD") p * 0.9 else p }
        assertEquals(setOf(1L, 2L), moves.keys)
        assertEquals(50.0, moves.getValue(1).percent, 1e-9); assertEquals(-50.0, moves.getValue(2).percent, 1e-9)
        val (up, down) = Movers.top(moves.values)
        assertEquals(listOf(1L), up.map { it.rowId }); assertEquals(listOf(2L), down.map { it.rowId })
    }

    @Test fun badgesCountProgress() {
        fun card(id: Long, grader: String? = null, grade: String? = null, lang: String = "EN", qty: Int = 1) = OwnedCard(id = id, game = Game.POKEMON, cardId = "c$id",
            variant = "n", variantLabel = "N", name = "C$id", number = "$id/2", setId = "s", setName = "S", quantity = qty, grader = grader, grade = grade, language = lang)
        val set = com.monkaydee.tcgcatalogue.data.db.CardSet(Game.POKEMON, "s", "S", 2)
        val badges = Badges.all(Badges.Input(listOf(card(1, "PSA", "10"), card(2, lang = "JA", qty = 3)), mapOf((Game.POKEMON to "s") to set), 0, 0, 0, 1500.0))
            .associateBy { it.id }
        assertTrue(badges.getValue("first_card").earned); assertTrue(badges.getValue("set_complete").earned); assertTrue(badges.getValue("gem_mint").earned)
        assertTrue(badges.getValue("value_1000").earned); assertFalse(badges.getValue("binder").earned)
        assertEquals(3, badges.getValue("japanese").progress); assertEquals(4, badges.getValue("cards_100").progress)
    }
}
