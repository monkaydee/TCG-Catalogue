package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CardOutlineTest {
    /** A blue card back on a grey table of the same brightness: only the colour tells them apart. */
    @Test
    fun findsABlueBackOnAGreyTable() {
        val w = 540; val h = 960
        val x0 = 90; val x1 = 450; val y0 = 230; val y1 = 733 // 360 x 503 ≈ 63 x 88
        val table = 0xFF5C5C5C.toInt() // brightness 92
        val blue = 0xFF3C5AC8.toInt() // (60, 90, 200): brightness 94
        val line = 0xFFC8A0A0.toInt()
        val argb = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                x !in x0 until x1 || y !in y0 until y1 -> table
                (x - x0 + 2 * (y - y0)) % 97 < 2 -> line // printed pattern lines
                else -> blue
            }
        }
        val guide = Quad(Pt(60.0, 190.0), Pt(480.0, 190.0), Pt(480.0, 776.0), Pt(60.0, 776.0))
        val q = CardRectifier.findQuadIn(Pixels(w, h, argb), guide)
        assertNotNull(q)
        val expected = listOf(Pt(x0.toDouble(), y0.toDouble()), Pt(x1.toDouble(), y0.toDouble()), Pt(x1.toDouble(), y1.toDouble()), Pt(x0.toDouble(), y1.toDouble()))
        q!!.corners.zip(expected).forEach { (a, b) -> assertTrue("$a vs $b", abs(a.x - b.x) < 3 && abs(a.y - b.y) < 3) }
    }

    /**
     * A Pokémon-style back: a dark navy border round light artwork, on a grey table. The border's
     * inner edge is far stronger than the cut, but the outline must be the cut.
     */
    @Test
    fun takesTheCutNotTheBordersInnerEdge() {
        val w = 540; val h = 960
        val x0 = 90; val x1 = 450; val y0 = 230; val y1 = 733
        val b = 16 // border width, 4.5 % of the card
        val table = 0xFF3C3C40.toInt()
        val navy = 0xFF16265F.toInt()
        val art = 0xFFB4C8F0.toInt()
        val raw = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                x !in x0 until x1 || y !in y0 until y1 -> table
                x < x0 + b || x >= x1 - b || y < y0 + b || y >= y1 - b -> navy
                else -> art
            }
        }
        // a little blur, as in a phone photo
        val argb = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            var r = 0; var g = 0; var bl = 0; var n = 0
            for (dy in -2..2) for (dx in -2..2) {
                val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1)
                val c = raw[yy * w + xx]; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF; n++
            }
            (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (bl / n)
        }
        val guide = Quad(Pt(60.0, 190.0), Pt(480.0, 190.0), Pt(480.0, 776.0), Pt(60.0, 776.0))
        val q = CardRectifier.findQuadIn(Pixels(w, h, argb), guide)
        assertNotNull(q)
        val expected = listOf(Pt(x0.toDouble(), y0.toDouble()), Pt(x1.toDouble(), y0.toDouble()), Pt(x1.toDouble(), y1.toDouble()), Pt(x0.toDouble(), y1.toDouble()))
        q!!.corners.zip(expected).forEach { (a, e) -> assertTrue("$a vs $e", abs(a.x - e.x) < 3 && abs(a.y - e.y) < 3) }
    }
}
