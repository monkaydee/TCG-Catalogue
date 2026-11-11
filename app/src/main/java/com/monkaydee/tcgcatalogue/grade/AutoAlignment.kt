package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.*

/** Robust printed-frame line fit. Abstains when independent sides disagree or artwork dominates. */
object AutoAlignment {
    fun correction(card: Pixels): Double? {
        if (card.width < 80 || card.height < 80) return null
        fun lum(x: Int, y: Int): Double {
            val c = card.argb[y * card.width + x]
            return ((c shr 16) and 255) * .299 + ((c shr 8) and 255) * .587 + (c and 255) * .114
        }
        val fits = "LRTB".mapNotNull { side ->
            val vertical = side == 'L' || side == 'R'
            val depth = if (vertical) card.width else card.height
            val length = if (vertical) card.height else card.width
            val points = (0 until 36).mapNotNull { i ->
                val along = (length * (.2 + .6 * i / 35)).roundToInt()
                fun value(pos: Int): Double {
                    val d = if (side == 'R' || side == 'B') depth - 1 - pos else pos
                    return if (vertical) lum(d, along) else lum(along, d)
                }
                val range = maxOf(2, (depth * .015).toInt()) until (depth * .13).toInt()
                val pos = range.maxByOrNull { abs(value(it + 1) - value(it - 1)) } ?: return@mapNotNull null
                if (abs(value(pos + 1) - value(pos - 1)) < 25) null
                else along.toDouble() to (if (side == 'R' || side == 'B') depth - 1 - pos else pos).toDouble()
            }
            if (points.size < 27) return@mapNotNull null
            val slopes = points.flatMapIndexed { i, a -> points.drop(i + 8).map { b -> (b.second - a.second) / (b.first - a.first) } }.sorted()
            if (slopes.isEmpty()) return@mapNotNull null
            val slope = slopes[slopes.size / 2]
            val intercepts = points.map { it.second - slope * it.first }.sorted()
            val intercept = intercepts[intercepts.size / 2]
            val supported = points.count { abs(it.second - slope * it.first - intercept) < depth * .003 }
            if (supported < points.size * .8) return@mapNotNull null
            Math.toDegrees(atan(slope)) * if (vertical) 1 else -1
        }.filter { abs(it) <= CenteringRotation.LIMIT }
        if (fits.size < 2) return null
        val median = fits.sorted()[fits.size / 2]
        val agreed = fits.filter { abs(it - median) < .4 }
        return if (agreed.size >= 2) agreed.average() else null
    }
}
