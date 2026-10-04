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
    )

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
        // The guide only helps when the card is roughly in it; otherwise search the photo.
        val quad = (hint?.let { CardRectifier.findQuad(p, it) } ?: CardRectifier.findQuad(p)) ?: return Outcome.NoCard
        val upright = upright(quad)
        val flat = CardRectifier.warp(p, upright)
        val c = upright.corners
        val widthInPhoto = (hypot(c[1].x - c[0].x, c[1].y - c[0].y) + hypot(c[2].x - c[3].x, c[2].y - c[3].y)) / 2 / scale
        val bitmap = Bitmap.createBitmap(flat.argb, flat.width, flat.height, Bitmap.Config.ARGB_8888)
        if (work !== photo) work.recycle()
        return Outcome.Ok(
            Side(
                card = bitmap,
                problems = PhotoCheck.problems(flat, widthInPhoto),
                centering = Centering.measure(flat),
                wear = Wear.measure(flat),
            ),
        )
    }

    /** A card lying on its side is turned upright (its short sides become top and bottom). */
    private fun upright(q: Quad): Quad {
        val c = q.corners
        val top = hypot(c[1].x - c[0].x, c[1].y - c[0].y)
        val left = hypot(c[3].x - c[0].x, c[3].y - c[0].y)
        return if (top > left) Quad(c[3], c[0], c[1], c[2]) else q
    }
}
