package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Corner and edge wear on a flattened card ([CardRectifier.W] x [CardRectifier.H], 15 px/mm):
 * in the outermost ~0.6 mm along the cut, the share of pixels that clearly differ from the border
 * colour just inside (1 – 2 mm in) — whitening, chips, dings and nicks — and the share that are
 * whiter than the border (whitening shows best on dark borders).
 */
object Wear {
    /** One corner or edge: share of defect pixels, share of whitened pixels, and how strongly they stand out (1 = threshold). */
    data class Zone(val defects: Double, val whitening: Double, val strength: Double)

    /** Zones in the order T, R, B, L (edges) and TL, TR, BR, BL (corners). */
    data class Result(val zones: Map<String, Zone>) {
        val corners get() = listOf("TL", "TR", "BR", "BL").map { zones.getValue(it) }
        val edges get() = listOf("T", "R", "B", "L").map { zones.getValue(it) }
    }

    val EDGES = listOf("T", "R", "B", "L")
    val CORNERS = listOf("TL", "TR", "BR", "BL")

    /** Corner radius of a standard card (3.2 mm) at 15 px/mm, scaled to the picture. */
    private fun radius(card: Pixels) = 3.2 * 15 * card.width / CardRectifier.W

    fun measure(card: Pixels): Result {
        val w = card.width
        val h = card.height
        val s = w.toDouble() / CardRectifier.W
        val r = radius(card)
        val ring = 9 * s
        val refIn = 14 * s
        val refOut = 30 * s
        val zones = LinkedHashMap<String, Zone>()
        for (name in EDGES + CORNERS) {
            val (x0, x1, y0, y1) = area(name, w, h, s)
            val refPx = ArrayList<IntArray>()
            val ringPx = ArrayList<IntArray>()
            for (y in y0 until y1) for (x in x0 until x1) {
                val d = inside(x, y, w, h, r)
                if (d < 0) continue
                val c = card.argb[y * w + x]
                val rgb = intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
                if (d < ring) ringPx += rgb else if (d >= refIn && d < refOut) refPx += rgb
            }
            if (refPx.size < 20 || ringPx.isEmpty()) { zones[name] = Zone(0.0, 0.0, 0.0); continue }
            val ref = IntArray(3) { ch -> refPx.map { it[ch] }.sorted()[refPx.size / 2] }
            val refL = refPx.map { lum(it) }.sorted()[refPx.size / 2]
            val spread = refPx.map { dist(it, ref) }.sorted()[(refPx.size * 0.8).toInt().coerceAtMost(refPx.size - 1)]
            val thr = max(40.0, 2.5 * spread)
            var bad = 0
            var whiter = 0
            val ds = DoubleArray(ringPx.size)
            ringPx.forEachIndexed { i, p ->
                val d = dist(p, ref)
                ds[i] = d
                if (d > thr) {
                    bad++
                    if (lum(p) - refL > 30) whiter++
                }
            }
            ds.sort()
            zones[name] = Zone(bad.toDouble() / ringPx.size, whiter.toDouble() / ringPx.size, ds[(ds.size * 0.95).toInt().coerceAtMost(ds.size - 1)] / thr)
        }
        return Result(zones)
    }

    /** The pixel box of a zone: x0, x1, y0, y1. */
    fun area(name: String, w: Int, h: Int, s: Double = w.toDouble() / CardRectifier.W): List<Int> {
        val band = (40 * s).toInt()
        return when (name) {
            "T" -> listOf((w * 0.08).toInt(), (w * 0.92).toInt(), 0, band)
            "B" -> listOf((w * 0.08).toInt(), (w * 0.92).toInt(), h - band - 1, h)
            "L" -> listOf(0, band, (h * 0.06).toInt(), (h * 0.94).toInt())
            "R" -> listOf(w - band - 1, w, (h * 0.06).toInt(), (h * 0.94).toInt())
            "TL" -> listOf(0, (w * 0.08).toInt(), 0, (h * 0.06).toInt())
            "TR" -> listOf((w * 0.92).toInt(), w, 0, (h * 0.06).toInt())
            "BR" -> listOf((w * 0.92).toInt(), w, (h * 0.94).toInt(), h)
            else -> listOf(0, (w * 0.08).toInt(), (h * 0.94).toInt(), h)
        }
    }

    /** Distance inside the card's rounded-rectangle cut, in pixels (negative = outside). */
    private fun inside(x: Int, y: Int, w: Int, h: Int, r: Double): Double {
        val cx = min(max(x.toDouble(), r), w - 1 - r)
        val cy = min(max(y.toDouble(), r), h - 1 - r)
        return if (cx != x.toDouble() && cy != y.toDouble()) r - hypot(x - cx, y - cy)
        else minOf(x, w - 1 - x, y, h - 1 - y).toDouble()
    }

    private fun lum(p: IntArray) = 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

    private fun dist(a: IntArray, b: IntArray): Double {
        val r = (a[0] - b[0]).toDouble()
        val g = (a[1] - b[1]).toDouble()
        val bb = (a[2] - b[2]).toDouble()
        return sqrt(r * r + g * g + bb * bb)
    }
}
