package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A point in a picture, in pixels. */
data class Pt(val x: Double, val y: Double)

/** The card's four corners in the photo: top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val corners get() = listOf(tl, tr, br, bl)
    fun scaled(f: Double) = Quad(Pt(tl.x * f, tl.y * f), Pt(tr.x * f, tr.y * f), Pt(br.x * f, br.y * f), Pt(bl.x * f, bl.y * f))
}

/**
 * Finds the exact outline of a card in a photo and flattens it to a straight card picture, so
 * borders can be measured. The rough outline comes from [StraightEdges] (or the camera's card
 * guide); then many points along
 * each side are placed on the card's cut edge (the outermost strong edge across the side), a
 * straight line is fitted through them (outliers dropped), and the four lines meet at the corners.
 */
object CardRectifier {
    /** Width and height of the flattened card: 15 pixels per millimetre (63 x 88 mm). */
    const val W = 945
    const val H = 1320
    private const val LINE_SIDE = 480

    /**
     * The card's outline in [p], or null when no clear card is found. [hint] is where the card
     * should be (e.g. the camera's card guide); without it the photo is searched.
     */
    fun findQuad(p: Pixels, hint: Quad? = null): Quad? {
        val gray = Channels(p)
        // The guide is only roughly where the card is: start with a wider search band.
        if (hint != null) return best(gray, listOf(hint), doubleArrayOf(0.10, 0.03, 0.012))
        // Straight-line search (finds tilted cards, ignores round logos). Nothing is better than a
        // wrong outline for measuring, so there is no looser fallback.
        val lineScale = LINE_SIDE.toDouble() / max(p.width, p.height)
        return best(gray, StraightEdges.quads(downscale(p, lineScale), 3).map { it.scaled(1 / lineScale) })
    }

    /**
     * Each start refined in passes with a narrowing search band. Among the outlines whose sides are
     * well supported by straight edges, the biggest wins: printed boxes inside a card have straight
     * edges too, but the card's outline encloses them.
     */
    /**
     * The card inside the camera guide: the user holds it in the box with a small margin, so only
     * outlines that lie within the box (plus a little) and fill most of it count. Inner printed
     * boxes are too small, lines on the mat outside the box are out of bounds.
     */
    fun findQuadIn(p: Pixels, guide: Quad): Quad? {
        val gray = Channels(p)
        val g = guide.corners
        val gx0 = g.minOf { it.x }; val gx1 = g.maxOf { it.x }; val gy0 = g.minOf { it.y }; val gy1 = g.maxOf { it.y }
        val mx = 0.06 * (gx1 - gx0); val my = 0.06 * (gy1 - gy0)
        val guideArea = abs(area(guide))
        val fits = { q: Quad ->
            q.corners.all { it.x in (gx0 - mx)..(gx1 + mx) && it.y in (gy0 - my)..(gy1 + my) } &&
                abs(area(q)) in (0.35 * guideArea)..(1.15 * guideArea)
        }
        val lineScale = LINE_SIDE.toDouble() / max(p.width, p.height)
        // The guide itself is only a start when no straight-edged outline lies in the box: refined, it
        // settles on mat texture or sleeve lines bigger than the card.
        val lines = StraightEdges.quads(downscale(p, lineScale), 24).map { it.scaled(1 / lineScale) }.filter(fits)
        return best(gray, lines.ifEmpty { listOf(guide) }, accept = fits, share = 0.93)
    }

    private fun best(
        gray: Channels,
        starts: List<Quad>,
        bands: DoubleArray = doubleArrayOf(0.03, 0.012),
        accept: (Quad) -> Boolean = { true },
        share: Double = 0.93,
    ): Quad? {
        val refined = starts.mapNotNull { start ->
            var q = start
            var support = 0.0
            for (band in bands) {
                val r = refine(gray, q, band) ?: return@mapNotNull null
                q = r.first
                support = r.second
            }
            q.takeIf { support >= 0.5 && plausible(it) && accept(it) }?.let { it to support }
        }
        val top = refined.maxOfOrNull { it.second } ?: return null
        // Only near-equal outlines compete on size: a side on mat texture or a shadow is weaker than
        // the card's own cut, and must not stretch the outline past the card.
        return refined.filter { it.second >= share * top }.maxByOrNull { abs(area(it.first)) }?.first
    }

    private fun area(q: Quad): Double {
        val c = q.corners
        return (0 until 4).sumOf { k -> c[k].x * c[(k + 1) % 4].y - c[(k + 1) % 4].x * c[k].y } / 2
    }

