package com.monkaydee.tcgcatalogue.grade

import kotlin.math.*

/** Expanded rotation canvas: no source corner is clipped and guide axes stay fixed. */
object CenteringRotation {
    const val LIMIT = 15.0
    data class Bounds(val width: Int, val height: Int)
    fun bounds(width: Int, height: Int, degrees: Double): Bounds {
        require(width > 0 && height > 0 && degrees.isFinite() && abs(degrees) <= LIMIT)
        val radians = Math.toRadians(degrees)
        val c = abs(cos(radians)); val s = abs(sin(radians))
        return Bounds(ceil(width * c + height * s).toInt(), ceil(width * s + height * c).toInt())
    }
    /** Canvas position mapped back to the unrotated image (used by the results overlay). */
    fun sourcePoint(x: Double, y: Double, width: Int, height: Int, degrees: Double): Pt {
        val b = bounds(width, height, degrees)
        val dx = x - b.width / 2.0; val dy = y - b.height / 2.0
        val r = Math.toRadians(degrees); val c = cos(r); val s = sin(r)
        return Pt(width / 2.0 + c * dx + s * dy, height / 2.0 - s * dx + c * dy)
    }
    /** Keep lines at the same distance from the image centre when padding changes. */
    fun reframeGuides(guides: List<Double>, old: Bounds, next: Bounds): List<Double> {
        require(guides.size == 8)
        return (0..3).flatMap { side ->
            val from = if (side < 2) old.width else old.height
            val to = if (side < 2) next.width else next.height
            val outer = ((guides[side * 2] * from + (to - from) / 2.0) / to).coerceIn(0.0, 0.48)
            val inner = ((guides[side * 2 + 1] * from + (to - from) / 2.0) / to).coerceIn(outer + 1.0 / to, 0.49)
            listOf(outer, inner)
        }
    }
    fun angleDelta(beforeX: Float, beforeY: Float, afterX: Float, afterY: Float): Double {
        val d = Math.toDegrees(atan2(afterY.toDouble(), afterX.toDouble()) - atan2(beforeY.toDouble(), beforeX.toDouble()))
        return (d + 540.0) % 360.0 - 180.0
    }
}
