package com.monkaydee.tcgcatalogue.grade

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ConfirmedOutlineTest {
    private fun photo(): Bitmap = Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.GRAY)
        val c = Canvas(this); val p = Paint()
        p.color=Color.YELLOW; c.drawRect(80f,150f,720f,1050f,p)
        p.color=Color.DKGRAY; c.drawRect(100f,170f,700f,1030f,p)
        p.color=Color.RED; c.drawRect(80f,150f,92f,1050f,p)
        p.color=Color.GREEN; c.drawRect(708f,150f,720f,1050f,p)
        p.color=Color.BLUE; c.drawRect(80f,150f,720f,162f,p)
        p.color=Color.MAGENTA; c.drawRect(80f,1038f,720f,1050f,p)
        p.color=Color.WHITE; for (i in 200..980 step 40) c.drawRect(140f,i.toFloat(),660f,i+2f,p)
    }
    private fun full() = Quad(Pt(80.0,150.0),Pt(720.0,150.0),Pt(720.0,1050.0),Pt(80.0,1050.0))
    private fun assertEdges(side: PreGrader.Side) {
        val b=side.card
        assertEquals(Color.RED,b.getPixel(5,b.height/2))
        assertEquals(Color.GREEN,b.getPixel(b.width-6,b.height/2))
        assertEquals(Color.BLUE,b.getPixel(b.width/2,5))
        assertEquals(Color.MAGENTA,b.getPixel(b.width/2,b.height-6))
        assertTrue(side.outlineConfirmed)
        assertEquals(full(),side.quad)
    }
    @Test fun confirmationPreservesAllFourPhysicalEdgesAndOriginalPhoto() {
        val photo=photo()
        val side=PreGrader.fromOutline(PreGrader.Outline(photo,full()))
        assertEdges(side)
        assertSame(photo,side.photo)
        assertEquals(Color.GRAY,side.photo!!.getPixel(0,0))
    }
    @Test fun correctingAnInsideArtworkCropRecoversPhysicalEdgesWithoutSnappingOrTrimming() {
        val photo=photo()
        val wrong=Quad(Pt(100.0,170.0),Pt(700.0,170.0),Pt(700.0,1030.0),Pt(100.0,1030.0))
        val cropped=PreGrader.fromOutline(PreGrader.Outline(photo,wrong))
        val corrected=PreGrader.adjust(cropped,full())
        assertEdges(corrected)
        assertSame(photo,corrected.photo)
    }
    @Test fun failedDetectionKeepsOriginalForManualRecoveryAndDoesNotInventEvidence() {
        val photo=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val draft=PreGrader.prepareOutline(photo)
        assertFalse(draft.detected)
        assertSame(photo,draft.photo)
        assertEquals(PreGrader.Outcome.NoCard,PreGrader.analyse(photo))
        val checked=PreGrader.fromOutline(draft)
        assertNull(AutomaticPreGrade.assess(checked,null))
    }
    @Test fun boundedWorkingImageRetainsWholePhotoAndSourceScale() {
        val photo=Bitmap.createBitmap(1000,2800,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val draft=PreGrader.prepareOutline(photo)
        assertEquals(2400,draft.photo.height)
        assertEquals(1000,draft.photoWidth)
        assertEquals(photo.width.toDouble()/photo.height,draft.photo.width.toDouble()/draft.photo.height,.001)
        assertFalse(photo.isRecycled)
    }
    @Test fun crossedOverlappingAndOutOfPhotoCornersAreRejected() {
        val draft=PreGrader.Outline(photo(),full())
        val q=full()
        for (bad in listOf(Quad(q.tl,q.br,q.tr,q.bl),Quad(q.tl,q.tl,q.br,q.bl),q.copy(tl=Pt(-1.0,150.0)))) {
            assertThrows(IllegalArgumentException::class.java) { PreGrader.fromOutline(draft,bad) }
        }
    }
}
