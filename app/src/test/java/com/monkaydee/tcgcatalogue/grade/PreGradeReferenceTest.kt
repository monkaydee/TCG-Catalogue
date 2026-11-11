package com.monkaydee.tcgcatalogue.grade

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Private/research photos live outside the repo. Set PREGRADING_FIXTURE_DIR to reproduce. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class PreGradeReferenceTest {
    private fun source(name: String): Bitmap {
        val path = System.getenv("PREGRADING_FIXTURE_DIR")
        assumeNotNull(path)
        val file = File(path!!, name)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun record(name: String, outcome: PreGrader.Outcome) {
        val text = when (outcome) {
            PreGrader.Outcome.NoCard -> "$name: no whole card"
            is PreGrader.Outcome.Ok -> with(outcome.side) {
                "$name: problems=$problems; centering=${centering?.worst}; quad=$quad; wear=${wear.zones}"
            }
        }
        println(text)
        File("build/pregrade-reference-results").apply { mkdirs() }.resolve("$name.txt").writeText(text)
    }

    @Test fun measuresOwnFullFrontAndBackPhotos() {
        for (name in listOf("drive-front.jpg", "drive-back.jpg")) {
            val bitmap = source(name)
            val outcome = PreGrader.analyse(bitmap)
            record(name, outcome)
            assertTrue("$name must find the whole card", outcome is PreGrader.Outcome.Ok)
            val side = (outcome as PreGrader.Outcome.Ok).side
            assertEquals(CardRectifier.W, side.card.width)
            assertTrue(side.wear.zones.values.all { it.defects.isFinite() && it.defects in 0.0..1.0 })
        }
    }

    @Test fun doesNotGradeCornerCloseups() {
        val outcome = PreGrader.analyse(source("drive-corner.jpg"))
        record("drive-corner.jpg", outcome)
        assertTrue("A corner closeup must not yield a usable grading input",
            outcome is PreGrader.Outcome.NoCard || outcome is PreGrader.Outcome.Ok && !outcome.side.usableForGrade)
    }

    @Test fun onlineFrontBackImagesAreMeasurementFixturesNotKnownGrades() {
        for (name in listOf("pokemon-front.png", "pokemon-back.jpg", "onepiece-zoro-front.png", "onepiece-leader-pair.jpg")) {
            val card = source(name)
            // Publisher illustrations have no surrounding mat. Add one to exercise outline search.
            val photo = Bitmap.createBitmap(card.width + 100, card.height + 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(photo)
            canvas.drawColor(Color.rgb(30, 30, 30))
            canvas.drawBitmap(card, 50f, 50f, Paint())
            val outcome = PreGrader.analyse(photo)
            record(name, outcome)
            if (name == "onepiece-leader-pair.jpg") assertTrue("A pair must not yield a usable grading input",
                outcome is PreGrader.Outcome.NoCard || outcome is PreGrader.Outcome.Ok && !outcome.side.usableForGrade)
            if (outcome is PreGrader.Outcome.Ok) {
                assertTrue(outcome.side.wear.zones.values.all { it.defects.isFinite() })
                // A low-resolution source must not become a grading-quality photo by upscaling.
                if (card.width < 560 && name != "onepiece-leader-pair.jpg") {
                    assertTrue(outcome.side.problems.contains(PhotoCheck.Problem.TOO_SMALL))
                }
            }
        }
    }
}
