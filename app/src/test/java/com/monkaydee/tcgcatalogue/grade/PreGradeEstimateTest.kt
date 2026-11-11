package com.monkaydee.tcgcatalogue.grade

import android.graphics.Bitmap
import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreGradeEstimateTest {
    private fun side() = PreGrader.Side(Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888), emptyList(),
        Centering.Result(5.0,5.0,5.0,5.0), Wear.Result((Wear.CORNERS+Wear.EDGES).associateWith { Wear.Zone(0.0,0.0,0.0) }),
        outlineConfirmed=true,manualCentering=true, wearFindings=(Wear.CORNERS+Wear.EDGES).associateWith { Wear.Finding.NO_VISIBLE_DAMAGE },
        surfacePhotos=listOf("angle-a","angle-b"),surfaceFinding=SurfaceFinding.CLEAR_IN_PHOTOS)
    @Test fun manualGuidesCanSupportExperimentalRangeAfterAllReviews() {
        val s=side();assertNotNull(PreGradeEstimate.assess(s,s));assertFalse(s.usableForGrade)
    }
    @Test fun incompleteReviewsAndFailedPhotosAbstain() {
        val s=side()
        assertNull(PreGradeEstimate.assess(s.copy(surfacePhotos=emptyList()),s))
        assertNull(PreGradeEstimate.assess(s.copy(wearFindings=emptyMap()),s))
        assertNull(PreGradeEstimate.assess(s.copy(problems=listOf(PhotoCheck.Problem.BLURRY)),s))
    }
    @Test fun worseCenteringAndRecordedDamageNeverImproveRange() {
        val s=side();val clean=PreGradeEstimate.assess(s,s)!!
        val off=PreGradeEstimate.assess(s.copy(centering=Centering.Result(3.0,7.0,5.0,5.0)),s)!!
        assertTrue(off.high<clean.high)
        val damage=PreGradeEstimate.assess(s.copy(surfaceFinding=SurfaceFinding.DENT_OR_CREASE),s)!!
        assertTrue(damage.high<off.high);assertEquals(1,damage.low)
    }
}
