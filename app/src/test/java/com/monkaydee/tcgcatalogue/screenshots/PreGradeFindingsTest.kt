package com.monkaydee.tcgcatalogue.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.grade.AutomaticPreGrade
import com.monkaydee.tcgcatalogue.grade.Centering
import com.monkaydee.tcgcatalogue.grade.Evidence
import com.monkaydee.tcgcatalogue.grade.PreGradeReport
import com.monkaydee.tcgcatalogue.grade.PreGradeReportStore
import com.monkaydee.tcgcatalogue.grade.PreGrader
import com.monkaydee.tcgcatalogue.grade.Pt
import com.monkaydee.tcgcatalogue.grade.Wear
import com.monkaydee.tcgcatalogue.ui.screens.FindingsSection
import com.monkaydee.tcgcatalogue.ui.screens.VerifiedGradeSection
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class PreGradeFindingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun card() = Bitmap.createBitmap(358, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 60, 140)) }
    private fun side(zones: Map<String, Wear.Zone> = emptyMap(), findings: Map<String, Wear.Finding> = emptyMap()) =
        PreGrader.Side(card(), emptyList(), Centering.Result(54.0, 46.0, 51.0, 49.0),
            Wear.Result((Wear.CORNERS + Wear.EDGES).associateWith { zones[it] ?: Wear.Zone(0.0, 0.0, 0.0) }), outlineConfirmed = true, wearFindings = findings)

    private fun save(name: String) {
        rule.waitForIdle(); val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun findingsShowAllThreeStatesWithMarkersOnTheCard() {
        val front = side(
            zones = mapOf("TR" to Wear.Zone(0.04, 0.03, 2.0, flaggedPoints = listOf(Pt(330.0, 6.0), Pt(345.0, 10.0), Pt(350.0, 22.0), Pt(338.0, 16.0))),
                "L" to Wear.Zone(0.0, 0.0, 0.0, Wear.Evidence.LOW_CONTRAST)),
            findings = mapOf("B" to Wear.Finding.CHIP_OR_TEAR))
        val back = side()
        val evidence = Evidence.build("Test card", Game.POKEMON, front, back, AutomaticPreGrade.assess(front, back))
        rule.setContent { TcgTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FindingsSection(evidence, front.card, back.card) } } }
        save("pregrade_findings")
        rule.onNodeWithText("Findings").assertIsDisplayed()
        rule.onNodeWithText("Bottom edge · Confirmed").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Top-right corner · Possible").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Left edge · Not assessable").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Whole surface · Not assessable").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Confirmed 1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("pre_findings_markers").performScrollTo().performClick()   // markers can be hidden
        rule.onNodeWithText("Back").performScrollTo().performClick()
        rule.onNodeWithText("Confirmed 0").performScrollTo().assertIsDisplayed()
    }

    @Test fun receivedGradeIsValidatedSavedAndRemovable() {
        val store = PreGradeReportStore(rule.activity)
        val s = side(); val report = PreGradeReport.create("Test card", Game.POKEMON, AutomaticPreGrade.assess(s, s)!!, s, s)
        store.save(report, s.card, s.card)
        rule.setContent { TcgTheme { Column(Modifier.verticalScroll(rememberScrollState())) { VerifiedGradeSection(store, report.id) } } }
        rule.onNodeWithTag("pre_verified_grade").performTextInput("9.3")
        rule.onNodeWithTag("pre_verified_save").performScrollTo().performClick()
        assertNull(store.verified(report.id))                       // 9.3 is not a grade
        rule.onNodeWithText("Enter a grade from 1 to 10 in half steps and a numeric certificate number.").assertIsDisplayed()
        rule.onNodeWithTag("pre_verified_grade").performTextInput("")
        rule.runOnUiThread { }
        store.setVerified(report.id, com.monkaydee.tcgcatalogue.grade.VerifiedGrade.of("PSA", "9", "12345678"))
        assertEquals(9.0, store.verified(report.id)!!.grade, 0.0)
        File(rule.activity.filesDir, "pregrade-reports").deleteRecursively()
    }
}
