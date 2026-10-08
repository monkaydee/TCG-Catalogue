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

/** Saved photos are private durable copies, independent of the pre-grade cache and session. */
class PreGradeReportStore(context: Context) {
    private val directory = File(context.filesDir, "pregrade-reports")
    private val json = Json { ignoreUnknownKeys = true }
    private fun folder(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(directory, id)
    }
    @Synchronized fun save(report: PreGradeReport, front: Bitmap, back: Bitmap?) {
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
    }
    @Synchronized fun all(): List<PreGradeReport> = directory.listFiles().orEmpty().mapNotNull { dir ->
        runCatching { json.decodeFromString<PreGradeReport>(AtomicFile(File(dir, "report.json")).readFully().decodeToString()) }
            .getOrNull()?.takeIf { it.id == dir.name && runCatching { UUID.fromString(it.id).toString() == it.id }.getOrDefault(false) &&
                !it.professional && it.low in 1..it.grade && it.high in it.grade..10 }
    }.sortedByDescending { it.createdAt }
    fun contains(id: String): Boolean = File(folder(id), "report.json").isFile
    fun image(report: PreGradeReport, front: Boolean = true): File = File(folder(report.id), if (front) "front.png" else "back.png")
}

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
