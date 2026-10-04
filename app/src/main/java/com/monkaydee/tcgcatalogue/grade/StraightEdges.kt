package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds card-shaped outlines made of four long straight edges (a Hough transform, like document
 * scanners): every strong edge pixel votes for the lines through it in its edge direction; the
 * strongest lines are combined in pairs of roughly parallel sides, and the four-sided shapes whose
 * sides really lie on edges and whose proportions fit a card (63 x 88 mm, allowing for the
 * camera's perspective) are returned, best first. Works on a small picture (about 480 pixels).
 */
object StraightEdges {
    private const val THETAS = 180

    private class Line(val theta: Int, val rho: Double, val votes: Int) {
        val t get() = theta * PI / THETAS
        val a get() = cos(t)
        val b get() = sin(t)
    }

    fun quads(p: Pixels, top: Int = 2): List<Quad> {
        val w = p.width
        val h = p.height
        if (w < 32 || h < 32) return emptyList()
        val g = FloatArray(w * h) { i -> val c = p.argb[i]; (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) }
        val mag = FloatArray(w * h)
        val dir = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - 1] + g[i + w - 1])
            val gy = (g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - w] + g[i - w + 1])
            mag[i] = hypot(gx, gy)
            dir[i] = atan2(gy, gx)
        }
        val sorted = mag.copyOf().also { it.sort() }
        val strong = max(40f, 0.5f * sorted[(sorted.size * 0.85).toInt()])
        val diag = hypot(w.toDouble(), h.toDouble())
        val rhos = (2 * diag).toInt() + 1
        val acc = IntArray(THETAS * rhos)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (mag[i] < strong) continue
            // the line's normal is the gradient direction; vote within ±3 degrees of it
            var t0 = ((dir[i] + PI) % PI * THETAS / PI).roundToInt()
            for (dt in -3..3) {
                val t = ((t0 + dt) % THETAS + THETAS) % THETAS
                val th = t * PI / THETAS
                val r = (x * cos(th) + y * sin(th) + diag).roundToInt()
                if (r in 0 until rhos) acc[t * rhos + r]++
            }
        }
        // strongest lines, keeping only the best of nearly identical ones
        val lines = ArrayList<Line>()
        val order = acc.indices.filter { acc[it] >= 20 }.sortedByDescending { acc[it] }
        for (k in order) {
            val t = k / rhos
            val r = k % rhos - diag
            if (lines.any { l -> angleDiff(l.theta, t) <= 4 && abs(l.rho - sameSense(l.theta, t, r)) <= 8 }) continue
            lines += Line(t, r, acc[k])
            if (lines.size >= 16) break
        }
        // pairs of nearly parallel lines at a card-like distance
        val pairs = ArrayList<Pair<Line, Line>>()
        for (i in lines.indices) for (j in i + 1 until lines.size) {
            val a = lines[i]
            val b = lines[j]
            if (angleDiff(a.theta, b.theta) > 12) continue
            if (abs(a.rho - sameSense(a.theta, b.theta, b.rho)) < 0.12 * min(w, h)) continue
            pairs += a to b
        }
        val found = ArrayList<Pair<Quad, Double>>()
        for (i in pairs.indices) for (j in i + 1 until pairs.size) {
            val (a1, a2) = pairs[i]
            val (b1, b2) = pairs[j]
            val cross = angleDiff(a1.theta, b1.theta)
            if (cross < 70) continue
            val q = order4(listOf(meet(a1, b1), meet(b1, a2), meet(a2, b2), meet(b2, a1)).map { it ?: return@map null }.filterNotNull()) ?: continue
            if (q.corners.any { it.x < -0.02 * w || it.y < -0.02 * h || it.x > 1.02 * w || it.y > 1.02 * h }) continue
            val sides = (0 until 4).map { k -> hypot(q.corners[k].x - q.corners[(k + 1) % 4].x, q.corners[k].y - q.corners[(k + 1) % 4].y) }
            val ratio = (sides[0] + sides[2]) / (sides[1] + sides[3])
            val aspect = if (ratio < 1) ratio / 0.716 else ratio / (1 / 0.716)
            if (aspect < 0.82 || aspect > 1.22) continue
            val area = abs(shoelace(q.corners))
            if (area < 0.04 * w * h) continue
            val support = (0 until 4).minOf { k -> sideSupport(q.corners[k], q.corners[(k + 1) % 4], mag, dir, w, h, strong) }
            if (support < 0.45) continue
            val score = support * sqrt(area) * (1 - 0.8 * abs(1 - aspect))
            found += q to score
        }
        return found.sortedByDescending { it.second }.map { it.first }.fold(ArrayList<Quad>()) { acc2, q ->
            if (acc2.none { near(it, q, 0.05 * min(w, h)) }) acc2 += q
            acc2
        }.take(top)
    }

    /** Share of points along a side that lie on a strong edge running along that side. */
    private fun sideSupport(a: Pt, b: Pt, mag: FloatArray, dir: FloatArray, w: Int, h: Int, strong: Float): Double {
        val n = 40
        val normal = atan2(b.x - a.x, -(b.y - a.y)) // perpendicular to a->b
        var hit = 0
        for (k in 0 until n) {
            val t = 0.1 + 0.8 * k / (n - 1)
            val x = a.x + (b.x - a.x) * t
            val y = a.y + (b.y - a.y) * t
            var ok = false
            for (dy in -2..2) for (dx in -2..2) {
                val xi = (x + dx).roundToInt()
                val yi = (y + dy).roundToInt()
                if (xi !in 1 until w - 1 || yi !in 1 until h - 1) continue
                val i = yi * w + xi
                if (mag[i] < 0.6f * strong) continue
                var d = abs(dir[i] - normal) % PI
                if (d > PI / 2) d = PI - d
                if (d < PI / 9) ok = true
            }
            if (ok) hit++
        }
        return hit.toDouble() / n
    }

    private fun angleDiff(a: Int, b: Int): Int { val d = abs(a - b) % THETAS; return min(d, THETAS - d) }

    /** [rho] of a line with angle [t] expressed for angle [ref] (lines near 0° and 179° are the same direction). */
    private fun sameSense(ref: Int, t: Int, rho: Double) = if (abs(ref - t) > THETAS / 2) -rho else rho

    private fun meet(l1: Line, l2: Line): Pt? {
        val det = l1.a * l2.b - l1.b * l2.a
        if (abs(det) < 1e-6) return null
        return Pt((l1.rho * l2.b - l1.b * l2.rho) / det, (l1.a * l2.rho - l1.rho * l2.a) / det)
    }

    /** The four corners in the order top-left, top-right, bottom-right, bottom-left. */
    private fun order4(c: List<Pt>): Quad? {
        if (c.size != 4) return null
        val cx = c.sumOf { it.x } / 4
        val cy = c.sumOf { it.y } / 4
        val s = c.sortedBy { atan2(it.y - cy, it.x - cx) } // clockwise from the left in picture coords
        // start at the corner closest to the top-left
        val start = s.indices.minBy { s[it].x + s[it].y }
        val o = (0 until 4).map { s[(start + it) % 4] }
        return Quad(o[0], o[1], o[2], o[3])
    }

    private fun shoelace(c: List<Pt>) = (0 until 4).sumOf { k -> c[k].x * c[(k + 1) % 4].y - c[(k + 1) % 4].x * c[k].y } / 2

    private fun near(a: Quad, b: Quad, tol: Double) = a.corners.zip(b.corners).all { (p, q) -> hypot(p.x - q.x, p.y - q.y) < tol }
}
