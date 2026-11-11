package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCheckTest {
    @Test fun tinyImagesAreBlurryInsteadOfCrashingOrProducingNaN() {
        for (size in 1..4) {
            val pixels = Pixels(size, size, IntArray(size * size) { 0xff888888.toInt() })
            assertEquals(0.0, PhotoCheck.sharpness(pixels), 0.0)
            assertTrue(PhotoCheck.problems(pixels, size.toDouble()).contains(PhotoCheck.Problem.BLURRY))
        }
    }

    @Test fun psaTenUsesCurrentFiftyFiveFortyFiveFrontTolerance() {
        assertEquals("10", Centering.Company.PSA.bestGrade(55.0, 75.0))
        assertEquals("9", Centering.Company.PSA.bestGrade(56.0, 75.0))
    }
}
