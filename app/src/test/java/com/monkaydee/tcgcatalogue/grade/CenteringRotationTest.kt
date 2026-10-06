package com.monkaydee.tcgcatalogue.grade

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CenteringRotationTest {
    @Test fun zeroRotationPreservesImageAndCoordinates() {
        assertEquals(CenteringRotation.Bounds(600, 840), CenteringRotation.bounds(600, 840, 0.0))
        assertEquals(Pt(35.0, 70.0), CenteringRotation.sourcePoint(35.0, 70.0, 600, 840, 0.0))
    }

    @Test fun expandedCanvasContainsEveryCornerAndMapsBackToSource() {
        for (angle in listOf(-15.0, -2.0, -0.1, 0.0, 0.1, 2.0, 15.0)) {
            val bounds = CenteringRotation.bounds(600, 840, angle)
            val r = Math.toRadians(angle)
            for (point in listOf(Pt(0.0, 0.0), Pt(600.0, 0.0), Pt(600.0, 840.0), Pt(0.0, 840.0), Pt(35.0, 70.0))) {
                val x = bounds.width / 2.0 + cos(r) * (point.x - 300) - sin(r) * (point.y - 420)
                val y = bounds.height / 2.0 + sin(r) * (point.x - 300) + cos(r) * (point.y - 420)
                assertTrue(x in 0.0..bounds.width.toDouble())
                assertTrue(y in 0.0..bounds.height.toDouble())
                val restored = CenteringRotation.sourcePoint(x, y, 600, 840, angle)
                assertEquals(point.x, restored.x, 1e-8)
                assertEquals(point.y, restored.y, 1e-8)
            }
        }
    }

    @Test fun paddingChangesPreserveAsymmetricMeasurementsAndReset() {
        val original = CenteringRotation.bounds(600, 840, 0.0)
        val expanded = CenteringRotation.bounds(600, 840, 2.0)
        val guides = listOf(15.0 / 600, 35.0 / 600, 15.0 / 600, 45.0 / 600,
            15.0 / 840, 55.0 / 840, 15.0 / 840, 65.0 / 840)
        val rotated = CenteringRotation.reframeGuides(guides, original, expanded)
        val measured = requireNotNull(Centering.manual(expanded.width, expanded.height,
            (0..3).map { rotated[it * 2] }, (0..3).map { rotated[it * 2 + 1] }))
        assertEquals(20.0, measured.left, 1e-8)
        assertEquals(30.0, measured.right, 1e-8)
        assertEquals(40.0, measured.top, 1e-8)
        assertEquals(50.0, measured.bottom, 1e-8)
        assertEquals(40.0, measured.leftRight, 1e-8)
        guides.zip(CenteringRotation.reframeGuides(rotated, expanded, original)).forEach { (a, b) -> assertEquals(a, b, 1e-8) }
    }

    @Test fun gestureWrapDoesNotJumpNearlyAFullTurn() {
        fun delta(a: Double, b: Double): Double = CenteringRotation.angleDelta(
            cos(Math.toRadians(a)).toFloat(), sin(Math.toRadians(a)).toFloat(),
            cos(Math.toRadians(b)).toFloat(), sin(Math.toRadians(b)).toFloat())
        assertEquals(2.0, delta(179.0, -179.0), 1e-5)
        assertEquals(-2.0, delta(-179.0, 179.0), 1e-5)
        assertEquals(0.1, delta(2.0, 2.1), 1e-5)
    }

    @Test fun angleChangesAreManualEvenWhenWidthsDoNotChange() {
        val baseline = Centering.Result(20.0, 20.0, 20.0, 20.0)
        assertTrue(baseline.samePlacement(baseline.copy()))
        assertFalse(baseline.samePlacement(baseline.copy(rotationDegrees = 0.1)))
    }

    @Test fun invalidImageOrRotationIsRejected() {
        for ((w, h, angle) in listOf(Triple(0, 840, 0.0), Triple(600, -1, 0.0), Triple(600, 840, Double.NaN), Triple(600, 840, 15.1))) {
            assertThrows(IllegalArgumentException::class.java) { CenteringRotation.bounds(w, h, angle) }
        }
    }
}
