package com.monkaydee.tcgcatalogue.grade

import org.junit.Assert.*
import org.junit.Test

class CenteringPotentialTest {
    private fun borders(a: Double, b: Double) = Centering.Result(a, b, 50.0, 50.0)
    @Test fun completeMeasuredBordersCanMeetPublishedTenCriterion() {
        assertEquals(CenteringPotential.Status.WITHIN_10, CenteringPotential.assess(borders(50.0, 50.0), borders(60.0, 40.0), true))
    }
    @Test fun missingFrontIsNotReplacedWithPerfectCentering() {
        assertNull(CenteringPotential.assess(null, borders(50.0, 50.0), true))
        assertNull(CenteringPotential.assess(borders(50.0, 50.0), null, true))
    }
    @Test fun boundaryPlacementsAreMarkedUncertain() {
        assertEquals(CenteringPotential.Status.BORDERLINE_10, CenteringPotential.assess(borders(55.0, 45.0), borders(50.0, 50.0), true))
        assertEquals(CenteringPotential.Status.BORDERLINE_10, CenteringPotential.assess(borders(50.0, 50.0), borders(75.0, 25.0), true))
    }
    @Test fun unacceptableFrontOrBackCannotMeetTen() {
        assertEquals(CenteringPotential.Status.BELOW_10, CenteringPotential.assess(borders(60.0, 40.0), borders(50.0, 50.0), true))
        assertEquals(CenteringPotential.Status.BELOW_10, CenteringPotential.assess(borders(50.0, 50.0), borders(80.0, 20.0), true))
    }
    @Test fun invalidOrLowQualityInputsHaveNoPotential() {
        assertNull(CenteringPotential.assess(borders(50.0, 50.0), borders(50.0, 50.0), false))
        assertNull(CenteringPotential.assess(borders(Double.NaN, 50.0), borders(50.0, 50.0), true))
    }
}
