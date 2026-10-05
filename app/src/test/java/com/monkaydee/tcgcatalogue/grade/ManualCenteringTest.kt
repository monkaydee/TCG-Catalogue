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
