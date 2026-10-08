package com.monkaydee.tcgcatalogue.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.monkaydee.tcgcatalogue.data.*
import com.monkaydee.tcgcatalogue.grade.*
import com.monkaydee.tcgcatalogue.ui.screens.*
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = Application::class)
class OutlineRecoveryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun photo(): Bitmap = Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.GRAY)
        val canvas=Canvas(this); val paint=Paint()
        paint.color=Color.YELLOW; canvas.drawRect(80f,150f,720f,1050f,paint)
        paint.color=Color.DKGRAY; canvas.drawRect(120f,190f,680f,1010f,paint)
        paint.color=Color.WHITE; for (y in 200..980 step 40) canvas.drawRect(140f,y.toFloat(),660f,y+4f,paint)
    }
    private fun quad()=Quad(Pt(80.0,150.0),Pt(720.0,150.0),Pt(720.0,1050.0),Pt(80.0,1050.0))
    private fun save(name: String) {
        rule.waitForIdle()
        val view=rule.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun originalPhotoReviewPrecedesCroppingAndFourCornerDragsUsePhotoCoordinates() {
        val photo=photo()
        val wrong=Quad(Pt(200.0,300.0),Pt(600.0,300.0),Pt(600.0,900.0),Pt(200.0,900.0))
        var selection: Quad?=null
        rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.CARDNAVO)) {
            AdjustOutline(photo,wrong,{},fullscreen=true,applyLabel="Use this outline",onApply={selection=it})
        } }
        save("pregrade_original_outline")
        for ((from,to) in wrong.corners.zip(quad().corners)) {
            rule.onNodeWithTag("outline_photo_canvas").performTouchInput {
                val scale=minOf(width/800f,height/1200f)
                val ox=(width-800*scale)/2; val oy=(height-1200*scale)/2
                swipe(Offset(ox+from.x.toFloat()*scale,oy+from.y.toFloat()*scale),Offset(ox+to.x.toFloat()*scale,oy+to.y.toFloat()*scale),400)
            }
        }
        rule.onNodeWithText("Use this outline").performClick()
        selection!!.corners.zip(quad().corners).forEach { (a,b) ->
            assertEquals(b.x,a.x,1.0);assertEquals(b.y,a.y,1.0)
        }
        assertEquals(800,photo.width);assertEquals(1200,photo.height)
        save("pregrade_corrected_outline")
    }
    @Test fun confirmingEachSideAdvancesThroughAutomaticChecksToRevealWithoutDamageChecklists() {
        val photo=photo()
        val front=PreGrader.fromOutline(PreGrader.Outline(photo,quad()))
        assertTrue(front.problems.toString(),front.problems.isEmpty())
        assertNotNull(front.centering)
        rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.CARDNAVO)) {
            PreGradeFlow("Test card",{},initialFront=front,initialStep=Step.BACK,
                initialOutline=PreGrader.Outline(photo,quad()))
        } }
        rule.onNodeWithText("Card outline · four corners").assertIsDisplayed()
        rule.onNodeWithTag("pregrade_paper").assertDoesNotExist()
        rule.onNodeWithText("Use this outline").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithTag("pregrade_paper").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Centering · eight guides").assertDoesNotExist()
        rule.onNodeWithText("Surface · multiple lighting angles").assertDoesNotExist()
    }
    @Test fun failedDetectionOffersFourCornersAndFailedPhotoCanBeCorrectedWithoutInventingGrade() {
        val photo=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GRAY)}
        val draft=PreGrader.prepareOutline(photo)
        assertFalse(draft.detected)
        rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.CARDNAVO)) { PreGradeFlow(null,{},initialOutline=draft) } }
        rule.onNodeWithText("Automatic detection could not find the full card",substring=true).assertIsDisplayed()
        rule.onNodeWithText("Use this outline").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Adjust the four card corners").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Adjust the four card corners").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Next: back").assertIsNotEnabled()
        rule.onNodeWithTag("pregrade_revealed_grade").assertDoesNotExist()
    }
    @Test fun resultCanReopenEitherOriginalPhotoAndCancelKeepsMeasurements() {
        val photo=photo()
        val side=PreGrader.fromOutline(PreGrader.Outline(photo,quad())).copy(centering=Centering.Result(30.0,30.0,42.0,42.0))
        rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.CARDNAVO)) {
            PreGradeFlow("Test card",{},initialFront=side,initialBack=side,initialStep=Step.RESULT)
        } }
        for (label in listOf("Adjust front corners","Adjust back corners")) {
            rule.onNodeWithText(label).performScrollTo().performClick()
            rule.onNodeWithTag("outline_photo_canvas").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()
            rule.onNodeWithText("Why this score").performScrollTo().assertIsDisplayed()
            assertFalse(photo.isRecycled)
        }
    }
}
