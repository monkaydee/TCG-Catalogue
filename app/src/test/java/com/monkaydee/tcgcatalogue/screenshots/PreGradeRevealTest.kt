package com.monkaydee.tcgcatalogue.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.monkaydee.tcgcatalogue.data.Look
import com.monkaydee.tcgcatalogue.data.Palette
import com.monkaydee.tcgcatalogue.data.ThemeMode
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
class PreGradeRevealTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun photo() = Bitmap.createBitmap(600,840,Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); canvas.drawColor(Color.rgb(235,196,71))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(21,87,109); canvas.drawRect(25f,25f,575f,815f,paint)
        paint.color = Color.rgb(106,226,212); canvas.drawCircle(300f,310f,175f,paint)
        paint.color = Color.WHITE; paint.textSize = 38f; canvas.drawText("TEST CARD",48f,72f,paint)
        paint.textSize = 27f; canvas.drawText("Actual captured photo",48f,640f,paint)
    }
    private fun result(grade:Int) = AutomaticPreGrade.Result(grade,(grade-1).coerceAtLeast(1),(grade+1).coerceAtMost(10),
        AutomaticPreGrade.Scope.CENTERING_ONLY,listOf(AutomaticPreGrade.Reason.CENTERED,AutomaticPreGrade.Reason.SURFACE_UNAVAILABLE))
    private fun save(name:String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun gradeStaysHiddenUntilRightSwipeAndNiceOpensResult() {
        var continued = false
        rule.mainClock.autoAdvance = false
        rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.FOREST)) {
            PreGradeReveal(photo(),"Flareon",result(9),{ continued = true })
        } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("pregrade_revealed_grade").assertDoesNotExist()
        rule.onNodeWithTag("pregrade_reveal_continue").assertDoesNotExist()
        rule.onNodeWithText("pull").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithTag("pregrade_paper").assertIsDisplayed()
        rule.onNodeWithText("pull").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(2400)
        rule.onNodeWithText("pull").assertIsDisplayed()
        save("pregrade_paper_pull")
        // A swipe toward the wrong direction must not reveal the grade.
        rule.onNodeWithTag("pregrade_paper").performTouchInput { swipe(center,Offset(0f,center.y),200) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("pregrade_revealed_grade").assertDoesNotExist()
        rule.onNodeWithTag("pregrade_paper").performTouchInput { swipe(center,Offset(width*2f,center.y),300) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("pregrade_revealed_grade").assertTextEquals("9")
        rule.onNodeWithText("Nice!").assertIsDisplayed().performClick()
        assertTrue(continued)
        rule.onNodeWithText("pull").assertDoesNotExist()
        rule.onNodeWithTag("pregrade_paper").assertDoesNotExist()
        save("pregrade_revealed_nice")
    }
    @Test fun lowerGradeHasMeehAndAccessiblePaperAction() {
        var continued = false
        rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.INDIGO)) {
            PreGradeReveal(photo(),"Dark Charizard",result(8),{ continued = true },playAnimation=false)
        } }
        rule.onNodeWithTag("pregrade_paper").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithTag("pregrade_revealed_grade").assertTextEquals("8")
        rule.onNodeWithText("meeh!").assertIsDisplayed().performClick()
        rule.onNodeWithText("Nice!").assertDoesNotExist()
        assertTrue(continued)
        save("pregrade_revealed_meeh")
    }
    @Test @Config(qualifiers = "de-rDE-w320dp-h640dp-xhdpi")
    fun germanResultActionsStayVisibleWithLargeText() {
        val side = PreGrader.Side(photo(),emptyList(),Centering.Result(30.0,30.0,42.0,42.0),Wear.Result(emptyMap()))
        val report = PreGradeReport.create("Dark Charizard",null,result(8),side,side)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                TcgTheme(Look(ThemeMode.DARK,Palette.CARDNAVO)) {
                    PreGradeReportPage(report,side.card,side.card)
                }
            }
        }
        rule.onNodeWithText("CSV exportieren").assertIsDisplayed()
        rule.onNodeWithText("Speichern").assertIsDisplayed()
        save("pregrade_german_large_text")
    }
    @Test fun resultSavesOnceAndHistoryReopensSameReportWithExport() {
        val store = PreGradeReportStore(rule.activity)
        val side = PreGrader.Side(photo(),emptyList(),Centering.Result(30.0,30.0,42.0,42.0),Wear.Result(emptyMap()))
        val report = PreGradeReport.create("Flareon",null,result(9),side,side)
        var history by mutableStateOf(false)
        try {
            rule.setContent { TcgTheme(Look(ThemeMode.DARK,Palette.FOREST)) { Surface(Modifier.fillMaxSize()) {
                if (history) SavedPreGrades() else PreGradeReportPage(report,side.card,side.card)
            } } }
            save("pregrade_clean_result")
            rule.onNodeWithText("Export CSV").assertIsDisplayed()
            rule.onNodeWithText("Save").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { store.all().any { it.id == report.id } }
            rule.onNodeWithText("Saved").assertIsNotEnabled()
            assertEquals(1,store.all().count { it.id == report.id })
            assertTrue(store.image(report).exists())
            rule.runOnUiThread { history = true }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Flareon").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Flareon").performClick()
            rule.onNodeWithText("Why this score").assertIsDisplayed()
            rule.onNodeWithText("Export CSV").assertIsDisplayed()
            rule.onNodeWithText("Saved").assertIsNotEnabled()
        } finally { File(rule.activity.filesDir,"pregrade-reports").deleteRecursively() }
    }
}
