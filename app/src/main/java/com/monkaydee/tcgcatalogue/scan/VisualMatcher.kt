package com.monkaydee.tcgcatalogue.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt

/**
 * Tells apart printings that share a card number (One Piece alt arts, parallel and special
 * versions of the indexed games, same-size Pokémon sets) by comparing the scanned picture with
 * each candidate's image: a coarse colour layout of the card, robust to lighting and blur.
 */
object VisualMatcher {
    private const val GRID_W = 12
    private const val GRID_H = 16

    /** Similarity (-1..1) above which a picture is taken as recognised. */
    private const val MIN_MATCH = 0.35

    /** How much better than the next one the best picture must be to choose it. */
    private const val MIN_MARGIN = 0.04

    /** Weight of the picture compared with the number, name and set-code match. */
    private const val WEIGHT = 0.6

    /**
     * Reorders [candidates] by how well their picture matches [scan] and preselects the printing
     * whose picture matches. Unchanged when there is nothing to choose between or no picture.
     */
    suspend fun rank(context: Context, scan: Bitmap?, candidates: List<CardCandidate>): List<CardCandidate> {
        if (scan == null || candidates.isEmpty()) return candidates
        // The pictures to compare: per printing when printings have their own picture.
        val options = candidates.flatMapIndexed { i, c ->
            val own = c.variants.filter { it.imageUrl != null }.distinctBy { it.imageUrl }
            if (own.size > 1) own.map { Triple(i, it.key, it.imageUrl!!) } else listOfNotNull(c.imageUrl?.let { Triple(i, null, it) })
        }.take(16)
        if (options.size < 2) return candidates
        val target = withContext(Dispatchers.Default) { signature(scan, trim = false) } ?: return candidates
        val scores = coroutineScope {
            options.map { (_, _, url) ->
                async {
                    val bitmap = load(context, url) ?: return@async null
                    withContext(Dispatchers.Default) { signature(bitmap, trim = true)?.let { similarity(target, it) } }
                }
            }.awaitAll()
        }
        if (scores.count { it != null } < 2) return candidates

        return candidates.mapIndexed { i, c ->
            val mine = options.indices.filter { options[it].first == i && scores[it] != null }.sortedByDescending { scores[it]!! }
            if (mine.isEmpty()) return@mapIndexed c
            val best = scores[mine[0]]!!
            val second = mine.getOrNull(1)?.let { scores[it]!! }
            val variant = options[mine[0]].second
                ?.takeIf { best >= MIN_MATCH && (second == null || best - second >= MIN_MARGIN) && c.preferredVariant == null }
            c.copy(score = c.score + WEIGHT * best, preferredVariant = variant ?: c.preferredVariant)
        }.sortedByDescending { it.score }
    }

    /** True when the printings of [c] look different and the scan didn't tell which one it is, so the user must choose. */
    fun needsChoice(c: CardCandidate): Boolean =
        c.preferredVariant == null && c.variants.mapNotNull { it.imageUrl }.distinct().size > 1

    private suspend fun load(context: Context, url: String): Bitmap? = withTimeoutOrNull(8000) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(180, 252)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withTimeoutOrNull null
        (result.drawable as? BitmapDrawable)?.bitmap
    }

    /**
     * A card picture as a small grid of colours, each channel normalised so that brightness and
     * white balance don't matter. [trim] cuts away plain margins around the card (shop images).
     */
    fun signature(source: Bitmap, trim: Boolean): DoubleArray? {
        if (source.width < 8 || source.height < 8) return null
        val small = Bitmap.createScaledBitmap(source, 90, (90f * source.height / source.width).toInt().coerceAtLeast(8), true)
        val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val w = small.width
        val h = small.height
        if (small !== source) small.recycle()
        val (left, top, right, bottom) = if (trim) contentBox(pixels, w, h) else intArrayOf(0, 0, w, h).toList()
        // Only the middle of the card: the edges are the least reliable part of a scan (frame, sleeve, background).
        val insetX = (right - left) * 0.08
        val insetY = (bottom - top) * 0.06
        val x0 = left + insetX
        val y0 = top + insetY
        val cw = (right - left) - 2 * insetX
        val ch = (bottom - top) - 2 * insetY
        if (cw < GRID_W || ch < GRID_H) return null
        val out = DoubleArray(GRID_W * GRID_H * 3)
        for (gy in 0 until GRID_H) for (gx in 0 until GRID_W) {
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var n = 0
            val xs = (x0 + gx * cw / GRID_W).toInt()
            val xe = (x0 + (gx + 1) * cw / GRID_W).toInt().coerceAtMost(w)
            val ys = (y0 + gy * ch / GRID_H).toInt()
            val ye = (y0 + (gy + 1) * ch / GRID_H).toInt().coerceAtMost(h)
            for (y in ys until ye) for (x in xs until xe) {
                val p = pixels[y * w + x]
                r += Color.red(p)
                g += Color.green(p)
                b += Color.blue(p)
                n++
            }
            val cell = (gy * GRID_W + gx) * 3
            if (n > 0) {
                out[cell] = r / n
                out[cell + 1] = g / n
                out[cell + 2] = b / n
            }
        }
        for (channel in 0 until 3) normalise(out, channel)
        return out
    }

    /** Pearson correlation of two signatures, -1..1. */
    fun similarity(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return dot / a.size
    }

    /** Zero mean and unit variance per colour channel. */
    private fun normalise(v: DoubleArray, channel: Int) {
        val idx = (channel until v.size step 3).toList()
        val mean = idx.sumOf { v[it] } / idx.size
        val sd = sqrt(idx.sumOf { (v[it] - mean) * (v[it] - mean) } / idx.size).coerceAtLeast(1.0)
        idx.forEach { v[it] = (v[it] - mean) / sd }
    }

    /** Bounds of the card inside a picture with a plain or transparent margin (left, top, right, bottom). */
    private fun contentBox(px: IntArray, w: Int, h: Int): List<Int> {
        val corners = listOf(px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1])
        val bg = corners[0]
        fun background(p: Int) = Color.alpha(p) < 128 ||
            Color.alpha(bg) >= 128 && kotlin.math.abs(Color.red(p) - Color.red(bg)) + kotlin.math.abs(Color.green(p) - Color.green(bg)) + kotlin.math.abs(Color.blue(p) - Color.blue(bg)) < 36
        // Only trim when the corners agree on a margin colour.
        if (corners.any { kotlin.math.abs(Color.red(it) - Color.red(bg)) + kotlin.math.abs(Color.green(it) - Color.green(bg)) + kotlin.math.abs(Color.blue(it) - Color.blue(bg)) > 36 && Color.alpha(it) >= 128 }) {
            return listOf(0, 0, w, h)
        }
        fun rowContent(y: Int) = (0 until w).count { !background(px[y * w + it]) } > w / 10
        fun colContent(x: Int) = (0 until h).count { !background(px[it * w + x]) } > h / 10
        val top = (0 until h).firstOrNull(::rowContent) ?: 0
        val bottom = (h - 1 downTo 0).firstOrNull(::rowContent)?.plus(1) ?: h
        val left = (0 until w).firstOrNull(::colContent) ?: 0
        val right = (w - 1 downTo 0).firstOrNull(::colContent)?.plus(1) ?: w
        return if (right - left < w / 3 || bottom - top < h / 3) listOf(0, 0, w, h) else listOf(left, top, right, bottom)
    }
}
