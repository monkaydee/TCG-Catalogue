package com.monkaydee.tcgcatalogue.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** An image as ARGB pixels, independent of Android so the matching can be tested on the JVM. */
class Pixels(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height)
    }
}

/** A card-shaped area of a picture, in pixels. */
data class CardRect(val x: Double, val y: Double, val w: Double, val h: Double)

/**
 * What a card picture looks like: [coarse] is its colour layout (finds the card and tells most
 * artworks apart), [fine] the detail of its brightness and texture (tells nearly identical
 * artworks apart, e.g. the same character on a plain or a manga background).
 */
class CardSignature(val coarse: DoubleArray, val fine: DoubleArray)

/** How well a photo matches one artwork, coarse and fine, each -1..1. */
data class ArtMatch(val coarse: Double, val fine: Double)

/**
 * Finds which artwork a photo shows. Each card picture is reduced to a coarse colour layout
 * (a grid of mean colours, normalised for brightness and white balance). Instead of trusting a
 * fixed crop, the photo is searched for the card-shaped area that best matches the artworks, so a
 * card that is small, off-centre or not quite filling the camera guide is still compared exactly.
 */
object CardVision {
    const val CARD_ASPECT = 63.0 / 88.0
    private const val GRID_W = 12
    private const val GRID_H = 16
    private const val FINE_W = 21
    private const val FINE_H = 28

    /** Signature of a card picture (a shop or database image), with plain margins cut away. */
    fun reference(p: Pixels): CardSignature? {
        val integral = Integral(p)
        val box = contentBox(p)
        return CardSignature(integral.signature(box) ?: return null, integral.fine(box) ?: return null)
    }

    /**
     * How well each reference signature matches the card in [scene], -1..1 (null when the
     * scene is too small). [minHeight]..[maxHeight] limit the card's size as a fraction of the
     * scene's height.
     */
    fun scores(scene: Pixels, refs: List<CardSignature>, minHeight: Double = 0.3, maxHeight: Double = 1.0): List<ArtMatch>? {
        if (refs.isEmpty() || scene.width < 24 || scene.height < 24) return null
        val integral = Integral(scene)
        // 1) Find the card: the card-shaped area most like any of the artworks.
        val coarse = rects(scene.width, scene.height, minHeight, maxHeight, step = 0.07, scaleStep = 1.08)
        val ranked = coarse.mapNotNull { r -> integral.signature(r)?.let { s -> r to refs.maxOf { similarity(s, it.coarse) } } }
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return null
        // 2) Refine the few best areas, then compare every artwork on the area found and close
        // by (the framing of a photo is never exact).
        val seeds = ranked.take(4).map { it.first }
        val refined = seeds.flatMap { around(it, scene.width, scene.height, 0.03, 1.03) }
            .mapNotNull { r -> integral.signature(r)?.let { s -> Triple(r, s, refs.maxOf { similarity(s, it.coarse) }) } }
            .maxByOrNull { it.third } ?: return null
        val near = around(refined.first, scene.width, scene.height, 0.02, 1.02)
        val nearCoarse = near.mapNotNull { integral.signature(it) } + listOf(refined.second)
        val nearFine = near.mapNotNull { integral.fine(it) }
        return refs.map { ref ->
            ArtMatch(nearCoarse.maxOf { similarity(it, ref.coarse) }, nearFine.maxOfOrNull { similarity(it, ref.fine) } ?: 0.0)
        }
    }

    /** Card-shaped areas of a [w]×[h] picture, from [minHeight] to [maxHeight] of its height. */
    internal fun rects(w: Int, h: Int, minHeight: Double, maxHeight: Double, step: Double, scaleStep: Double): List<CardRect> {
        val out = ArrayList<CardRect>()
        val largest = min(h.toDouble() * maxHeight, w / CARD_ASPECT)
        var ch = largest
        while (ch >= h * minHeight && ch >= 24) {
            val cw = ch * CARD_ASPECT
            val stepPx = max(1.0, ch * step)
            var y = 0.0
            while (y + ch <= h + 0.5) {
                var x = 0.0
                while (x + cw <= w + 0.5) {
                    out += CardRect(x, y, cw, ch)
                    x += stepPx
                }
                // The last position touches the far edge, so the edges are always covered.
                if (x - stepPx + cw < w - 1) out += CardRect(w - cw, y, cw, ch)
                y += stepPx
            }
            ch /= scaleStep
        }
        return out
    }

    /** Small shifts and size changes of [r] that stay inside the picture. */
    private fun around(r: CardRect, w: Int, h: Int, shift: Double, scale: Double): List<CardRect> {
        val out = ArrayList<CardRect>()
        for (s in listOf(1 / scale, 1.0, scale)) {
            val ch = r.h * s
            val cw = ch * CARD_ASPECT
            if (ch > h || cw > w) continue
            val cx = r.x + r.w / 2
            val cy = r.y + r.h / 2
            for (dy in -1..1) for (dx in -1..1) {
                val x = (cx - cw / 2 + dx * shift * ch).coerceIn(0.0, w - cw)
                val y = (cy - ch / 2 + dy * shift * ch).coerceIn(0.0, h - ch)
                out += CardRect(x, y, cw, ch)
            }
        }
        return out
    }

