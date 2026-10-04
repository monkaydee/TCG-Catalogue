package com.monkaydee.tcgcatalogue.scan

import com.monkaydee.tcgcatalogue.data.db.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRecognitionTest {
    private fun lines(vararg text: String) = text.mapIndexed { i, t -> OcrLine(t, top = i / text.size.toFloat(), height = 0.02f) }

    private val index = mapOf(
        Game.DRAGON_BALL_FW to { c: String -> c in setOf("FB01-139", "FS01-01") },
        Game.UNION_ARENA to { c: String -> c == "UE01BT/BLC-1-001" },
        Game.WEISS_SCHWARZ to { c: String -> c in setOf("HOL/W91-001", "HOL/W91-001SP") },
    )

    @Test fun magicModernCollectorLine() {
        val hit = CardTextParser.parse(lines("Sheoldred, the Apocalypse", "Legendary Creature", "0107 M", "DMU • EN  Chris Rahn"))
        assertEquals(ScanHit.Magic("dmu", "107", "Sheoldred, the Apocalypse"), hit)
    }

    @Test fun magicOlderCollectorLineIsNotPokemon() {
        val hit = CardTextParser.parse(lines("Lightning Bolt", "141/274 C", "M10 · EN"))
        assertEquals("m10", (hit as ScanHit.Magic).set)
        assertEquals("141", hit.number)
    }

    @Test fun magicByNameWhenChosen() {
        val hit = CardTextParser.parse(lines("Counterspell", "Instant"), Game.MAGIC)
        assertEquals(ScanHit.Magic(null, null, "Counterspell"), hit)
    }

    @Test fun indexedCodes() {
        assertEquals(ScanHit.Indexed(Game.DRAGON_BALL_FW, "FB01-139"), CardTextParser.parse(lines("Son Goku", "FB0I-139".replace('I', '1')), indexes = index))
        assertEquals(ScanHit.Indexed(Game.UNION_ARENA, "UE01BT/BLC-1-001"), CardTextParser.parse(lines("UE01BT / BLC-1-001"), indexes = index))
        assertEquals(ScanHit.Indexed(Game.WEISS_SCHWARZ, "HOL/W91-001SP"), CardTextParser.parse(lines("HOL/W91-001SP SP"), indexes = index))
        // OCR read the zero as an O
        assertEquals(ScanHit.Indexed(Game.DRAGON_BALL_FW, "FB01-139"), CardTextParser.parse(lines("FBO1-139"), indexes = index))
        // A Japanese Union Arena print finds its English counterpart
        assertEquals(ScanHit.Indexed(Game.UNION_ARENA, "UE01BT/BLC-1-001"), CardTextParser.parse(lines("UA01BT/BLC-1-001"), indexes = index))
    }

    @Test fun unknownCodesAreIgnored() {
        assertNull(CardTextParser.parse(lines("FB99-999"), Game.DRAGON_BALL_FW, index))
    }

    @Test fun psaLabel() {
        val g = CardTextParser.parseGrade(lines("2013 POKEMON BW LEGENDARY", "ZEKROM", "#115", "GEM MT 10", "85476123", "PSA"))
        assertEquals(GradeInfo("PSA", "10", null, "85476123"), g)
        assertEquals("PSA 10", g!!.label)
    }

    @Test fun psaLabelWithGradeOnTwoLinesAndNoLogoText() {
        val g = CardTextParser.parseGrade(lines("1999 POKEMON JUNGLE", "FLAREON-HOLO", "#3", "NM-MT", "8", "74004211"))
        assertEquals(GradeInfo("PSA", "8", null, "74004211"), g)
    }

    @Test fun psaWordingWithoutLogo() {
        assertEquals("PSA", CardTextParser.parseGrade(lines("CHESPIN", "GEM MT 10", "12345678"))!!.grader)
        assertEquals("9", CardTextParser.parseGrade(lines("PSA", "MINT 9"))!!.grade)
    }

    @Test fun bgsBlackLabelIgnoresSubgrades() {
        val g = CardTextParser.parseGrade(lines("BECKETT", "CENTERING 10", "CORNERS 10", "EDGES 10", "SURFACE 10", "PRISTINE 10", "0012345678"))!!
        assertEquals("BGS", g.grader)
        assertEquals("10", g.grade)
        assertEquals("Black Label", g.qualifier)
    }

    @Test fun bgsNineAndAHalf() {
        val g = CardTextParser.parseGrade(lines("BECKETT GRADING SERVICES", "CENTERING 9.5", "9.5 GEM MINT"))!!
        assertEquals("9.5", g.grade)
        assertNull(g.qualifier)
    }

    @Test fun cgcPristineAndBareNumber() {
        assertEquals(GradeInfo("CGC", "10", "Pristine", "4123456789"), CardTextParser.parseGrade(lines("CGC", "PRISTINE 10", "4123456789")))
        assertEquals("8.5", CardTextParser.parseGrade(lines("CGC TRADING CARDS", "8.5"))!!.grade)
    }

    @Test fun otherGraders() {
        assertEquals("SGC", CardTextParser.parseGrade(lines("SGC", "10 PRISTINE"))!!.grader)
        assertEquals("AOG", CardTextParser.parseGrade(lines("AOG", "GEM MINT 10"))!!.grader)
        assertEquals("GSG", CardTextParser.parseGrade(lines("GSG", "MINT 9"))!!.grader)
    }

    @Test fun misreadCodesAndNameCheck() {
        assertTrue("OP05-080" in CardTextParser.misreadVariants("OP05-060"))
        assertTrue("OP05-068" in CardTextParser.misreadVariants("OP05-060"))
        assertTrue(CardTextParser.nameOnCard("Monkey.D.Luffy (060)", listOf("LEADER", "Monkey.D.Luffy", "OP05-060")))
        assertTrue(CardTextParser.nameOnCard("Monkey.D.Luffy", listOf("Monkey.D.Lufy")))
        assertTrue(!CardTextParser.nameOnCard("Brannew", listOf("Monkey.D.Luffy", "OP05-060")))
    }

    @Test fun smallGradersAndGermanLabels() {
        assertEquals(GradeInfo("GSG", "8.5", null, null), CardTextParser.parseGrade(lines("GSG", "ZEKROM", "NM-MT+ 8.5")))
        assertEquals("8.5", CardTextParser.parseGrade(lines("GSG GRADING", "Zekrom 115/113", "8,5"))!!.grade)
        assertEquals("8.5", CardTextParser.parseGrade(lines("Zekrom", "NOTE: 8,5", "GRADING"))!!.grade)
        // Company not readable: still graded, the user picks the company.
        val unknown = CardTextParser.parseGrade(lines("ZEKROM 115/113", "NEAR MINT-MINT+", "8.5"))!!
        assertEquals("8.5", unknown.grade)
        assertNull(unknown.grader)
    }

    @Test fun rawCardsAreNotGraded() {
        assertNull(CardTextParser.parseGrade(lines("Basic", "Pikachu", "60 HP", "Gnaw 10", "MEW EN 025/165")))
        assertNull(CardTextParser.parseGrade(lines("Luffy & Ace", "ST30-001", "[DON!! x2] This Leader gains +1000 power")))
        assertNull(CardTextParser.parseGrade(lines("Charizard ex", "HP 330", "Burning Darkness 180+")))
        assertNull(CardTextParser.parseGrade(lines("Snorlax", "Rest", "Heal 30 damage", "2/2", "Flip a coin. 10")))
    }
}
