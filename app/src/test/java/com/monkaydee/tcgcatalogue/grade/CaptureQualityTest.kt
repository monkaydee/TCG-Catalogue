package com.monkaydee.tcgcatalogue.grade
import com.monkaydee.tcgcatalogue.scan.Pixels
import org.junit.Assert.*
import org.junit.Test
class CaptureQualityTest {
    private fun image(phase:Int=0)=Pixels(320,440,IntArray(320*440) { i ->
        if ((i%320/3+i/320/3+phase)%2==0) 0xFF444444.toInt() else 0xFFBBBBBB.toInt()
    })
    @Test fun firstFrameAndMovingFramesCannotTriggerAnAutomaticCapture() {
        val check=CaptureQuality()
        assertFalse(check.check(image()).ready)
        assertTrue(check.check(image()).ready)
        assertFalse(check.check(image(1)).ready)
    }
    @Test fun darkAndBlurredImagesStayBlockedEvenWhenStationary() {
        for (color in listOf(0xFF000000.toInt(),0xFF888888.toInt())) {
            val check=CaptureQuality();val photo=Pixels(100,140,IntArray(14000){color})
            check.check(photo);assertFalse(check.check(photo).ready)
        }
    }
}