    /** Pearson correlation of two signatures, -1..1. */
    fun similarity(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return dot / a.size
    }

    /** Sums of each colour channel over rectangles in constant time. */
    private class Integral(p: Pixels) {
        val w = p.width
        val h = p.height
        private val stride = w + 1
        private val r = LongArray(stride * (h + 1))
        private val g = LongArray(stride * (h + 1))
        private val b = LongArray(stride * (h + 1))
        private val l = LongArray(stride * (h + 1))
        private val l2 = LongArray(stride * (h + 1))

        init {
            for (y in 0 until h) {
                var sr = 0L
                var sg = 0L
                var sb = 0L
                var sl = 0L
                var sl2 = 0L
                for (x in 0 until w) {
                    val c = p.argb[y * w + x]
                    // Transparent pixels (rounded corners of database images) count as white card border.
                    val a = (c ushr 24) and 0xFF
                    val cr = if (a < 128) 255 else (c shr 16) and 0xFF
                    val cg = if (a < 128) 255 else (c shr 8) and 0xFF
                    val cb = if (a < 128) 255 else c and 0xFF
                    val lum = (cr * 77 + cg * 150 + cb * 29) shr 8
                    sr += cr
                    sg += cg
                    sb += cb
                    sl += lum
                    sl2 += lum * lum
                    val i = (y + 1) * stride + x + 1
                    r[i] = r[i - stride] + sr
                    g[i] = g[i - stride] + sg
                    b[i] = b[i - stride] + sb
                    l[i] = l[i - stride] + sl
                    l2[i] = l2[i - stride] + sl2
                }
            }
        }

        private fun sum(a: LongArray, x0: Int, y0: Int, x1: Int, y1: Int) =
            a[y1 * stride + x1] - a[y0 * stride + x1] - a[y1 * stride + x0] + a[y0 * stride + x0]

        /** The grid of mean colours inside [rect], leaving out its outer edge (sleeve, frame, background). */
        fun signature(rect: CardRect): DoubleArray? {
            val x0 = rect.x + rect.w * 0.07
            val y0 = rect.y + rect.h * 0.05
            val cw = rect.w * 0.86
            val ch = rect.h * 0.90
            if (cw < GRID_W || ch < GRID_H) return null
            val out = DoubleArray(GRID_W * GRID_H * 3)
            for (gy in 0 until GRID_H) {
                val ys = (y0 + gy * ch / GRID_H).roundToInt().coerceIn(0, h - 1)
                val ye = (y0 + (gy + 1) * ch / GRID_H).roundToInt().coerceIn(ys + 1, h)
                for (gx in 0 until GRID_W) {
                    val xs = (x0 + gx * cw / GRID_W).roundToInt().coerceIn(0, w - 1)
                    val xe = (x0 + (gx + 1) * cw / GRID_W).roundToInt().coerceIn(xs + 1, w)
                    val n = ((xe - xs) * (ye - ys)).toDouble()
                    val i = (gy * GRID_W + gx) * 3
                    out[i] = sum(r, xs, ys, xe, ye) / n
                    out[i + 1] = sum(g, xs, ys, xe, ye) / n
                    out[i + 2] = sum(b, xs, ys, xe, ye) / n
                }
            }
            for (channel in 0 until 3) normalise(out, channel)
            return out
        }

        /** A finer grid of brightness and of texture (how busy each cell is), each normalised. */
        fun fine(rect: CardRect): DoubleArray? {
            val x0 = rect.x + rect.w * 0.07
            val y0 = rect.y + rect.h * 0.05
            val cw = rect.w * 0.86
            val ch = rect.h * 0.90
            if (cw < FINE_W || ch < FINE_H) return null
            val out = DoubleArray(FINE_W * FINE_H * 2)
            for (gy in 0 until FINE_H) {
                val ys = (y0 + gy * ch / FINE_H).roundToInt().coerceIn(0, h - 1)
                val ye = (y0 + (gy + 1) * ch / FINE_H).roundToInt().coerceIn(ys + 1, h)
                for (gx in 0 until FINE_W) {
                    val xs = (x0 + gx * cw / FINE_W).roundToInt().coerceIn(0, w - 1)
                    val xe = (x0 + (gx + 1) * cw / FINE_W).roundToInt().coerceIn(xs + 1, w)
                    val n = ((xe - xs) * (ye - ys)).toDouble()
                    val mean = sum(l, xs, ys, xe, ye) / n
                    val variance = (sum(l2, xs, ys, xe, ye) / n - mean * mean).coerceAtLeast(0.0)
                    val i = (gy * FINE_W + gx) * 2
                    out[i] = mean
                    out[i + 1] = sqrt(variance)
                }
            }
            normalise(out, 0, 2)
            normalise(out, 1, 2)
            return out
        }
    }

