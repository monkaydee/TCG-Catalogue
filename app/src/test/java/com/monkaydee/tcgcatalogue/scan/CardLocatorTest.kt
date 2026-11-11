package com.monkaydee.tcgcatalogue.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class CardLocatorTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** A noisy table with a card: a yellow border round a busy picture. */
    private fun photo(w: Int, h: Int, cx: Int, cy: Int, cw: Int, ch: Int, seed: Int): Pixels {
        val rnd = Random(seed)
        val px = IntArray(w * h) { rgb(70 + rnd.nextInt(20), 90 + rnd.nextInt(20), 110 + rnd.nextInt(20)) }
        val border = max(3, cw / 16)
        for (y in cy until cy + ch) for (x in cx until cx + cw) {
            val edge = x - cx < border || cx + cw - 1 - x < border || y - cy < border || cy + ch - 1 - y < border
            px[y * w + x] = if (edge) rgb(240, 210, 40) else rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        }
        return Pixels(w, h, px)
    }

    private fun overlap(a: CardRect, x: Int, y: Int, w: Int, h: Int): Double {
        val ix = max(0.0, min(a.x + a.w, (x + w).toDouble()) - max(a.x, x.toDouble()))
        val iy = max(0.0, min(a.y + a.h, (y + h).toDouble()) - max(a.y, y.toDouble()))
        val i = ix * iy
        return i / (a.w * a.h + w * h - i)
    }

    @Test fun findsAnOffCentreCard() {
        val p = photo(200, 260, 30, 90, 86, 120, seed = 1)
        val best = CardLocator.locate(p).first()
        assertFalse(best.lying)
        assertTrue("overlap ${overlap(best.rect, 30, 90, 86, 120)}", overlap(best.rect, 30, 90, 86, 120) > 0.8)
    }

    @Test fun findsALargeCard() {
        val p = photo(200, 260, 20, 15, 160, 224, seed = 2)
        assertTrue(overlap(CardLocator.locate(p).first().rect, 20, 15, 160, 224) > 0.8)
    }

    @Test fun findsACardLyingOnItsSide() {
        val p = photo(260, 200, 40, 50, 140, 100, seed = 3)
        val best = CardLocator.locate(p).first()
        assertTrue(best.lying)
        assertTrue(overlap(best.rect, 40, 50, 140, 100) > 0.8)
    }

    @Test fun givesSeveralDifferentGuesses() {
        val found = CardLocator.locate(photo(200, 260, 30, 90, 86, 120, seed = 4))
        assertTrue(found.size in 2..3)
        assertTrue(found.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test fun tinyPicturesGiveNothing() {
        assertTrue(CardLocator.locate(Pixels(8, 8, IntArray(64))).isEmpty())
    }
}
