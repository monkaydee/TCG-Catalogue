package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * The machine-readable pre-grade report (docs/PREGRADING-SYSTEM.md, "Report format").
 *
 * Every entry comes from a measurement of the supplied photos or from an observation the user recorded.
 * Nothing is filled in: what was not measured is null, and what the photos cannot show is listed as
 * [FindingStatus.NOT_ASSESSABLE] instead of being passed off as clean.
 */
@Serializable
enum class FindingStatus {
    /** The user looked at the region and recorded the damage. */
    @SerialName("confirmed") CONFIRMED,
    /** An automatic check flagged it; it may also be print, foil, glare or a sleeve. Needs a look or another photo. */
    @SerialName("possible") POSSIBLE,
    /** These photos cannot show it either way. */
    @SerialName("not_assessable") NOT_ASSESSABLE,
}

/** A region of the flattened card, as fractions of its width and height (0–1 from the top-left corner). */
@Serializable
data class EvidenceBox(val x: Double, val y: Double, val width: Double, val height: Double)

@Serializable
data class DefectFinding(
    val id: String,
    /** corner_whitening, edge_whitening, corner_wear, edge_wear, chip_or_tear, bend_or_dent, corner_shape, surface_scratches, surface_dent_or_crease, surface. */
    val type: String,
    /** front or back. */
    val side: String,
    /** top_left … bottom_left, top, right, bottom, left, or whole_surface. */
    val location: String,
    /** Only set where a severity scale has been calibrated; none is yet for phone photos. */
    val severity: String? = null,
    @SerialName("finding_status") val findingStatus: FindingStatus,
    /** A calibrated probability. Null: no detector with measured calibration exists yet. */
    val confidence: Double? = null,
    @SerialName("bounding_box") val boundingBox: EvidenceBox? = null,
    /** What the finding rests on, in plain words with the measured numbers. */
    val evidence: String,
)

@Serializable
data class EvidenceCard(val name: String?, val set: String? = null, val number: String? = null)

@Serializable
data class QualityChecks(
    @SerialName("front_usable") val frontUsable: Boolean,
    @SerialName("back_usable") val backUsable: Boolean?,
    @SerialName("problems") val problems: List<String>,
    @SerialName("additional_images_required") val additionalImagesRequired: List<String>,
)

@Serializable
data class CenteringEvidence(
    @SerialName("front_horizontal") val frontHorizontal: String?,
    @SerialName("front_vertical") val frontVertical: String?,
    @SerialName("back_horizontal") val backHorizontal: String?,
    @SerialName("back_vertical") val backVertical: String?,
)

@Serializable
data class ComponentAssessments(val centering: String?, val corners: String?, val edges: String?, val surface: String?)

@Serializable
data class PreGradeEvidence(
    val standard: String = "PSA-style",
    @SerialName("estimated_grade") val estimatedGrade: Int?,
    @SerialName("estimated_range") val estimatedRange: List<Int>?,
    @SerialName("calibration_status") val calibrationStatus: String,
    val limitations: List<String>,
)

@Serializable
data class EvidenceReport(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val game: String?,
    val card: EvidenceCard,
    @SerialName("analysis_status") val analysisStatus: String,
    @SerialName("quality_checks") val qualityChecks: QualityChecks,
    val centering: CenteringEvidence,
    val defects: List<DefectFinding>,
    @SerialName("component_assessments") val componentAssessments: ComponentAssessments,
    @SerialName("pre_grade") val preGrade: PreGradeEvidence,
    @SerialName("method") val method: String = Evidence.METHOD,
    /** The same content as sentences, for people. */
    val explanation: List<String>,
)

object Evidence {
    /** Names the measuring code and rules that produced a report, so later reports stay comparable. */
    const val METHOD = "measurements-v2 (centering, rim colour comparison, corner contour); no learned defect detector"

    /** Pre-grading is offered for these games only. */
    val GAMES = listOf(Game.POKEMON, Game.ONE_PIECE)

