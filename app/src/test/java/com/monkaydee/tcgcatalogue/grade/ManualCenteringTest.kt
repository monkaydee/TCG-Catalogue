package com.monkaydee.tcgcatalogue.grade

import org.junit.Assert.*
import org.junit.Test

class ManualCenteringTest {
    @Test fun measuresBothAxesAgainstTheConfirmedCardEdges() {
        val measured = requireNotNull(Centering.manual(600, 840, listOf(0.06, 0.04, 0.035, 0.065)))
        assertEquals(60.0, measured.leftRight, 1e-9)
        assertEquals(35.0, measured.topBottom, 1e-9)
        assertEquals(65.0, measured.worst, 1e-9)
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), measured.cuts)
    }
    @Test fun independentOuterAndInnerPairsMeasureGapsAndPreserveAllPositions() {
        val result = requireNotNull(Centering.manual(1000, 1400,
            listOf(0.01, 0.02, 0.015, 0.005), listOf(0.06, 0.08, 0.055, 0.065)))
        assertEquals(50.0, result.left, 1e-9)
        assertEquals(60.0, result.right, 1e-9)
        assertEquals(56.0, result.top, 1e-9)
        assertEquals(84.0, result.bottom, 1e-9)
        assertEquals(100.0 * 50 / 110, result.leftRight, 1e-9)
        assertEquals(40.0, result.topBottom, 1e-9)
        assertEquals(listOf(10.0, 20.0, 21.0, 7.0), result.cuts)
    }
    @Test fun rejectsOuterEdgesCrossingTheirInnerFrames() {
        assertNull(Centering.manual(1000, 1400, listOf(0.06, 0.02, 0.01, 0.01), List(4) { 0.05 }))
        assertNull(Centering.manual(1000, 1400, List(4) { 0.05 }, List(4) { 0.05 }))
        assertNull(Centering.manual(1000, 1400, listOf(-0.01, 0.0, 0.0, 0.0), List(4) { 0.05 }))
    }

    @Test fun rejectsMissingCrossedAndNonFiniteGuides() {
        for (guides in listOf(listOf(0.1), listOf(0.0, 0.1, 0.1, 0.1), listOf(0.6, 0.6, 0.1, 0.1),
            listOf(Double.NaN, 0.1, 0.1, 0.1), listOf(0.1, 0.1, Double.POSITIVE_INFINITY, 0.1))) {
            assertNull(Centering.manual(600, 840, guides))
        }
        assertNull(Centering.manual(0, 840, List(4) { 0.05 }))
    }
    @Test fun ratiosDoNotChangeWithImageResolution() {
        val a = requireNotNull(Centering.manual(600, 840, listOf(0.045, 0.055, 0.05, 0.05)))
        val b = requireNotNull(Centering.manual(1200, 1680, listOf(0.045, 0.055, 0.05, 0.05)))
        assertEquals(a.leftRight, b.leftRight, 1e-9)
        assertEquals(a.topBottom, b.topBottom, 1e-9)
    }
}