    /** The card in [p] inside [q], flattened to [W] x [H] (bilinear sampling). */
    fun warp(p: Pixels, q: Quad, w: Int = W, h: Int = H): Pixels {
        val hm = homography(listOf(Pt(0.0, 0.0), Pt(w.toDouble(), 0.0), Pt(w.toDouble(), h.toDouble()), Pt(0.0, h.toDouble())), q.corners)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val (sx, sy) = apply(hm, x + 0.5, y + 0.5)
                out[y * w + x] = sample(p, sx - 0.5, sy - 0.5)
            }
        }
        return Pixels(w, h, out)
    }

    /** The outline refined around [q], and how well its sides are supported (0..1). */
    private fun refine(g: Channels, q: Quad, band: Double): Pair<Quad, Double>? {
        val c = q.corners
        val size = (dist(c[0], c[1]) + dist(c[1], c[2])) / 2
        val reach = band * size
        var supported = 0
        val n = 60
        val lines = (0 until 4).map { i ->
            val a = c[i]
            val b = c[(i + 1) % 4]
            // outward normal of side a->b (corners go clockwise in picture coordinates)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            val nx = dy / len
            val ny = -dx / len
            val pts = ArrayList<Pt>()
            for (k in 0 until n) {
                // skip the rounded corners
                val t = 0.08 + 0.84 * k / (n - 1)
                val px = a.x + dx * t
                val py = a.y + dy * t
                edgeAlong(g, px, py, nx, ny, reach)?.let { pts += it }
            }
            val fit = fitLine(pts) ?: return null
            supported += fit.second
            fit.first
        }
        val corners = (0 until 4).map { i -> intersect(lines[(i + 3) % 4], lines[i]) ?: return null }
        return Quad(corners[0], corners[1], corners[2], corners[3]) to supported.toDouble() / (4 * n)
    }

    /** The card's cut edge on a line through ([px], [py]) along the outward normal. */
    private fun edgeAlong(g: Channels, px: Double, py: Double, nx: Double, ny: Double, reach: Double): Pt? {
        val steps = max(8, reach.roundToInt())
        // per colour channel, the strongest change counts (blue backs on dark tables differ in colour)
        val v = Array(3) { k -> DoubleArray(2 * steps + 1) { i -> val s = (i - steps).toDouble(); g.at(k, px + nx * s, py + ny * s) } }
        // smoothed derivative
        val d = DoubleArray(2 * steps + 1) { i ->
            if (i < 2 || i > 2 * steps - 2) 0.0 else v.maxOf { c -> abs((c[i + 1] + c[i + 2]) - (c[i - 1] + c[i - 2])) / 2 }
        }
        val peak = d.maxOrNull() ?: return null
        if (peak < 12) return null
        // The strongest change, preferring ones near the expected line (a printed box just inside
        // the card can be about as strong as the cut).
        var best = -1
        var bestScore = 0.0
        for (i in d.indices) {
            if (d[i] < 0.5 * peak || d[i] < d.getOrElse(i - 1) { 0.0 } || d[i] < d.getOrElse(i + 1) { 0.0 }) continue
            val score = d[i] * (1 - 0.5 * abs(i - steps) / steps)
            if (score > bestScore) { bestScore = score; best = i }
        }
        if (best < 0) return null
        // sub-pixel position from the neighbours
        val a = d.getOrElse(best - 1) { 0.0 }
        val b = d[best]
        val cc = d.getOrElse(best + 1) { 0.0 }
        val den = a - 2 * b + cc
        val off = if (den != 0.0) 0.5 * (a - cc) / den else 0.0
        val s = best - steps + off
        return Pt(px + nx * s, py + ny * s)
    }

    /** A line a*x + b*y = c (a² + b² = 1) through [pts], dropping points far from it; and how many points it kept. */
    private fun fitLine(pts: List<Pt>): Pair<DoubleArray, Int>? {
        if (pts.size < 10) return null
        var use = pts
        var line = leastSquares(use) ?: return null
        repeat(3) {
            val resid = use.map { abs(line[0] * it.x + line[1] * it.y - line[2]) }
            val sorted = resid.sorted()
            val limit = max(1.5, 2.5 * sorted[sorted.size / 2])
            val kept = use.filterIndexed { i, _ -> resid[i] <= limit }
            if (kept.size < 8) return line to use.size
            use = kept
            line = leastSquares(use) ?: return line to use.size
        }
        // points within 2 px of the final line count as support
        return line to pts.count { abs(line[0] * it.x + line[1] * it.y - line[2]) <= 2.0 }
    }

    /** Total least squares (principal direction) line. */
    private fun leastSquares(pts: List<Pt>): DoubleArray? {
        val mx = pts.sumOf { it.x } / pts.size
        val my = pts.sumOf { it.y } / pts.size
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (p in pts) {
            sxx += (p.x - mx) * (p.x - mx); syy += (p.y - my) * (p.y - my); sxy += (p.x - mx) * (p.y - my)
        }
        // normal = eigenvector of the smaller eigenvalue of the scatter matrix
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val small = tr / 2 - sqrt(max(0.0, tr * tr / 4 - det))
        var a = sxy
        var b = small - sxx
        if (abs(a) + abs(b) < 1e-12) { a = small - syy; b = sxy }
        val n = hypot(a, b)
        if (n < 1e-12) return null
        a /= n; b /= n
        return doubleArrayOf(a, b, a * mx + b * my)
    }

    private fun intersect(l1: DoubleArray, l2: DoubleArray): Pt? {
        val det = l1[0] * l2[1] - l1[1] * l2[0]
        if (abs(det) < 1e-9) return null
        return Pt((l1[2] * l2[1] - l1[1] * l2[2]) / det, (l1[0] * l2[2] - l1[2] * l2[0]) / det)
    }

    /** A real card is close to 63 x 88 mm and nearly rectangular in the photo. */
    private fun plausible(q: Quad): Boolean {
        val c = q.corners
        val top = dist(c[0], c[1])
        val right = dist(c[1], c[2])
        val bottom = dist(c[2], c[3])
        val left = dist(c[3], c[0])
        val ratio = (top + bottom) / (left + right)
        val upright = ratio in 0.6..0.85
        val lying = ratio in 1.18..1.65
        return (upright || lying) && min(top, bottom) / max(top, bottom) > 0.8 && min(left, right) / max(left, right) > 0.8
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** 3x3 homography (row-major, h33 = 1) mapping [from] points to [to] points. */
    fun homography(from: List<Pt>, to: List<Pt>): DoubleArray {
        val m = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i]
            val (u, v) = to[i]
            m[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            m[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gaussian elimination with partial pivoting
        for (col in 0 until 8) {
            val piv = (col until 8).maxBy { abs(m[it][col]) }
            val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp
            for (r in 0 until 8) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                for (k in col until 9) m[r][k] -= f * m[col][k]
            }
        }
        return DoubleArray(9) { i -> if (i == 8) 1.0 else m[i][8] / m[i][i] }
    }

    fun apply(h: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
        val w = h[6] * x + h[7] * y + h[8]
        return Pair((h[0] * x + h[1] * y + h[2]) / w, (h[3] * x + h[4] * y + h[5]) / w)
    }

    private fun sample(p: Pixels, x: Double, y: Double): Int {
        val x0 = x.toInt().coerceIn(0, p.width - 1)
        val y0 = y.toInt().coerceIn(0, p.height - 1)
        val x1 = min(x0 + 1, p.width - 1)
        val y1 = min(y0 + 1, p.height - 1)
        val fx = (x - x0).coerceIn(0.0, 1.0)
        val fy = (y - y0).coerceIn(0.0, 1.0)
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        var out = 0xFF shl 24
        for (s in intArrayOf(16, 8, 0)) {
            val a = ch(p.argb[y0 * p.width + x0], s) * (1 - fx) + ch(p.argb[y0 * p.width + x1], s) * fx
            val b = ch(p.argb[y1 * p.width + x0], s) * (1 - fx) + ch(p.argb[y1 * p.width + x1], s) * fx
            out = out or ((a * (1 - fy) + b * fy).roundToInt().coerceIn(0, 255) shl s)
        }
        return out
    }

    private fun downscale(p: Pixels, f: Double): Pixels {
        val w = max(1, (p.width * f).roundToInt())
        val h = max(1, (p.height * f).roundToInt())
        // box average, so small details don't alias
        val out = IntArray(w * h)
        val step = 1 / f
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = (x * step).toInt(); val x1 = min(p.width, ((x + 1) * step).toInt().coerceAtLeast(x0 + 1))
            val y0 = (y * step).toInt(); val y1 = min(p.height, ((y + 1) * step).toInt().coerceAtLeast(y0 + 1))
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            val sy = max(1, (y1 - y0) / 4); val sx = max(1, (x1 - x0) / 4)
            var yy = y0
            while (yy < y1) {
                var xx = x0
                while (xx < x1) {
                    val c = p.argb[yy * p.width + xx]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                    xx += sx
                }
                yy += sy
            }
            out[y * w + x] = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        return Pixels(w, h, out)
    }

    /** The colour channels of a picture (red, green, blue), sampled between pixels. */
    private class Channels(p: Pixels) {
        private val w = p.width
        private val h = p.height
        private val v = Array(3) { k -> val s = 16 - 8 * k; FloatArray(w * h) { i -> ((p.argb[i] shr s) and 0xFF).toFloat() } }
        fun at(k: Int, x: Double, y: Double): Double {
            if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return 0.0
            val c = v[k]
            val x0 = x.toInt(); val y0 = y.toInt()
            val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
            val fx = x - x0; val fy = y - y0
            val a = c[y0 * w + x0] * (1 - fx) + c[y0 * w + x1] * fx
            val b = c[y1 * w + x0] * (1 - fx) + c[y1 * w + x1] * fx
            return a * (1 - fy) + b * fy
        }
    }
}
