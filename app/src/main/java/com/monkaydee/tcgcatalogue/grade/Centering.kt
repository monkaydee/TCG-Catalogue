package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Measures how well the print is centred on a flattened card picture ([CardRectifier.W] x
 * [CardRectifier.H]): on each side, the border runs from the card's cut to the printed frame.
 * The cut is the start of the first even stretch of colour; the frame is the first straight line
 * where the colour clearly changes, found from the edge strength averaged over many lines (a
 * straight printed frame adds up, the artwork's edges average out).
 *
 * Tuned and checked on PSA's scans of graded cards: on English Pokémon backs (all the same design)
 * the measured left + right border stays within ±0.2 % of the card width for most cards.
 */
object Centering {
    /** Border widths in pixels and the two ratios, as the bigger side's share (e.g. 54.0 for 54/46). */
    data class Result(
        val left: Double,
        val right: Double,
        val top: Double,
        val bottom: Double,
        /** Where the card's cut was found, in pixels from each side (for drawing the borders). */
        val cuts: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    ) {
        /** Left share in % (50 = perfect). */
        val leftRight: Double get() = 100 * left / (left + right)
        val topBottom: Double get() = 100 * top / (top + bottom)
        /** The worse axis as the bigger side's share: 58 for 58/42. */
        val worst: Double get() = maxOf(leftRight, 100 - leftRight, topBottom, 100 - topBottom)
    }

    /** User placed printed-frame guides on a confirmed, flattened whole card.
     * Fractions are measured inward from its four edges, never from an artwork subject. */
    fun manual(width: Int, height: Int, borders: List<Double>): Result? {
        if (width <= 0 || height <= 0 || borders.size != 4) return null
        if (borders.any { !it.isFinite() || it <= 0.0 || it >= 0.5 }) return null
        val (l, r, t, b) = borders
        if (l + r >= 1.0 || t + b >= 1.0) return null
        return Result(l * width, r * width, t * height, b * height)
    }

    private class Side(val edge: Double, val frame: Double, val spread: Double, val support: Double) {
        val width get() = frame - edge
    }

    /** The centering of [card], or null when the borders can't be found reliably (e.g. full art). */
    fun measure(card: Pixels): Result? {
        val sides = "LRTB".map { side(card, it) ?: return null }
        val (l, r, t, b) = sides
        val w = card.width.toDouble()
        val h = card.height.toDouble()
        // Plausible borders: each 1 – 12 % of the card, both sides of an axis similar in kind,
        // and the frame straight (positions along it agree).
        if (listOf(l.width, r.width).any { it < 0.01 * w || it > 0.12 * w }) return null
        if (listOf(t.width, b.width).any { it < 0.01 * h || it > 0.12 * h }) return null
        if (sides.any { it.spread > 0.0075 * max(w, h) || it.support < 0.3 }) return null
        return Result(l.width, r.width, t.width, b.width, sides.map { it.edge })
    }

    /** Colour lines running inward from one side, from 20 % to 80 % along it: [line][pos][rgb]. */
    private fun lines(card: Pixels, side: Char, n: Int = 60): Array<Array<FloatArray>> {
        val w = card.width
        val h = card.height
        return Array(n) { k ->
            if (side == 'L' || side == 'R') {
                val y = (h * (0.2 + 0.6 * k / (n - 1))).toInt()
                Array(w) { i -> rgb(card.argb[y * w + if (side == 'L') i else w - 1 - i]) }
            } else {
                val x = (w * (0.2 + 0.6 * k / (n - 1))).toInt()
                Array(h) { i -> rgb(card.argb[(if (side == 'T') i else h - 1 - i) * w + x]) }
            }
        }
    }

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF).toFloat(), ((c shr 8) and 0xFF).toFloat(), (c and 0xFF).toFloat())

    private fun dist(a: FloatArray, b: FloatArray): Double {
        val r = (a[0] - b[0]).toDouble()
        val g = (a[1] - b[1]).toDouble()
        val bb = (a[2] - b[2]).toDouble()
        return sqrt(r * r + g * g + bb * bb)
    }

    private fun median(values: List<FloatArray>): FloatArray =
        FloatArray(3) { c -> values.map { it[c] }.sorted().let { it[it.size / 2] } }

    /**
     * Where the card really starts on one side of a straightened photo, in pixels: the first colour
     * step followed by an even stretch (the border), else the first lasting step away from the
     * background. Thin background strips left by straightening would otherwise count as wear.
     * At most 4 % of the card; 0 when nothing is found.
     */
    fun cut(card: Pixels, side: Char): Int {
        val ls = lines(card, side)
        val length = ls[0].size
        val prof = Array(length) { i -> median(ls.map { it[i] }) }
        val region = (0.04 * length).toInt()
        val need = max(10, (0.02 * length).toInt())
        fun even(i: Int): Boolean {
            val seg = (i until min(length, i + need)).map { prof[it] }
            val med = median(seg)
            val dev = seg.map { dist(it, med) }.sorted()
            return dev[(dev.size * 0.8).toInt().coerceAtMost(dev.size - 1)] < 12 && dev.last() < 40
        }
        for (i in 0 until region) if ((i <= 2 || dist(prof[i], prof[i - 3]) > 14) && even(i)) return i
        val bg = median(prof.take(3))
        val run = max(6, (0.01 * length).toInt())
        for (i in 3 until region) if ((i until i + run).all { dist(prof[it], bg) > 30 }) return i
        return 0
    }

    private fun side(card: Pixels, side: Char): Side? {
        val ls = lines(card, side)
        val length = ls[0].size
        val prof = Array(length) { i -> median(ls.map { it[i] }) }
        // The cut: the first colour step followed by an even stretch of at least 2 % (the border).
        val region = (0.13 * length).toInt()
        val need = max(10, (0.02 * length).toInt())
        fun even(i: Int): Boolean {
            val seg = (i until min(length, i + need)).map { prof[it] }
            val med = median(seg)
            val dev = seg.map { dist(it, med) }.sorted()
            return dev[(dev.size * 0.8).toInt().coerceAtMost(dev.size - 1)] < 12 && dev.last() < 40
        }
        var e = -1
        for (i in 0 until region) {
            if ((i <= 2 || dist(prof[i], prof[i - 3]) > 14) && even(i)) { e = i; break }
        }
        if (e < 0) return null
        val skip = e + max(3, (0.005 * length).toInt())
        val bandLen = max(4, (0.008 * length).toInt())
        val ref = median((skip until skip + bandLen).map { prof[it] })
        // edge strength averaged over all lines
        val g = DoubleArray(length - 1) { i -> ls.sumOf { ln -> (abs(ln[i + 1][0] - ln[i][0]) + abs(ln[i + 1][1] - ln[i][1]) + abs(ln[i + 1][2] - ln[i][2])).toDouble() } / ls.size }
        val lo = skip + bandLen
        val noise = (skip until lo).map { g[it] }.sorted()[bandLen / 2] + 1e-3
        val hi = min(g.size - 1, e + (0.2 * length).toInt())
        var f = -1
        for (i in lo until hi) {
            if (g[i] >= g[i - 1] && g[i] >= g[i + 1] && g[i] > max(3 * noise, 10.0)) {
                val after = median((i + 2 until min(length, i + 2 + max(3, (0.006 * length).toInt()))).map { prof[it] })
                if (dist(after, ref) > 12) { f = i + 1; break }
            }
        }
        if (f < 0) return null
        // how straight the frame is: where single lines leave the border colour
        val per = ArrayList<Int>()
        for (ln in ls) {
            var run = 0
            for (i in lo until length) {
                if (dist(ln[i], ref) > 16) {
                    run++
                    if (run == 3) {
                        val k = i - 2
                        if (abs(k - f) < 0.05 * length) per += k
                        break
                    }
                } else {
                    run = 0
                }
            }
        }
        val spread = if (per.size > 5) per.sorted().let { it[it.size * 3 / 4] - it[it.size / 4] }.toDouble() else 99.0
        return Side(e.toDouble(), f.toDouble(), spread, per.size.toDouble() / ls.size)
    }

    /**
     * Grading companies' centering limits (the bigger side's share, front / back) for the best
     * grade a card can get on centering. Published standards, approximately; centering alone
     * doesn't set a grade.
     */
    enum class Company(val label: String, val limits: List<Triple<String, Double, Double>>) {
        PSA(
            "PSA",
            listOf(
                Triple("10", 55.0, 75.0), Triple("9", 65.0, 90.0), Triple("8", 70.0, 90.0), Triple("7", 75.0, 90.0),
                Triple("6", 80.0, 90.0), Triple("5", 85.0, 90.0), Triple("4", 85.0, 90.0), Triple("3", 90.0, 90.0),
            ),
        ),
        BGS(
            "BGS",
            listOf(
                Triple("10", 50.5, 60.0), Triple("9.5", 55.0, 60.0), Triple("9", 60.0, 80.0), Triple("8.5", 62.5, 85.0),
                Triple("8", 65.0, 90.0), Triple("7", 70.0, 95.0),
            ),
        ),
        CGC(
            "CGC",
            listOf(
                Triple("10", 55.0, 75.0), Triple("9.5", 60.0, 80.0), Triple("9", 62.5, 85.0), Triple("8", 67.5, 90.0),
                Triple("7", 72.5, 95.0),
            ),
        ),
        ;

        /** The best grade allowed by centering, or the lowest listed one when worse. */
        fun bestGrade(front: Double?, back: Double?): String =
            limits.firstOrNull { (_, f, b) -> (front == null || front <= f + 1e-9) && (back == null || back <= b + 1e-9) }?.first
                ?: "<" + limits.last().first
    }
}
