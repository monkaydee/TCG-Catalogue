package com.monkaydee.tcgcatalogue.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds where a card lies in a photo without knowing which card it is, so picture search looks
 * at the card and not at the table around it. Every card-shaped rectangle (upright or lying, at
 * all sizes and positions) is scored by how different the colour is just inside and just outside
 * each of its four sides, how sharp the edge on each side is, and how alike the four inside
 * strips are (a card's border has one colour all round). Tuned on synthetic phone photos of real
 * cards: the best rectangle is the card about 6 times in 10, one of the best three 8 times in 10.
 */
object CardLocator {
    /** The photo is reduced to this many pixels on its longest side before searching. */
    const val SIDE = 200
    private const val ASPECT = 0.716
    private const val UNIFORM_WEIGHT = 1.0
    private const val EDGE_WEIGHT = 0.5

    /** A rectangle in the photo's pixels; [lying] when the card lies on its side. */
    data class Found(val rect: CardRect, val lying: Boolean, val score: Double)

    /** Up to [top] different card-shaped rectangles of [p], best first. */
    fun locate(p: Pixels, top: Int = 3): List<Found> {
        val w = p.width
        val h = p.height
        if (w < 16 || h < 16) return emptyList()
        val colour = Array(3) { c -> Integral(w, h) { x, y -> ((p.argb[y * w + x] shr (16 - 8 * c)) and 0xFF).toDouble() } }
        val grey = DoubleArray(w * h) { i -> val c = p.argb[i]; (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3.0 }
        val gx = Integral(w, h) { x, y -> if (x in 1 until w - 1) kotlin.math.abs(grey[y * w + x + 1] - grey[y * w + x - 1]) else 0.0 }
        val gy = Integral(w, h) { x, y -> if (y in 1 until h - 1) kotlin.math.abs(grey[(y + 1) * w + x] - grey[(y - 1) * w + x]) else 0.0 }

        val candidates = ArrayList<Found>()
        for (lying in listOf(false, true)) {
            var frac = 0.25
            while (frac <= 1.001) {
                val rh: Int
                val rw: Int
                if (!lying) { rh = (frac * h).toInt(); rw = (rh * ASPECT).toInt() } else { rw = (frac * w).toInt(); rh = (rw * ASPECT).toInt() }
                frac += 0.03
                if (rw > w || rh > h || rw < 8) continue
                val step = max(1, (min(rw, rh) * 0.03).toInt())
                val t = max(2, (min(rw, rh) * 0.05).toInt())
                // The 20 best positions of this size go on to the final ranking.
                val best = ArrayList<Found>()
                var y0 = 0
                while (y0 <= h - rh) {
                    var x0 = 0
                    while (x0 <= w - rw) {
                        val s = score(colour, gx, gy, w, h, x0, y0, x0 + rw, y0 + rh, t)
                        if (best.size < 20 || s > best.last().score) {
                            val f = Found(CardRect(x0.toDouble(), y0.toDouble(), rw.toDouble(), rh.toDouble()), lying, s)
                            val at = best.indexOfFirst { it.score < s }.let { if (it < 0) best.size else it }
                            best.add(at, f)
                            if (best.size > 20) best.removeAt(best.size - 1)
                        }
                        x0 += step
                    }
                    y0 += step
                }
                candidates += best
            }
        }
        candidates.sortByDescending { it.score }
        val out = ArrayList<Found>()
        for (c in candidates) {
            val r = c.rect
            val near = out.any { o ->
                val q = o.rect
                kotlin.math.abs(r.x - q.x) + kotlin.math.abs(r.y - q.y) + kotlin.math.abs(r.x + r.w - q.x - q.w) + kotlin.math.abs(r.y + r.h - q.y - q.h) < 0.15 * (r.w + r.h)
            }
            if (near) continue
            out += c
            if (out.size >= top) break
        }
        return out
    }

    private fun score(colour: Array<Integral>, gx: Integral, gy: Integral, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, t: Int): Double {
        // Strips just inside each side (top, bottom, left, right) and just outside it.
        val inside = arrayOf(
            mean(colour, x0, y0, x1, y0 + t), mean(colour, x0, y1 - t, x1, y1),
            mean(colour, x0, y0, x0 + t, y1), mean(colour, x1 - t, y0, x1, y1),
        )
        val outside = arrayOf(
            mean(colour, x0, y0 - t, x1, y0), mean(colour, x0, y1, x1, y1 + t),
            mean(colour, x0 - t, y0, x0, y1), mean(colour, x1, y0, x1 + t, y1),
        )
        val within = booleanArrayOf(y0 - t >= 0, y1 + t <= h, x0 - t >= 0, x1 + t <= w)
        // A side on the photo's edge has nothing outside to compare with: counted as average.
        val contrast = DoubleArray(4) { if (within[it]) distance(inside[it], outside[it]) else 25.0 }
        val edge = doubleArrayOf(
            gy.mean(x0 + 2, y0 - 1, x1 - 2, y0 + 2), gy.mean(x0 + 2, y1 - 2, x1 - 2, y1 + 1),
            gx.mean(x0 - 1, y0 + 2, x0 + 2, y1 - 2), gx.mean(x1 - 2, y0 + 2, x1 + 1, y1 - 2),
        )
        contrast.sort()
        edge.sort()
        val avg = DoubleArray(3) { c -> (inside[0][c] + inside[1][c] + inside[2][c] + inside[3][c]) / 4 }
        val uneven = inside.sumOf { distance(it, avg) } / 4
        return (contrast[0] + contrast[1]) / 2 + EDGE_WEIGHT * (edge[0] + edge[1]) / 2 - UNIFORM_WEIGHT * uneven
    }

    private fun mean(colour: Array<Integral>, x0: Int, y0: Int, x1: Int, y1: Int) =
        DoubleArray(3) { colour[it].mean(x0, y0, x1, y1) }

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val r = a[0] - b[0]
        val g = a[1] - b[1]
        val bl = a[2] - b[2]
        return sqrt(r * r + g * g + bl * bl)
    }

    /** Sums over rectangles in constant time; rectangles are clipped to the picture. */
    private class Integral(private val w: Int, private val h: Int, value: (Int, Int) -> Double) {
        private val sums = DoubleArray((w + 1) * (h + 1))

        init {
            for (y in 0 until h) {
                var row = 0.0
                for (x in 0 until w) {
                    row += value(x, y)
                    sums[(y + 1) * (w + 1) + x + 1] = sums[y * (w + 1) + x + 1] + row
                }
            }
        }

        fun mean(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            val ax = x0.coerceIn(0, w)
            val bx = x1.coerceIn(0, w)
            val ay = y0.coerceIn(0, h)
            val by = y1.coerceIn(0, h)
            val n = max((bx - ax) * (by - ay), 1)
            if (bx <= ax || by <= ay) return 0.0
            val s = sums[by * (w + 1) + bx] - sums[ay * (w + 1) + bx] - sums[by * (w + 1) + ax] + sums[ay * (w + 1) + ax]
            return s / n
        }
    }
}