    /** Zero mean and unit variance per colour channel, so lighting and white balance don't count. */
    private fun normalise(v: DoubleArray, channel: Int, channels: Int = 3) {
        var mean = 0.0
        var n = 0
        var i = channel
        while (i < v.size) { mean += v[i]; n++; i += channels }
        mean /= n
        var variance = 0.0
        i = channel
        while (i < v.size) { variance += (v[i] - mean) * (v[i] - mean); i += channels }
        val sd = sqrt(variance / n).coerceAtLeast(1.0)
        i = channel
        while (i < v.size) { v[i] = (v[i] - mean) / sd; i += channels }
    }

    /** Where the card is in a shop or database image with a plain or transparent margin. */
    internal fun contentBox(p: Pixels): CardRect {
        val w = p.width
        val h = p.height
        val px = p.argb
        val bg = px[0]
        fun alpha(c: Int) = (c ushr 24) and 0xFF
        fun diff(a: Int, b: Int) = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
        val corners = listOf(px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1])
        val full = CardRect(0.0, 0.0, w.toDouble(), h.toDouble())
        val transparent = corners.all { alpha(it) < 128 }
        // Only trim when the corners agree on a margin: transparent, or all the same plain colour.
        if (!transparent && corners.any { alpha(it) < 128 || diff(it, bg) > 36 }) return full
        fun background(c: Int) = alpha(c) < 128 || !transparent && diff(c, bg) < 36
        fun rowContent(y: Int) = (0 until w).count { !background(px[y * w + it]) } > w / 4
        fun colContent(x: Int) = (0 until h).count { !background(px[it * w + x]) } > h / 4
        val top = (0 until h).firstOrNull(::rowContent) ?: 0
        val bottom = (h - 1 downTo 0).firstOrNull(::rowContent)?.plus(1) ?: h
        val left = (0 until w).firstOrNull(::colContent) ?: 0
        val right = (w - 1 downTo 0).firstOrNull(::colContent)?.plus(1) ?: w
        if (right - left < w / 3 || bottom - top < h / 3) return full
        return CardRect(left.toDouble(), top.toDouble(), (right - left).toDouble(), (bottom - top).toDouble())
    }

    /**
     * The artwork a photo shows ([index] into the artworks) and whether the photo really proves
     * it. [lookAlikes] are the artworks it can't be told apart from (itself included), e.g. a
     * reprint of the same art; the first of them is chosen.
     */
    data class ArtDecision(val index: Int, val sure: Boolean, val lookAlikes: List<Int> = listOf(index))

    /**
     * Which artwork the photo shows, from its [matches] with each artwork in [refs] (in the order
     * of the printings, the standard print first).
     *
     * The colour layout decides when one artwork is clearly the best; close calls are settled by
     * the fine detail. When that still can't separate them (a reprint of the same art, a photo
     * that matches none), the first of the tied artworks is taken, not sure: a special printing is
     * only chosen when the photo really shows it.
     */
    fun decide(matches: List<ArtMatch>, refs: List<CardSignature>): ArtDecision? {
        val best = matches.indices.maxByOrNull { matches[it].coarse } ?: return null
        if (matches[best].coarse < MIN_SCORE) return ArtDecision(0, sure = false, lookAlikes = matches.indices.toList())
        val close = matches.indices.filter { matches[best].coarse - matches[it].coarse < MIN_MARGIN }
        val byFine = close.sortedByDescending { matches[it].fine }
        val winner = when {
            close.size == 1 -> best
            matches[byFine[0]].fine - matches[byFine[1]].fine >= FINE_MARGIN -> byFine[0]
            else -> close.filter { matches[byFine[0]].fine - matches[it].fine < FINE_MARGIN }.min()
        }
        // Printings that look alike in the shop images too (a reprint, a foil version of the same
        // art) aren't told apart reliably by a photo: take the first of them, the original print.
        val lookAlikes = close.filter { it == winner || similarity(refs[it].coarse, refs[winner].coarse) >= LOOK_ALIKE }
        if (lookAlikes.size == 1) {
            val tie = close.size > 1 && matches[byFine[0]].fine - matches[byFine[1]].fine < FINE_MARGIN
            return ArtDecision(winner, sure = !tie, lookAlikes = if (tie) close else lookAlikes)
        }
        return ArtDecision(lookAlikes.min(), sure = false, lookAlikes = lookAlikes)
    }

    /** Shop images whose colour layouts correlate this much are versions of the same art. */
    const val LOOK_ALIKE = 0.92

    const val MIN_SCORE = 0.45
    const val MIN_MARGIN = 0.06
    const val FINE_MARGIN = 0.04
}
