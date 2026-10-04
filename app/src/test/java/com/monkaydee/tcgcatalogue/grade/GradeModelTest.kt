package com.monkaydee.tcgcatalogue.grade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeModelTest {
    @Test
    fun probabilitiesFormADistribution() {
        val e = GradeModel.estimate(null, null)
        assertEquals(1.0, e.probabilities.values.sum(), 1e-9)
        assertTrue(e.probabilities.values.all { it >= 0 })
        assertTrue(e.low <= e.mostLikely && e.mostLikely <= e.high)
        val inRange = e.probabilities.filterKeys { it in e.low..e.high }.values.sum()
        assertTrue(inRange >= 0.75 || (e.low == e.probabilities.keys.min() && e.high == e.probabilities.keys.max()))
    }

    @Test
    fun weightsMatchFeatures() {
        val n = GradeWeights.FEATURES.size
        assertEquals(n, GradeWeights.MEAN.size)
        assertEquals(n, GradeWeights.SCALE.size)
        assertEquals(n, GradeWeights.CLIP_LOW.size)
        assertEquals(n, GradeWeights.CLIP_HIGH.size)
        assertEquals(GradeWeights.GRADES.size, GradeWeights.WEIGHTS.size)
        GradeWeights.WEIGHTS.forEach { assertEquals(n + 1, it.size) }
        assertEquals(n, GradeModel.features(null, null).size)
    }
}
