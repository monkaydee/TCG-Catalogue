package com.monkaydee.tcgcatalogue.grade

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class AutomaticPreGradeTest {
    private fun side(left: Double = 30.0, right: Double = 30.0) = PreGrader.Side(
        Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) },
        emptyList(), Centering.Result(left, right, 42.0, 42.0), Wear.Result(emptyMap()))

    @Test fun automaticFallbackNeedsNoManualReviewAndIgnoresUnassessedDamageInputs() {
        val s = side()
        val result = AutomaticPreGrade.assess(s, s)!!
        assertEquals(10, result.grade)
        assertEquals(AutomaticPreGrade.Scope.CENTERING_ONLY, result.scope)
        assertTrue(AutomaticPreGrade.Reason.SURFACE_UNAVAILABLE in result.reasons)
        assertEquals(result, AutomaticPreGrade.assess(s.copy(surfaceFinding = SurfaceFinding.DENT_OR_CREASE,
            wearFindings = Wear.CORNERS.associateWith { Wear.Finding.CHIP_OR_TEAR }), s))
    }
    @Test fun poorOrMissingMeasurementsAbstainAndOmittedBackIsExplicit() {
        val s = side()
        assertNull(AutomaticPreGrade.assess(null,s))
        assertNull(AutomaticPreGrade.assess(s.copy(centering = null),s))
        assertNull(AutomaticPreGrade.assess(s,s.copy(problems = listOf(PhotoCheck.Problem.GLARE))))
        assertNull(AutomaticPreGrade.assess(s,s.copy(centering = null)))
        assertNull(AutomaticPreGrade.assess(s.copy(centering = Centering.Result(Double.NaN,1.0,1.0,1.0)),s))
        val frontOnly = AutomaticPreGrade.assess(s,null)!!
        assertEquals(9,frontOnly.grade)
        assertEquals(AutomaticPreGrade.Scope.FRONT_CENTERING_ONLY, frontOnly.scope)
        assertTrue(AutomaticPreGrade.Reason.BACK_MISSING in frontOnly.reasons)
    }
    @Test fun worseningEitherSideNeverImprovesScoreAndLowerGradeThresholdsAreConservative() {
        for (worst in 50..99) {
            assertTrue(AutomaticPreGrade.score(worst.toDouble(),50.0) >= AutomaticPreGrade.score(worst + 1.0,50.0))
            assertTrue(AutomaticPreGrade.score(50.0,worst.toDouble()) >= AutomaticPreGrade.score(50.0,worst + 1.0))
        }
        assertEquals(10,AutomaticPreGrade.score(55.0,75.0))
        assertEquals(9,AutomaticPreGrade.score(58.6,54.8))
        assertEquals(8,AutomaticPreGrade.score(62.0,50.0))
        assertEquals(1,AutomaticPreGrade.score(100.0,50.0))
    }
    @Test fun savedReportHasDurablePhotosAndReloadsAfterCacheRemoval() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val s = side(); val r = PreGradeReport.create("Misty’s Psyduck",null,AutomaticPreGrade.assess(s,s)!!,s,s)
        val store = PreGradeReportStore(context)
        try {
            store.save(r,s.card,s.card); store.save(r,s.card,s.card)
            context.cacheDir.deleteRecursively()
            val reloaded = PreGradeReportStore(context)
            assertEquals(listOf(r),reloaded.all())
            assertTrue(reloaded.image(r).length() > 0)
            assertTrue(reloaded.image(r,false).length() > 0)
            val bitmap = android.graphics.BitmapFactory.decodeFile(reloaded.image(r).absolutePath)
            assertEquals(android.graphics.Color.CYAN,bitmap.getPixel(50,70))
            File(context.filesDir,"pregrade-reports/bad/report.json").apply { parentFile!!.mkdirs(); writeText("{}") }
            assertEquals(listOf(r),reloaded.all())
        } finally { File(context.filesDir,"pregrade-reports").deleteRecursively() }
    }
    @Test fun csvEscapesUserTextAndUsesStableNumericFormatAndExplicitScope() {
        val s = side(); val r = PreGradeReport.create("=HYPERLINK(\"evil\"), 日本語\ncard",null,AutomaticPreGrade.assess(s,null)!!,s,null)
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val csv = PreGradeCsv.encode(r)
            assertTrue(csv.contains("\"'=HYPERLINK(\"\"evil\"\"), 日本語\ncard\""))
            assertTrue(csv.contains(",50.000,50.000,0.000,false,"))
            assertTrue(csv.contains("FRONT_CENTERING_ONLY"))
            assertTrue(csv.contains("not-assessed"))
            assertTrue(csv.contains("Actual professional grade may differ"))
            assertTrue(csv.endsWith("\r\n"))
        } finally { Locale.setDefault(original) }
    }
}
