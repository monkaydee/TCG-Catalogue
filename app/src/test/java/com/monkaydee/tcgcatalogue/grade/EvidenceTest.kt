package com.monkaydee.tcgcatalogue.grade

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EvidenceTest {
    private val all = Wear.CORNERS + Wear.EDGES
    private fun bitmap() = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888)
    private fun side(zones: Map<String, Wear.Zone> = emptyMap(), findings: Map<String, Wear.Finding> = emptyMap(),
                     surface: SurfaceFinding = SurfaceFinding.NOT_REVIEWED, problems: List<PhotoCheck.Problem> = emptyList()) =
        PreGrader.Side(bitmap(), problems, Centering.Result(54.0, 46.0, 51.0, 49.0),
            Wear.Result(all.associateWith { zones[it] ?: Wear.Zone(0.0, 0.0, 0.0) }), outlineConfirmed = true, wearFindings = findings, surfaceFinding = surface)
    private fun build(front: PreGrader.Side?, back: PreGrader.Side? = null, game: Game? = Game.POKEMON) =
        Evidence.build("Test card", game, front, back, AutomaticPreGrade.assess(front, back))

    @Test fun cleanMeasurementsReportNoDefectButSurfaceStaysUnassessed() {
        val r = build(side(), side())
        assertTrue(r.defects.all { it.type == "surface" && it.findingStatus == FindingStatus.NOT_ASSESSABLE })
        assertEquals(2, r.defects.size)
        assertEquals("no_anomaly_flagged", r.componentAssessments.corners)
        assertEquals("not_fully_assessable", r.componentAssessments.surface)
        assertTrue("tilted_front_surface" in r.qualityChecks.additionalImagesRequired)
        assertTrue(r.explanation.any { "not proof" in it })
        assertEquals("54/46", r.centering.frontHorizontal)
        assertEquals("not_yet_validated", r.preGrade.calibrationStatus)
    }

    @Test fun automaticAnomalyIsOnlyPossibleAndCarriesItsMeasuredBox() {
        val points = listOf(Pt(90.0, 2.0), Pt(95.0, 3.0), Pt(97.0, 6.0), Pt(92.0, 5.0))
        val r = build(side(zones = mapOf("TR" to Wear.Zone(0.04, 0.03, 2.0, flaggedPoints = points))))
        val d = r.defects.single { it.location == "top_right" }
        assertEquals(FindingStatus.POSSIBLE, d.findingStatus)
        assertEquals("corner_whitening", d.type)
        assertNull(d.confidence); assertNull(d.severity)
        val b = d.boundingBox!!
        assertTrue(b.x in 0.85..0.92 && b.x + b.width <= 1.0 && b.y < 0.05 && b.height < 0.1)
        assertEquals("possible_damage", r.componentAssessments.corners)
        assertTrue("4.0%" in d.evidence)
    }

    @Test fun recordedDamageIsConfirmedAndWinsOverTheAutomaticCheck() {
        val r = build(side(findings = mapOf("B" to Wear.Finding.CHIP_OR_TEAR, "TL" to Wear.Finding.WHITENING), surface = SurfaceFinding.SCRATCHES))
        assertEquals(setOf("chip_or_tear", "corner_whitening", "surface_scratches"), r.defects.filter { it.findingStatus == FindingStatus.CONFIRMED }.map { it.type }.toSet())
        assertEquals("damage_confirmed", r.componentAssessments.edges)
        assertEquals("damage_confirmed", r.componentAssessments.corners)
        assertEquals("damage_confirmed", r.componentAssessments.surface)
        assertEquals(listOf("defect_001", "defect_002", "defect_003"), r.defects.map { it.id })
    }

    @Test fun whiteBordersFoilAndBadPhotosAreNotAssessableNeverClean() {
        val r = build(side(zones = mapOf("L" to Wear.Zone(0.0, 0.0, 0.0, Wear.Evidence.LOW_CONTRAST), "T" to Wear.Zone(0.5, 0.5, 9.0, Wear.Evidence.TEXTURED))))
        assertEquals(FindingStatus.NOT_ASSESSABLE, r.defects.single { it.location == "left" }.findingStatus)
        assertEquals(FindingStatus.NOT_ASSESSABLE, r.defects.single { it.location == "top" }.findingStatus)
        assertEquals("not_fully_assessable", r.componentAssessments.edges)
        val blurry = build(side(problems = listOf(PhotoCheck.Problem.BLURRY)))
        assertEquals("needs_better_images", blurry.analysisStatus)
        assertFalse(blurry.qualityChecks.frontUsable)
        assertNull(blurry.preGrade.estimatedGrade)
        assertTrue(blurry.defects.filter { it.type != "surface" }.all { it.findingStatus == FindingStatus.NOT_ASSESSABLE })
        assertTrue("retake_front" in blurry.qualityChecks.additionalImagesRequired)
    }

    @Test fun onlyPokemonAndOnePieceGetAScoreAndOnePieceIsMarkedProvisional() {
        val magic = build(side(), side(), Game.MAGIC)
        assertEquals("unsupported_game", magic.analysisStatus); assertNull(magic.preGrade.estimatedGrade); assertNull(magic.preGrade.estimatedRange)
        val onePiece = build(side(), side(), Game.ONE_PIECE)
        assertNotNull(onePiece.preGrade.estimatedGrade)
        assertTrue(onePiece.preGrade.limitations.any { "One Piece" in it })
    }

    @Test fun jsonKeepsExplicitNullsAndRoundTrips() {
        val r = build(side())
        val json = Json.parseToJsonElement(Evidence.json(r)).jsonObject
        assertEquals(JsonNull, json.getValue("centering").jsonObject.getValue("back_horizontal"))
        val defect = json.getValue("defects").jsonArray.first().jsonObject
        assertEquals(JsonNull, defect.getValue("confidence"))
        assertEquals("not_assessable", defect.getValue("finding_status").jsonPrimitive.content)
        assertEquals("PSA-style", json.getValue("pre_grade").jsonObject.getValue("standard").jsonPrimitive.content)
        assertEquals(r, Evidence.parse(Evidence.json(r, pretty = false)))
    }

    @Test fun verifiedGradeAcceptsOnlyRealGrades() {
        assertEquals(9.5, VerifiedGrade.of("BGS", "9,5", "0012345678")!!.grade, 0.0)
        assertNull(VerifiedGrade.of("PSA", "11", null)); assertNull(VerifiedGrade.of("PSA", "9.3", null))
        assertNull(VerifiedGrade.of("ACME", "9", null)); assertNull(VerifiedGrade.of("PSA", "9", "12ab"))
        assertNull(VerifiedGrade.of("PSA", "", null))
    }

    @Test fun savedReportKeepsFindingsAndExportsAVerifiedTrainingSample() {
        val store = PreGradeReportStore(ApplicationProvider.getApplicationContext())
        val front = side(); val back = side()
        val result = AutomaticPreGrade.assess(front, back)!!
        val report = PreGradeReport.create("Test card", Game.POKEMON, result, front, back)
        val evidence = Evidence.build("Test card", Game.POKEMON, front, back, result)
        store.save(report, front.card, back.card, evidence)
        assertEquals(evidence, store.evidence(report.id))
        assertNull(store.verified(report.id))
        // an estimate alone is never a training sample
        assertTrue(runCatching { store.exportSample(report.id, ByteArrayOutputStream()) }.isFailure)
        store.setVerified(report.id, VerifiedGrade.of("PSA", "9", "12345678"))
        val out = ByteArrayOutputStream(); store.exportSample(report.id, out)
        val entries = HashMap<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { zip -> generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() } }
        assertEquals(setOf("front.png", "back.png", "report.json", "evidence.json", "verified.json", "sample.json"), entries.keys)
        val records = Json.parseToJsonElement(entries.getValue("sample.json")).jsonArray
        assertEquals(2, records.size)
        val first = records.first().jsonObject
        assertEquals(9.0, first.getValue("verified_grade").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("PSA", first.getValue("certified_grader").jsonPrimitive.content)
        assertEquals("grade_entered_by_user_from_returned_slab", first.getValue("label_provenance").jsonPrimitive.content)
        assertEquals(64, first.getValue("image_sha256").jsonPrimitive.content.length)
        store.setVerified(report.id, null); assertNull(store.verified(report.id))
    }
}
