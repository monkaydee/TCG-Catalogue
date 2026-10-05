package com.monkaydee.tcgcatalogue.grade

import android.graphics.Bitmap
import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pre-grading of one card side, entirely on the phone: find the card in the photo, flatten it,
 * check the photo, then measure centering and the wear of corners and edges.
 */
object PreGrader {
    /** Longest side the photo is reduced to before searching (keeps memory and time in check). */
    private const val WORK_SIDE = 2400

    data class Side(
        /** The flattened card ([CardRectifier.W] x [CardRectifier.H]). */
        val card: Bitmap,
        val problems: List<PhotoCheck.Problem>,
        val centering: Centering.Result?,
        val wear: Wear.Result,
        /** The photo as measured and the card's outline in it, for adjusting the corners by hand. */
        val photo: Bitmap? = null,
        val quad: Quad? = null,
        /** Width of the original photo ([photo] may be scaled down for measuring). */
        val outlineConfirmed: Boolean = false,
        val photoWidth: Int = photo?.width ?: 0,
    ) {
        /** A grade estimate must not fill missing measurements with training averages. */
        val usableForGrade: Boolean get() = outlineConfirmed && problems.isEmpty() && centering != null
    }

    sealed interface Outcome {
        data class Ok(val side: Side) : Outcome
        /** No whole card could be found in the photo. */
        data object NoCard : Outcome
    }

    /**
     * Analyses [photo] (upright). [guide] is where the card should be, as fractions of the photo
     * (left, top, right, bottom), e.g. the camera's card guide; null searches the whole photo.
     */
    fun analyse(photo: Bitmap, guide: FloatArray? = null): Outcome {
        val scale = minOf(1.0, WORK_SIDE.toDouble() / max(photo.width, photo.height))
        val work = if (scale < 1) Bitmap.createScaledBitmap(photo, (photo.width * scale).roundToInt(), (photo.height * scale).roundToInt(), true) else photo
        val pixels = IntArray(work.width * work.height).also { work.getPixels(it, 0, work.width, 0, 0, work.width, work.height) }
        val p = Pixels(work.width, work.height, pixels)
        val hint = guide?.let { g ->
            Quad(
                Pt(g[0] * p.width.toDouble(), g[1] * p.height.toDouble()), Pt(g[2] * p.width.toDouble(), g[1] * p.height.toDouble()),
                Pt(g[2] * p.width.toDouble(), g[3] * p.height.toDouble()), Pt(g[0] * p.width.toDouble(), g[3] * p.height.toDouble()),
            )
        }
        // The card's own straight edges first: the guide is only roughly where the card lies (and a
        // refined guide can settle on the guide itself), so it is only the fallback.
        // With the camera guide, the card is what lies in the box; a gallery photo is searched whole.
        // Without an outline in the box, ask for a retake rather than measure something else.
        val quad = (if (hint != null) CardRectifier.findQuadIn(p, hint) else CardRectifier.findQuad(p)) ?: return Outcome.NoCard
        return Outcome.Ok(measure(work, p, quad, scale))
    }

    /**
     * [side] measured again with corners placed by hand ([quad], in [Side.photo] pixels). Each
     * side is first snapped onto the card's edge nearby, as a finger is not pixel-exact.
     */
    fun adjust(side: Side, quad: Quad): Side {
        val photo = side.photo ?: return side
        require(quad.corners.all { it.x.isFinite() && it.y.isFinite() && it.x >= 0 && it.y >= 0 && it.x < photo.width && it.y < photo.height })
        val p = Pixels(photo.width, photo.height, IntArray(photo.width * photo.height).also { photo.getPixels(it, 0, photo.width, 0, 0, photo.width, photo.height) })
        val scale = side.photo.width.toDouble() / side.photoWidth
        return measure(photo, p, CardRectifier.snap(p, quad), scale, side.photoWidth)
    }

    private fun measure(work: Bitmap, p: Pixels, quad: Quad, scale: Double, photoWidth: Int = (work.width / scale).roundToInt()): Side {
        val upright = upright(quad)
        val flat = trimmed(CardRectifier.warp(p, upright))
        val c = upright.corners
        val widthInPhoto = (hypot(c[1].x - c[0].x, c[1].y - c[0].y) + hypot(c[2].x - c[3].x, c[2].y - c[3].y)) / 2 / scale
        val bitmap = Bitmap.createBitmap(flat.argb, flat.width, flat.height, Bitmap.Config.ARGB_8888)
        return Side(
            card = bitmap,
            problems = PhotoCheck.problems(flat, widthInPhoto),
            centering = Centering.measure(flat),
            wear = Wear.measure(flat),
            photo = work,
            quad = quad,
            photoWidth = photoWidth,
        )
    }

    /** The straightened card with leftover background strips cut off, scaled back to the standard size. */
    private fun trimmed(flat: Pixels): Pixels {
        val l = Centering.cut(flat, 'L')
        val r = Centering.cut(flat, 'R')
        val t = Centering.cut(flat, 'T')
        val b = Centering.cut(flat, 'B')
        if (l + r + t + b == 0) return flat
        val full = Bitmap.createBitmap(flat.argb, flat.width, flat.height, Bitmap.Config.ARGB_8888)
        val cropped = Bitmap.createBitmap(full, l, t, flat.width - l - r, flat.height - t - b)
        val scaled = Bitmap.createScaledBitmap(cropped, flat.width, flat.height, true)
        val px = IntArray(flat.width * flat.height).also { scaled.getPixels(it, 0, flat.width, 0, 0, flat.width, flat.height) }
        listOf(full, cropped, scaled).distinct().forEach { it.recycle() }
        return Pixels(flat.width, flat.height, px)
    }

    /** A card lying on its side is turned upright (its short sides become top and bottom). */
    private fun upright(q: Quad): Quad {
        val c = q.corners
        val top = hypot(c[1].x - c[0].x, c[1].y - c[0].y)
        val left = hypot(c[3].x - c[0].x, c[3].y - c[0].y)
        return if (top > left) Quad(c[3], c[0], c[1], c[2]) else q
    }
}
