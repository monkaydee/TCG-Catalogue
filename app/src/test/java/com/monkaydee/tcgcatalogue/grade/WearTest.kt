package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import org.junit.Assert.*
import org.junit.Test

class WearTest {
    private val w = CardRectifier.W
    private val h = CardRectifier.H
    private fun card(color: Int) = Pixels(w, h, IntArray(w * h) { color })
    private fun patch(card: Pixels, x0: Int, y0: Int, width: Int, height: Int, color: Int) {
        for (y in y0 until y0 + height) for (x in x0 until x0 + width) card.argb[y * card.width + x] = color
    }
    @Test fun uniformDarkBorderChecksAllEightRegionsWithoutDamage() {
        val result = Wear.measure(card(0xFF183C78.toInt()))
        assertEquals((Wear.EDGES + Wear.CORNERS).toSet(), result.zones.keys)
        assertTrue(result.complete)
        result.zones.values.forEach { assertEquals(0.0, it.defects, 0.0) }
    }
    @Test fun detectsWhiteningIndependentlyOnAllFourEdgesAndCorners() {
        for (name in Wear.EDGES + Wear.CORNERS) {
            val c = card(0xFF183C78.toInt())
            val rect = when (name) {
                "T" -> listOf(w / 2 - 30, 1, 60, 6)
                "B" -> listOf(w / 2 - 30, h - 7, 60, 6)
                "L" -> listOf(1, h / 2 - 30, 6, 60)
                "R" -> listOf(w - 7, h / 2 - 30, 6, 60)
                "TL" -> listOf(18, 18, 8, 8)
                "TR" -> listOf(w - 26, 18, 8, 8)
                "BR" -> listOf(w - 26, h - 26, 8, 8)
                else -> listOf(18, h - 26, 8, 8)
            }
            patch(c, rect[0], rect[1], rect[2], rect[3], 0xFFFFFFFF.toInt())
            val r = Wear.measure(c)
            assertTrue("$name whitening must be detected", r.zones.getValue(name).whitening > 0.0)
            assertTrue("$name must prompt inspection even below the model severity bucket", Wear.possibleDamage(r.zones.getValue(name)))
            val opposite = when (name) { "T" -> "B"; "B" -> "T"; "L" -> "R"; "R" -> "L"; "TL" -> "BR"; "TR" -> "BL"; "BR" -> "TL"; else -> "TR" }
            assertEquals("Opposite $opposite must stay unaffected", 0.0, r.zones.getValue(opposite).defects, 0.0)
        }
    }
    @Test fun detectsDarkChipsOnLightBorderWithoutCallingWhiteBorderFullyAssessed() {
        val c = card(0xFFF0F0F0.toInt())
        patch(c, w / 2 - 30, 1, 60, 6, 0xFF101010.toInt())
        val r = Wear.measure(c)
        assertTrue(r.zones.getValue("T").defects > 0)
        assertTrue(Wear.possibleDamage(r.zones.getValue("T")))
        assertEquals(0.0, r.zones.getValue("T").whitening, 0.0)
        assertEquals(Wear.Evidence.LOW_CONTRAST, r.zones.getValue("T").evidence)
        assertFalse(r.complete)
    }
    @Test fun textureAndInsufficientSamplingDoNotBecomeCleanMeasurements() {
        val c = Pixels(w, h, IntArray(w * h) { if ((it % w) % 2 == 0) 0xFF050505.toInt() else 0xFFDDDDDD.toInt() })
        assertEquals(Wear.Evidence.TEXTURED, Wear.measure(c).zones.getValue("T").evidence)
        val tiny = Wear.measure(Pixels(8, 12, IntArray(96) { 0xFF101010.toInt() }))
        assertFalse(tiny.complete)
        assertTrue(tiny.zones.values.all { it.evidence == Wear.Evidence.INSUFFICIENT })
        assertEquals(-1, GradeModel.zoneLevel(tiny.corners.first()))
        assertFalse(Wear.Result(emptyMap()).complete)
    }
}
