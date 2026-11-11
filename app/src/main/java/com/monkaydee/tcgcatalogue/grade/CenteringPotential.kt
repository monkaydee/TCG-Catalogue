package com.monkaydee.tcgcatalogue.grade

/** PSA's published Gem Mint 10 centering criterion; never an overall grade prediction.
 * A one-image-pixel placement interval is a sensitivity check, not a statistical confidence. */
object CenteringPotential {
    enum class Status { WITHIN_10, BORDERLINE_10, BELOW_10 }
    fun assess(front: Centering.Result?, back: Centering.Result?, photosUsable: Boolean): Status? {
        if (!photosUsable || front == null || back == null) return null
        fun interval(c: Centering.Result): Pair<Double, Double>? {
            val v = listOf(c.left, c.right, c.top, c.bottom)
            if (v.any { !it.isFinite() || it <= 0 }) return null
            fun axis(a: Double, b: Double): Pair<Double, Double> {
                val low = 100 * (a - 1).coerceAtLeast(0.001) / ((a - 1).coerceAtLeast(0.001) + b + 1)
                val high = 100 * (a + 1) / (a + 1 + (b - 1).coerceAtLeast(0.001))
                val best = if (low <= 50 && high >= 50) 50.0 else minOf(maxOf(low, 100 - low), maxOf(high, 100 - high))
                return best to maxOf(high, 100 - low)
            }
            val h = axis(c.left, c.right); val vAxis = axis(c.top, c.bottom)
            return maxOf(h.first, vAxis.first) to maxOf(h.second, vAxis.second)
        }
        val f = interval(front) ?: return null; val b = interval(back) ?: return null
        return when {
            f.second <= 55.0 + 1e-9 && b.second <= 75.0 + 1e-9 -> Status.WITHIN_10
            f.first > 55.0 + 1e-9 || b.first > 75.0 + 1e-9 -> Status.BELOW_10
            else -> Status.BORDERLINE_10
        }
    }
}
