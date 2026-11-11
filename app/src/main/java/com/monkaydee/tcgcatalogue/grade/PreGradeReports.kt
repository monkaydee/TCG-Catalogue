package com.monkaydee.tcgcatalogue.grade

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Serializable
data class CenteringRecord(val leftRight: Double, val topBottom: Double, val rotation: Double, val adjusted: Boolean)

@Serializable
data class PreGradeReport(
    val id: String, val createdAt: Long, val card: String, val game: String?,
    val grade: Int, val low: Int, val high: Int, val scope: AutomaticPreGrade.Scope,
    val reasons: List<AutomaticPreGrade.Reason>, val front: CenteringRecord, val back: CenteringRecord?,
    val method: String = "centering-heuristic-v1", val professional: Boolean = false,
    val surface: String = "not-assessed", val corners: String = "automatic-screening-not-used-in-score",
    val edges: String = "automatic-screening-not-used-in-score",
) {
    companion object {
        fun create(title: String, game: Game?, result: AutomaticPreGrade.Result, front: PreGrader.Side, back: PreGrader.Side?): PreGradeReport {
            fun record(side: PreGrader.Side) = side.centering!!.let {
                CenteringRecord(it.leftRight, it.topBottom, it.rotationDegrees, side.manualCentering)
            }
            return PreGradeReport(UUID.randomUUID().toString(), System.currentTimeMillis(), title, game?.name,
                result.grade, result.low, result.high, result.scope, result.reasons, record(front), back?.let(::record))
        }
    }
}

/**
 * The grade a card actually received from a grading company, typed in from the returned slab.
 * Together with the photos taken before grading it is a verified training sample; a pre-grade
 * estimate is never treated as one.
 */
@Serializable
data class VerifiedGrade(val grader: String, val grade: Double, val qualifier: String? = null, val cert: String? = null, val enteredAt: Long = System.currentTimeMillis()) {
    companion object {
        val GRADERS = listOf("PSA", "BGS", "CGC", "TAG")
        /** Null unless the entry is a real grade: a known grader, 1–10 in half steps, an optional numeric cert. */
        fun of(grader: String, grade: String, cert: String?): VerifiedGrade? {
            val g = grade.trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (grader !in GRADERS || g < 1 || g > 10 || (g * 2) % 1.0 != 0.0) return null
            val c = cert?.trim().orEmpty()
            if (c.isNotEmpty() && !(c.length in 5..12 && c.all(Char::isDigit))) return null
            return VerifiedGrade(grader, g, null, c.ifEmpty { null })
        }
    }
}

