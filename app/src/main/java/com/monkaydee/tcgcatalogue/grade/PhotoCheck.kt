package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.abs

/**
 * Is a flattened card photo good enough to judge? Checks sharpness (fine detail along edges),
 * glare (blown-out white spots) and that the card was big enough in the photo.
 */
object PhotoCheck {
    enum class Problem { BLURRY, GLARE, TOO_SMALL }

    /** Sharpness: mean absolute Laplacian of the brightness, ignoring the outer 3 % (the cut). */
    fun sharpness(card: Pixels): Double {
        val w = card.width
        val h = card.height
        if (w < 3 || h < 3) return 0.0
        val m = maxOf(1, (0.03 * minOf(w, h)).toInt())
        fun l(x: Int, y: Int): Int { val c = card.argb[y * w + x]; return (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 }
        var sum = 0.0
        var n = 0
        var y = m
        while (y < h - m) {
            var x = m
            while (x < w - m) {
                sum += abs(4 * l(x, y) - l(x - 1, y) - l(x + 1, y) - l(x, y - 1) - l(x, y + 1))
                n++
                x += 2
            }
            y += 2
        }
        return if (n > 0) sum / n else 0.0
    }

    /** Share of nearly white, colourless pixels (reflections of a lamp or window). */
    fun glare(card: Pixels): Double {
        var n = 0
        for (c in card.argb) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r > 245 && g > 245 && b > 245) n++
        }
        return n.toDouble() / card.argb.size
    }

    /**
     * Problems with a photo. [cardWidthInPhoto] is how wide the card was in the original photo,
     * in pixels: below ~560 there is too little detail for corners and edges.
     */
    fun problems(card: Pixels, cardWidthInPhoto: Double): List<Problem> = buildList {
        if (cardWidthInPhoto < 560) add(Problem.TOO_SMALL)
        if (sharpness(card) < 3.0) add(Problem.BLURRY)
        if (glare(card) > 0.02) add(Problem.GLARE)
    }
}
