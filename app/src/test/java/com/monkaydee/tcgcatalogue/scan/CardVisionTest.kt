package com.monkaydee.tcgcatalogue.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardVisionTest {
    /** A made-up artwork: coloured blocks on a coloured ground, [w]×[h]. */
    private fun art(seed: Int, w: Int = 126, h: Int = 176): Pixels {
        val r = Random(seed)
        val px = IntArray(w * h)
        val base = (0xFF shl 24) or (r.nextInt(256) shl 16) or (r.nextInt(256) shl 8) or r.nextInt(256)
        px.fill(base)
        repeat(14) {
            val c = (0xFF shl 24) or (r.nextInt(256) shl 16) or (r.nextInt(256) shl 8) or r.nextInt(256)
            val x0 = r.nextInt(w); val y0 = r.nextInt(h)
            val x1 = (x0 + r.nextInt(10, w / 2)).coerceAtMost(w); val y1 = (y0 + r.nextInt(10, h / 2)).coerceAtMost(h)
            for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = c
        }
        return Pixels(w, h, px)
    }

    /** [card] placed in a bigger noisy picture at ([x], [y]), [scale]d and dimmed. */
    private fun photo(card: Pixels, x: Int, y: Int, scale: Double, light: Double): Pixels {
        val w = 300; val h = 400
        val r = Random(7)
        val px = IntArray(w * h) { (0xFF shl 24) or (r.nextInt(60, 120) shl 16) or (r.nextInt(60, 120) shl 8) or r.nextInt(60, 120) }
        val cw = (card.width * scale).toInt(); val ch = (card.height * scale).toInt()
        for (j in 0 until ch) for (i in 0 until cw) {
            val c = card.argb[(j * card.height / ch) * card.width + i * card.width / cw]
            fun dim(v: Int) = (v * light).toInt().coerceIn(0, 255)
            px[(y + j) * w + x + i] = (0xFF shl 24) or (dim((c shr 16) and 255) shl 16) or (dim((c shr 8) and 255) shl 8) or dim(c and 255)
        }
        return Pixels(w, h, px)
    }

    @Test fun findsTheArtworkAnywhereInThePhoto() {
        val arts = listOf(art(1), art(2), art(3))
        val refs = arts.map { CardVision.reference(it)!! }
        for ((i, a) in arts.withIndex()) {
            val scene = photo(a, 40 + i * 30, 60, 1.3, 0.7)
            val decision = CardVision.decide(CardVision.scores(scene, refs, 0.25)!!, refs)!!
            assertEquals(i, decision.index)
            assertTrue(decision.sure)
        }
    }

    @Test fun theSamePictureTwiceIsTheOriginalPrintButNotSure() {
        val a = art(5)
        val refs = listOf(CardVision.reference(art(9))!!, CardVision.reference(a)!!, CardVision.reference(a)!!)
        val decision = CardVision.decide(CardVision.scores(photo(a, 20, 20, 1.6, 1.0), refs, 0.25)!!, refs)!!
        assertEquals(1, decision.index)
        assertFalse(decision.sure)
        assertEquals(listOf(1, 2), decision.lookAlikes)
    }

    @Test fun aPhotoOfSomethingElseIsNotSure() {
        val refs = listOf(CardVision.reference(art(11))!!, CardVision.reference(art(12))!!)
        val decision = CardVision.decide(CardVision.scores(photo(art(99), 30, 30, 1.5, 1.0), refs, 0.25)!!, refs)!!
        assertFalse(decision.sure)
    }

    @Test fun cardShapedAreasCoverThePicture() {
        val rects = CardVision.rects(300, 400, 0.3, 1.0, 0.07, 1.08)
        assertTrue(rects.all { it.x >= -0.5 && it.y >= -0.5 && it.x + it.w <= 300.5 && it.y + it.h <= 400.5 })
        assertTrue(rects.any { it.x + it.w > 299 })
        assertTrue(rects.size in 500..20000)
    }
}
