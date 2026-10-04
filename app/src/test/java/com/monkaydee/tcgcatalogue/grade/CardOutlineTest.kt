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
}
