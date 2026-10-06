package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.R
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Turns the measurements into PSA grade probabilities. For every grade k there is a logistic
 * model of "graded k or better" over the same features; the chance of exactly k is the difference
 * of neighbouring ones. The weights are fitted on PSA's scans of graded cards (scripts/pregrade/
 * train_grade_model.py, which writes [GradeWeights]).
 */
object GradeModel {
    data class Estimate(
        /** PSA grade → probability. */
        val probabilities: Map<Int, Double>,
        val mostLikely: Int,
        /** The likely range: the smallest range of grades covering 75 % together. */
        val low: Int,
        val high: Int,
        /** What holds the grade back most (a string resource). */
        val limiting: Int,
    )

    /** The features, in the order of [GradeWeights.FEATURES]. Missing sides count as unknown (mean). */
    fun features(front: PreGrader.Side?, back: PreGrader.Side?): DoubleArray {
        val out = ArrayList<Double>()
        for (side in listOf(front, back)) {
            val c = side?.centering
            out += c?.worst ?: Double.NaN
            for (name in Wear.EDGES + Wear.CORNERS) {
                val z = side?.wear?.zones?.get(name)?.takeIf { it.evidence == Wear.Evidence.MEASURED }
                out += z?.let { ln(it.defects + GradeWeights.LOG_EPS) } ?: Double.NaN
                out += z?.let { ln(it.whitening + GradeWeights.LOG_EPS) } ?: Double.NaN
                out += z?.strength ?: Double.NaN
            }
        }
        return out.toDoubleArray()
    }

    /** Retired weights are available only for explicit offline research, never the product UI. */
    fun estimate(front: PreGrader.Side?, back: PreGrader.Side?, researchOnly: Boolean = false): Estimate {
        check(researchOnly) { "Scan-trained weights are unvalidated on phone photos; use PreGradeEstimate for the experimental UI" }
        val x = features(front, back)
        // unknown features take the training mean (no information either way)
        for (i in x.indices) {
            x[i] = if (x[i].isNaN()) GradeWeights.MEAN[i] else x[i].coerceIn(GradeWeights.CLIP_LOW[i], GradeWeights.CLIP_HIGH[i])
        }
        val z = DoubleArray(x.size) { i -> (x[i] - GradeWeights.MEAN[i]) / GradeWeights.SCALE[i] }
        // P(grade >= k) for k = 10, 9, ...: a lower bar can't be less likely, so keep it non-decreasing
        val atLeast = LinkedHashMap<Int, Double>()
        var prev = 0.0
        for ((k, w) in GradeWeights.GRADES.zip(GradeWeights.WEIGHTS)) {
            var s = w[0]
            for (i in z.indices) s += w[i + 1] * z[i]
            val p = maxOf(prev, 1 / (1 + exp(-s)))
            atLeast[k] = p
            prev = p
        }
        // exact grades: 10, 9, ..., lowest; the rest goes to "lowest - 1 or below"
        val probs = LinkedHashMap<Int, Double>()
        val ks = GradeWeights.GRADES // descending
        for ((i, k) in ks.withIndex()) {
            probs[k] = max(0.0, atLeast.getValue(k) - if (i == 0) 0.0 else atLeast.getValue(ks[i - 1]))
        }
        probs[ks.last() - 1] = max(0.0, 1 - atLeast.getValue(ks.last()))
        val total = probs.values.sum().takeIf { it > 0 } ?: 1.0
        val norm = probs.mapValues { it.value / total }
        val most = norm.maxBy { it.value }.key
        // smallest contiguous range around the most likely grade covering 75 %
        var lo = most
        var hi = most
        var mass = norm.getValue(most)
        while (mass < 0.75) {
            val up = norm[hi + 1] ?: -1.0
            val down = norm[lo - 1] ?: -1.0
            if (up < 0 && down < 0) break
            if (up >= down) { hi += 1; mass += up } else { lo -= 1; mass += down }
        }
        return Estimate(norm, most, lo, hi, limiting(front, back))
    }

    /** The weakest of centering, corners and edges, as a message. */
    private fun limiting(front: PreGrader.Side?, back: PreGrader.Side?): Int {
        val centering = maxOf((front?.centering?.worst ?: 50.0) - 55, ((back?.centering?.worst ?: 50.0) - 75) / 2)
        val corners = listOfNotNull(front, back).maxOfOrNull { s -> s.wear.corners.maxOf { zoneLevel(it) } } ?: 0
        val edges = listOfNotNull(front, back).maxOfOrNull { s -> s.wear.edges.maxOf { zoneLevel(it) } } ?: 0
        return when {
            corners >= 2 && corners >= edges -> R.string.grade_limit_corners
            edges >= 2 -> R.string.grade_limit_edges
            centering > 5 -> R.string.grade_limit_centering
            corners >= 1 || edges >= 1 -> R.string.grade_limit_light_wear
            else -> R.string.grade_limit_none
        }
    }

    /** 0 = clean, 1 = light, 2 = visible, 3 = heavy wear of one corner or edge. */
    fun zoneLevel(z: Wear.Zone): Int = when {
        z.evidence == Wear.Evidence.INSUFFICIENT -> -1
        z.defects < GradeWeights.ZONE_LEVELS[0] -> 0
        z.defects < GradeWeights.ZONE_LEVELS[1] -> 1
        z.defects < GradeWeights.ZONE_LEVELS[2] -> 2
        else -> 3
    }
}
