package com.monkaydee.tcgcatalogue

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monkaydee.tcgcatalogue.scan.PhotoIdentifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Recognition test on real photos ("golden set"): every photo in androidTest assets/golden with
 * the card it shows (cases.csv: file,cardId[,language][,grader][,grade]). Runs the same pipeline
 * as photo import and reports how many were right. Fails when the share of exact matches drops
 * below `minExact` (instrumentation argument, default 0.9 = 90% exact).
 *
 * The photos are not in this public repository; CI copies them in from a private source
 * (docs/GOLDEN_SET.md). Without them the test is skipped.
 */
@RunWith(AndroidJUnit4::class)
class GoldenSetTest {
    private data class Case(val file: String, val cardId: String, val language: String?, val grader: String?, val grade: String?)

    @Test
    fun recognisesTheGoldenSet() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val csv = runCatching { assets.open("golden/cases.csv").bufferedReader().readLines() }.getOrNull()
        assumeTrue("no golden set in assets/golden", csv != null)
        val cases = csv!!.drop(1).filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val c = line.split(',').map { it.trim() }
            Case(c[0], c[1], c.getOrNull(2)?.ifBlank { null }, c.getOrNull(3)?.ifBlank { null }, c.getOrNull(4)?.ifBlank { null })
        }
        val app = instrumentation.targetContext.applicationContext as TcgApp
        val identifier = PhotoIdentifier(app, app.repository)
        val dir = File(app.cacheDir, "golden").apply { mkdirs() }

        var exact = 0
        var inTop3 = 0
        var languageRight = 0
        var gradeRight = 0
        val report = StringBuilder("file | expected | got | rank | how\n")
        for (case in cases) {
            val file = File(dir, case.file).also { f -> assets.open("golden/${case.file}").use { input -> f.outputStream().use { input.copyTo(it) } } }
            val found = runCatching { identifier.identify(Uri.fromFile(file)) }.getOrDefault(emptyList()).firstOrNull()
            val ids = found?.candidates.orEmpty().map { it.cardId }
            val rank = ids.indexOf(case.cardId)
            if (rank == 0) exact++
            if (rank in 0..2) inTop3++
            val top = found?.candidates?.firstOrNull()
            if (case.language != null && top?.language == case.language) languageRight++
            if (case.grader != null && found?.grade?.grader == case.grader && found.grade.grade == case.grade) gradeRight++
            val how = when {
                found == null -> "nothing read"
                found.byPicture -> "picture"
                else -> "number ${found.hit?.key}"
            }
            report.append("${case.file} | ${case.cardId} | ${top?.cardId ?: "-"} | ${if (rank < 0) "miss" else rank + 1} | $how\n")
        }
        assertTrue("Golden set must contain labelled photos", cases.isNotEmpty())
        val n = cases.size
        report.append("\nexact ${exact}/${cases.size} (${100 * exact / n} %), top-3 ${inTop3}/${cases.size}")
        val withLanguage = cases.count { it.language != null }
        if (withLanguage > 0) report.append(", language ${languageRight}/$withLanguage")
        val withGrade = cases.count { it.grader != null }
        if (withGrade > 0) report.append(", slab grade ${gradeRight}/$withGrade")
        println(report)
        android.util.Log.i("GoldenSet", report.toString())
        File(instrumentation.targetContext.getExternalFilesDir(null), "golden-report.txt").writeText(report.toString())

        val minExact = InstrumentationRegistry.getArguments().getString("minExact")?.toDoubleOrNull() ?: 0.9
        assertTrue("minExact must be finite and in (0, 1]", minExact.isFinite() && minExact > 0.0 && minExact <= 1.0)
        assertTrue("Recognition below ${minExact * 100} %:\n$report", exact.toDouble() / n >= minExact)
    }
}