    private val format = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }
    private val compact = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true }

    fun json(report: EvidenceReport, pretty: Boolean = true): String = (if (pretty) format else compact).encodeToString(report)
    fun parse(text: String): EvidenceReport? = runCatching { compact.decodeFromString<EvidenceReport>(text) }.getOrNull()

    private val LOCATION = mapOf("TL" to "top_left", "TR" to "top_right", "BR" to "bottom_right", "BL" to "bottom_left",
        "T" to "top", "R" to "right", "B" to "bottom", "L" to "left")

    /** "54/46": the wider border first is NOT assumed; left (or top) share first, as measured. */
    fun ratio(percent: Double?): String? = percent?.takeIf { it.isFinite() }?.let {
        String.format(Locale.ROOT, "%.0f/%.0f", it, 100 - it)
    }

    private fun box(name: String, side: PreGrader.Side): EvidenceBox {
        val w = side.card.width; val h = side.card.height
        val zone = side.wear.zones[name]
        val points = zone?.flaggedPoints.orEmpty()
        val (x0, x1, y0, y1) = if (points.size >= 4) {
            // the tight box around the flagged pixels, with a small margin so it stays visible
            val m = w * 0.01
            listOf((points.minOf { it.x } - m).toInt(), (points.maxOf { it.x } + m).toInt() + 1,
                (points.minOf { it.y } - m).toInt(), (points.maxOf { it.y } + m).toInt() + 1)
        } else Wear.area(name, w, h)
        fun fx(v: Int) = (v.toDouble() / w).coerceIn(0.0, 1.0)
        fun fy(v: Int) = (v.toDouble() / h).coerceIn(0.0, 1.0)
        return EvidenceBox(fx(x0), fy(y0), (fx(x1) - fx(x0)).coerceAtLeast(0.0), (fy(y1) - fy(y0)).coerceAtLeast(0.0))
    }

    private fun percent(share: Double) = String.format(Locale.ROOT, "%.1f%%", share * 100)

    private fun sideFindings(label: String, side: PreGrader.Side): List<DefectFinding> = buildList {
        fun add(type: String, location: String, status: FindingStatus, box: EvidenceBox?, evidence: String) {
            add(DefectFinding("", type, label, location, null, status, null, box, evidence))
        }
        for (name in Wear.CORNERS + Wear.EDGES) {
            val corner = name.length == 2
            val part = if (corner) "corner" else "edge"
            val location = LOCATION.getValue(name)
            val zone = side.wear.zones[name]
            when (side.wearFindings[name]) {
                Wear.Finding.WHITENING -> { add("${part}_whitening", location, FindingStatus.CONFIRMED, box(name, side), "Recorded by the user while inspecting this region."); continue }
                Wear.Finding.CHIP_OR_TEAR -> { add("chip_or_tear", location, FindingStatus.CONFIRMED, box(name, side), "Recorded by the user while inspecting this region."); continue }
                Wear.Finding.BEND_OR_DENT -> { add("bend_or_dent", location, FindingStatus.CONFIRMED, box(name, side), "Recorded by the user while inspecting this region."); continue }
                else -> Unit
            }
            val reviewedClear = side.wearFindings[name] == Wear.Finding.NO_VISIBLE_DAMAGE
            when {
                !side.usableForWear || zone == null || zone.evidence == Wear.Evidence.INSUFFICIENT ->
                    add("${part}_wear", location, FindingStatus.NOT_ASSESSABLE, null, "The photo gives no usable measurement here (outline not confirmed, photo problem, or too few pixels).")
                zone.evidence == Wear.Evidence.LOW_CONTRAST ->
                    add("${part}_wear", location, FindingStatus.NOT_ASSESSABLE, null, "The border is white here: whitening cannot be told from the border by colour.")
                zone.evidence == Wear.Evidence.TEXTURED ->
                    add("${part}_wear", location, FindingStatus.NOT_ASSESSABLE, null, "Artwork or foil reaches the rim here: the colour comparison is not reliable.")
                Wear.possibleDamage(zone) -> {
                    val whiter = zone.whitening >= 0.001
                    add(if (whiter) "${part}_whitening" else "${part}_wear", location, FindingStatus.POSSIBLE, box(name, side),
                        "${percent(zone.defects)} of the rim pixels differ from the border colour, ${percent(zone.whitening)} are brighter." +
                            if (reviewedClear) " The user saw no damage here; the two disagree." else "")
                }
            }
            if (corner && side.contours[name] == ContourCheck.Evidence.ASYMMETRIC)
                add("corner_shape", location, FindingStatus.POSSIBLE, box(name, side), "This corner's outline differs from the other corners (possible rounding, bend or missing material).")
        }
        when (side.surfaceFinding) {
            SurfaceFinding.SCRATCHES -> add("surface_scratches", "whole_surface", FindingStatus.CONFIRMED, null, "Recorded by the user from the angled-light photos.")
            SurfaceFinding.DENT_OR_CREASE -> add("surface_dent_or_crease", "whole_surface", FindingStatus.CONFIRMED, null, "Recorded by the user from the angled-light photos.")
            SurfaceFinding.CLEAR_IN_PHOTOS -> Unit
            SurfaceFinding.NOT_REVIEWED -> add("surface", "whole_surface", FindingStatus.NOT_ASSESSABLE, null,
                "A flat photo does not show scratches, dents or print lines. No angled-light photos were reviewed.")
        }
    }

    fun build(title: String?, game: Game?, front: PreGrader.Side?, back: PreGrader.Side?, result: AutomaticPreGrade.Result?): EvidenceReport {
        val supported = game == null || game in GAMES
        val sides = listOfNotNull(front?.let { "front" to it }, back?.let { "back" to it })
        val defects = sides.flatMap { (label, side) -> sideFindings(label, side) }
            .mapIndexed { i, d -> d.copy(id = String.format(Locale.ROOT, "defect_%03d", i + 1)) }
        val problems = sides.flatMap { (label, side) -> side.problems.map { "${label}_${it.name.lowercase(Locale.ROOT)}" } }
        val extra = buildList {
            if (front == null) add("front")
            if (back == null) add("back")
            sides.forEach { (label, side) ->
                if (side.problems.isNotEmpty()) add("retake_$label")
                if (side.surfaceFinding == SurfaceFinding.NOT_REVIEWED || side.surfacePhotos.size < 2) add("tilted_${label}_surface")
                if ((Wear.CORNERS).any { side.wear.zones[it]?.evidence != Wear.Evidence.MEASURED }) add("${label}_corner_closeups")
            }
        }.distinct()
        // chips and bends are recorded per region: a corner region ("top_left") or an edge ("top")
        fun partOf(d: DefectFinding) = when {
            d.type.startsWith("surface") -> "surface"
            d.type.startsWith("corner") || (d.location != "whole_surface" && '_' in d.location) -> "corner"
            else -> "edge"
        }
        fun component(part: String): String? {
            if (sides.isEmpty()) return null
            val mine = defects.filter { partOf(it) == part }.map { it.findingStatus }
            return when {
                FindingStatus.CONFIRMED in mine -> "damage_confirmed"
                FindingStatus.POSSIBLE in mine -> "possible_damage"
                FindingStatus.NOT_ASSESSABLE in mine -> "not_fully_assessable"
                else -> "no_anomaly_flagged"
            }
        }
        val centering = CenteringEvidence(ratio(front?.centering?.leftRight), ratio(front?.centering?.topBottom),
            ratio(back?.centering?.leftRight), ratio(back?.centering?.topBottom))
        val limitations = buildList {
            add("The score uses centering only. Corners, edges and surface are reported as findings but do not move the score: no model for them has been validated on phone photos.")
            if (defects.any { it.type == "surface" }) add("Surface visibility is incomplete.")
            if (back == null) add("No back photo: back centering and back wear are unknown.")
            if (game == Game.ONE_PIECE) add("No graded One Piece cards were available for calibration; treat the range as provisional.")
            if (!supported) add("Pre-grading supports Pokémon and One Piece only.")
            add("Not a grade from PSA, BGS, CGC or TAG. A professional grade may differ.")
        }
        val status = when {
            !supported -> "unsupported_game"
            front == null -> "incomplete"
            result == null -> "needs_better_images"
            else -> "completed_with_limitations"
        }
        val report = EvidenceReport(
            game = game?.name?.lowercase(Locale.ROOT),
            card = EvidenceCard(title),
            analysisStatus = status,
            qualityChecks = QualityChecks(front?.problems?.isEmpty() == true, back?.problems?.isEmpty(), problems, extra),
            centering = centering,
            defects = defects,
            componentAssessments = ComponentAssessments(
                centering = result?.let { r ->
                    when {
                        AutomaticPreGrade.Reason.FRONT_OFF_CENTER in r.reasons || AutomaticPreGrade.Reason.BACK_OFF_CENTER in r.reasons -> "outside_psa_10_allowance"
                        AutomaticPreGrade.Reason.BORDERLINE in r.reasons -> "borderline_for_psa_10"
                        else -> "within_psa_10_allowance"
                    }
                },
                corners = component("corner"), edges = component("edge"), surface = component("surface"),
            ),
            preGrade = PreGradeEvidence(
                estimatedGrade = result?.grade?.takeIf { supported },
                estimatedRange = result?.takeIf { supported }?.let { listOf(it.low, it.high) },
                calibrationStatus = "not_yet_validated",
                limitations = limitations,
            ),
            explanation = emptyList(),
        )
        return report.copy(explanation = explain(report))
    }

    private fun words(key: String) = key.replace('_', ' ')

    /** The report as sentences. English, like the exported report it accompanies. */
    fun explain(r: EvidenceReport): List<String> = buildList {
        r.centering.frontHorizontal?.let { add("Front centering measures $it left/right and ${r.centering.frontVertical} top/bottom.") }
        r.centering.backHorizontal?.let { add("Back centering measures $it left/right and ${r.centering.backVertical} top/bottom.") }
        r.defects.filter { it.findingStatus == FindingStatus.CONFIRMED }.forEach {
            add("Confirmed: ${words(it.type)} on the ${it.side}, ${words(it.location)}.")
        }
        r.defects.filter { it.findingStatus == FindingStatus.POSSIBLE }.forEach {
            add("Possible ${words(it.type)} on the ${it.side}, ${words(it.location)}: ${it.evidence}")
        }
        val blind = r.defects.filter { it.findingStatus == FindingStatus.NOT_ASSESSABLE }
        if (blind.isNotEmpty()) add("Not assessable from these photos: " + blind.joinToString(", ") { "${it.side} ${words(it.location)}" } + ".")
        if (r.defects.none { it.findingStatus != FindingStatus.NOT_ASSESSABLE } && r.qualityChecks.frontUsable)
            add("No anomaly was flagged where the photos could be measured. That is not proof of a flawless card.")
        if (r.qualityChecks.additionalImagesRequired.isNotEmpty())
            add("For a fuller assessment add: " + r.qualityChecks.additionalImagesRequired.joinToString(", ") { words(it) } + ".")
        r.preGrade.estimatedGrade?.let { g ->
            add("Centering-based score $g (range ${r.preGrade.estimatedRange?.joinToString("–")}), provisional until corners, edges and surface can be assessed.")
        }
    }
}