/** Saved photos are private durable copies, independent of the pre-grade cache and session. */
class PreGradeReportStore(context: Context) {
    private val directory = File(context.filesDir, "pregrade-reports")
    private val json = Json { ignoreUnknownKeys = true }
    private fun folder(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(directory, id)
    }
    private fun writeText(dir: File, name: String, text: String) {
        val file = AtomicFile(File(dir, name)); val out = file.startWrite()
        try { out.write(text.toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    private fun readText(id: String, name: String): String? =
        File(folder(id), name).takeIf { it.isFile }?.let { runCatching { AtomicFile(it).readFully().decodeToString() }.getOrNull() }

    /** The findings as measured when the report was saved (Evidence.kt). */
    fun evidence(id: String): EvidenceReport? = readText(id, EVIDENCE)?.let(Evidence::parse)

    fun verified(id: String): VerifiedGrade? = readText(id, VERIFIED)?.let { runCatching { json.decodeFromString<VerifiedGrade>(it) }.getOrNull() }

    /** Records (or with null removes) the grade the card received. Only for a saved report. */
    @Synchronized fun setVerified(id: String, grade: VerifiedGrade?) {
        check(contains(id))
        if (grade == null) File(folder(id), VERIFIED).delete() else writeText(folder(id), VERIFIED, json.encodeToString(grade))
    }

    /**
     * One training sample as a zip: the flattened photos, the report, the findings and the verified grade,
     * plus sample.json in the manifest format of scripts/pregrade (README.md there). Nothing leaves the
     * phone unless the user saves or shares this file. Requires a verified grade.
     */
    @Synchronized fun exportSample(id: String, out: java.io.OutputStream) {
        val report = all().first { it.id == id }
        val grade = checkNotNull(verified(id)) { "A training sample needs the grade the card received" }
        java.util.zip.ZipOutputStream(out).use { zip ->
            fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            val sides = listOf("front" to "front.png", "back" to "back.png").filter { File(folder(id), it.second).isFile }
            sides.forEach { (_, name) -> entry(name, File(folder(id), name).readBytes()) }
            readText(id, "report.json")?.let { entry("report.json", it.toByteArray()) }
            readText(id, EVIDENCE)?.let { entry(EVIDENCE, it.toByteArray()) }
            entry(VERIFIED, json.encodeToString(grade).toByteArray())
            val records = sides.map { (side, name) ->
                val bytes = File(folder(id), name).readBytes()
                SampleRecord(game = report.game?.lowercase(Locale.ROOT), physicalCardId = "app-${report.id}", cardName = report.card, side = side,
                    imagePath = name, imageSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                    certifiedGrader = grade.grader, verifiedGrade = grade.grade, gradeQualifier = grade.qualifier, cert = grade.cert,
                    estimatedGrade = report.grade.toDouble())
            }
            entry("sample.json", Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }.encodeToString(records).toByteArray())
        }
    }

    @Synchronized fun save(report: PreGradeReport, front: Bitmap, back: Bitmap?, evidence: EvidenceReport? = null) {
        val dir = folder(report.id).apply { mkdirs() }
        fun write(name: String, bitmap: Bitmap) {
            val file = AtomicFile(File(dir, name)); val out = file.startWrite()
            try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); file.finishWrite(out) }
            catch (e: Exception) { file.failWrite(out); throw e }
        }
        write("front.png", front)
        if (back != null) write("back.png", back)
        val file = AtomicFile(File(dir, "report.json")); val out = file.startWrite()
        try { out.write(json.encodeToString(report).toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
        if (evidence != null) writeText(dir, EVIDENCE, Evidence.json(evidence, pretty = false))
    }
    @Synchronized fun all(): List<PreGradeReport> = directory.listFiles().orEmpty().mapNotNull { dir ->
        runCatching { json.decodeFromString<PreGradeReport>(AtomicFile(File(dir, "report.json")).readFully().decodeToString()) }
            .getOrNull()?.takeIf { it.id == dir.name && runCatching { UUID.fromString(it.id).toString() == it.id }.getOrDefault(false) &&
                !it.professional && it.low in 1..it.grade && it.high in it.grade..10 }
    }.sortedByDescending { it.createdAt }
    fun contains(id: String): Boolean = File(folder(id), "report.json").isFile
    fun image(report: PreGradeReport, front: Boolean = true): File = File(folder(report.id), if (front) "front.png" else "back.png")

    private companion object { const val EVIDENCE = "evidence.json"; const val VERIFIED = "verified.json" }
}

/** One image of a training sample, in the manifest format of scripts/pregrade/pgdata.py. */
@Serializable
data class SampleRecord(
    val game: String?, @kotlinx.serialization.SerialName("physical_card_id") val physicalCardId: String,
    @kotlinx.serialization.SerialName("card_name") val cardName: String?, val side: String,
    val source: String = "own", val license: String = "contributed by the app's user",
    @kotlinx.serialization.SerialName("image_path") val imagePath: String,
    @kotlinx.serialization.SerialName("image_sha256") val imageSha256: String,
    @kotlinx.serialization.SerialName("certified_grader") val certifiedGrader: String,
    @kotlinx.serialization.SerialName("verified_grade") val verifiedGrade: Double,
    @kotlinx.serialization.SerialName("grade_qualifier") val gradeQualifier: String?,
    val cert: String?,
    /** What the app estimated before grading. Kept apart: an estimate is never ground truth. */
    @kotlinx.serialization.SerialName("estimated_grade") val estimatedGrade: Double?,
    @kotlinx.serialization.SerialName("label_provenance") val labelProvenance: String = "grade_entered_by_user_from_returned_slab",
    @kotlinx.serialization.SerialName("annotation_status") val annotationStatus: String = "pending_review",
)

object PreGradeCsv {
    fun encode(report: PreGradeReport): String {
        // Spreadsheet formula protection applies to user text, not numeric measurements.
        fun text(value: String?): String {
            var v = value.orEmpty()
            if (v.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || v.firstOrNull() in listOf('\t', '\r', '\n')) v = "'$v"
            return "\"${v.replace("\"", "\"\"")}\""
        }
        fun number(value: Double?) = value?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty()
        val header = "report_id,created_at_utc,card,game,grade,range_low,range_high,scope,method,professional,surface,corners,edges,reasons,front_left_percent,front_top_percent,front_rotation,front_guides_adjusted,back_left_percent,back_top_percent,back_rotation,back_guides_adjusted,disclaimer"
        val row = listOf(text(report.id), text(Instant.ofEpochMilli(report.createdAt).toString()), text(report.card), text(report.game),
            report.grade.toString(), report.low.toString(), report.high.toString(), text(report.scope.name), text(report.method), "false",
            text(report.surface), text(report.corners), text(report.edges), text(report.reasons.joinToString("; ") { it.name }),
            number(report.front.leftRight), number(report.front.topBottom), number(report.front.rotation), report.front.adjusted.toString(),
            number(report.back?.leftRight), number(report.back?.topBottom), number(report.back?.rotation), report.back?.adjusted?.toString().orEmpty(),
            text("Non-professional centering-only estimate. Actual professional grade may differ. Surface, corners, edges and authenticity do not determine this score."))
        return header + "\r\n" + row.joinToString(",") + "\r\n"
    }
}
