package com.monkaydee.tcgcatalogue.scan

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.OnePieceApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextParserTest {
    private fun lines(vararg text: String) = text.mapIndexed { i, t -> OcrLine(t, top = i / text.size.toFloat(), height = 0.02f) }

    @Test fun onePieceCode() {
        assertEquals(ScanHit.OnePiece("OP05-060"), CardTextParser.parse(lines("Monkey.D.Luffy", "L OP05-060")))
    }

    @Test fun onePieceCodeWithOcrConfusion() {
        assertEquals(ScanHit.OnePiece("OP05-060"), CardTextParser.parse(lines("0P05-O60")))
        assertEquals(ScanHit.OnePiece("ST01-001"), CardTextParser.parse(lines("ST01 - 001")))
        assertEquals(ScanHit.OnePiece("PRB01-001"), CardTextParser.parse(lines("PRB01-001")))
        assertEquals(ScanHit.OnePiece("EB01-012"), CardTextParser.parse(lines("SR EB01-012")))
    }

    @Test fun onePiecePromo() {
        assertEquals(ScanHit.OnePiece("P-001"), CardTextParser.parse(lines("Monkey.D.Luffy", "PR P-001")))
    }

    @Test fun pokemonNumber() {
        val hit = CardTextParser.parse(
            listOf(
                OcrLine("Basic", 0.03f, 0.015f),
                OcrLine("Pikachu", 0.05f, 0.04f),
                OcrLine("60 HP", 0.05f, 0.03f),
                OcrLine("Illus. Naoyo Kimura", 0.93f, 0.01f),
                OcrLine("MEW EN 025/165 •", 0.95f, 0.01f),
            ),
        ) as ScanHit.Pokemon
        assertEquals("025", hit.number)
        assertEquals(165, hit.total)
        assertEquals("Pikachu", hit.nameGuess)
    }

    @Test fun pokemonTrainerGallery() {
        val hit = CardTextParser.parse(lines("Flareon", "TG01/TG30")) as ScanHit.Pokemon
        assertEquals("TG01", hit.number)
        assertEquals(30, hit.total)
    }

    @Test fun pokemonSecretRareAndSpaces() {
        val hit = CardTextParser.parse(lines("Charizard ex", "199 / 165")) as ScanHit.Pokemon
        assertEquals("199", hit.number)
    }

    @Test fun ignoresNoise() {
        assertNull(CardTextParser.parse(lines("Flip a coin. If heads, 1/2 damage", "©2023 Pokémon/Nintendo")))
    }

    @Test fun filterRespected() {
        assertNull(CardTextParser.parse(lines("OP05-060"), Game.POKEMON))
        assertNull(CardTextParser.parse(lines("025/165"), Game.ONE_PIECE))
    }

    @Test fun binderPageFindsEveryCard() {
        val hits = CardTextParser.parseAll(lines("Pikachu", "025/165", "Bulbasaur 001/165", "Charmander", "004/165", "025/165"))
        assertEquals(listOf("025", "001", "004").toSet(), hits.map { (it as ScanHit.Pokemon).number }.toSet())
        assertTrue(hits.all { (it as ScanHit.Pokemon).nameGuess == null })
    }

    @Test fun multipleOnePieceCards() {
        val hits = CardTextParser.parseAll(lines("OP05-060 L", "ST01-012", "OP05-060"))
        assertEquals(listOf(ScanHit.OnePiece("OP05-060"), ScanHit.OnePiece("ST01-012")), hits)
    }

    @Test fun singleCardKeepsName() {
        val hits = CardTextParser.parseAll(listOf(OcrLine("Pikachu", 0.05f, 0.04f), OcrLine("025/165", 0.95f, 0.01f)))
        assertEquals(listOf(ScanHit.Pokemon("025", 165, "Pikachu")), hits)
    }

    @Test fun readsTheSetCodeOfScarletAndViolet() {
        val hit = CardTextParser.parseAll(listOf(OcrLine("Meowscarada ex", 0.05f, 0.04f), OcrLine("G PAL EN 015/193", 0.95f, 0.01f))).single() as ScanHit.Pokemon
        assertEquals("PAL", hit.setCode)
        assertEquals("015", hit.number)
        assertEquals(193, hit.total)
    }

    @Test fun noSetCodeOnOlderCards() {
        val hit = CardTextParser.parseAll(listOf(OcrLine("Zekrom", 0.05f, 0.04f), OcrLine("115/113", 0.95f, 0.01f))).single() as ScanHit.Pokemon
        assertNull(hit.setCode)
        assertTrue(!hit.firstEdition)
    }

    @Test fun readsTheFirstEditionStamp() {
        val hit = CardTextParser.parseAll(listOf(OcrLine("Charizard", 0.05f, 0.04f), OcrLine("EDITION", 0.55f, 0.01f), OcrLine("4/102", 0.95f, 0.01f))).single() as ScanHit.Pokemon
        assertTrue(hit.firstEdition)
    }

    @Test fun similarityToleratesOcrErrors() {
        assertTrue(CardTextParser.similarity("Pikachu", "Pikachu") == 1.0)
        assertTrue(CardTextParser.similarity("Pikaehu", "Pikachu") > 0.8)
        assertTrue(CardTextParser.similarity("Bulbasaur", "Pikachu") < 0.3)
    }

    @Test fun onePieceNames() {
        assertEquals("Monkey.D.Luffy", OnePieceApi.baseName("Monkey.D.Luffy (060) (Alternate Art)"))
        assertEquals("Alternate Art", OnePieceApi.variantLabel("Monkey.D.Luffy (060) (Alternate Art)"))
        assertEquals("", OnePieceApi.variantLabel("Monkey.D.Luffy (060)"))
        assertEquals("OP-05", OnePieceApi.expectedSetId("OP05-060"))
        assertEquals("PRB-01", OnePieceApi.expectedSetId("PRB01-001"))
        assertEquals("P", OnePieceApi.expectedSetId("P-001"))
    }
}
