package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.abs

/** Motion is measured from consecutive images, independently of the phone's spirit level. */
class CaptureQuality {
    private var previous: IntArray? = null
    data class Result(val steady: Boolean, val sharp: Boolean, val exposed: Boolean, val clipped: Boolean) {
        val ready get() = steady && sharp && exposed && !clipped
        val message get() = when {
            !exposed -> "Add diffuse light"
            clipped -> "Possible glare: change lighting or angle"
            !sharp -> "Focus on the card; move slightly farther away"
            !steady -> "Hold still"
            else -> "Sharp and steady"
        }
    }
    fun check(card: Pixels): Result {
        val grid = IntArray(32 * 32) { i ->
            val x = ((i % 32 + .5) * card.width / 32).toInt().coerceAtMost(card.width - 1)
            val y = ((i / 32 + .5) * card.height / 32).toInt().coerceAtMost(card.height - 1)
            val c = card.argb[y * card.width + x]
            (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
        }
        val old = previous
        previous = grid
        val motion = old?.let { grid.indices.sumOf { i -> abs(grid[i] - it[i]).toDouble() } / grid.size } ?: 100.0
        return Result(motion < 2.5, PhotoCheck.sharpness(card) >= 3.5, grid.average() >= 35,
            PhotoCheck.glare(card) > .25 && PhotoCheck.sharpness(card) < 5)
    }
}
