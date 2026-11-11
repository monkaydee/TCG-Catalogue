package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AutoAlignmentTest {
    @Test fun agreesOnIndependentEdgesAndCorrectsRotation() {
        val w=300;val h=420;val angle=Math.toRadians(2.0)
        val card=Pixels(w,h,IntArray(w*h) { index ->
            val x=index%w-w/2.0;val y=index/w-h/2.0
            val px=cos(angle)*x+sin(angle)*y;val py=-sin(angle)*x+cos(angle)*y
            if (abs(px)<w*.45 && abs(py)<h*.45) 0xFF444444.toInt() else 0xFFCCCCCC.toInt()
        })
        val correction=AutoAlignment.correction(card)
        assertNotNull(correction);assertEquals(-2.0,correction!!,.4)
    }
    @Test fun uniformAndNoFrameImagesAbstain() {
        assertNull(AutoAlignment.correction(Pixels(100,140,IntArray(14000){0xFFAAAAAA.toInt()})))
    }
    @Test fun printedWhiteDoesNotBecomeGlareRejectionOnSharpArtwork() {
        val w=100;val h=140
        val card=Pixels(w,h,IntArray(w*h){if(it%w<40) 0xFFFFFFFF.toInt() else if(it%2==0) 0xFF555555.toInt() else 0xFF999999.toInt()})
        assertFalse(PhotoCheck.problems(card,800.0).contains(PhotoCheck.Problem.GLARE))
    }
}
