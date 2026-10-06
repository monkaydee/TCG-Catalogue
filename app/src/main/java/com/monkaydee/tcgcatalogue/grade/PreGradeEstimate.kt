package com.monkaydee.tcgcatalogue.grade

enum class SurfaceFinding { NOT_REVIEWED, CLEAR_IN_PHOTOS, SCRATCHES, DENT_OR_CREASE }

/** A deliberately broad observation-based estimate, not fitted probabilities or PSA rules. */
object PreGradeEstimate {
    data class Result(val low: Int, val high: Int, val reasons: List<String>)
    fun missing(front: PreGrader.Side?, back: PreGrader.Side?): List<String> = buildList {
        for ((label, side) in listOf("Front" to front, "Back" to back)) {
            if (side == null) { add("$label photo missing"); continue }
            if (!side.outlineConfirmed) add("$label outline needs confirmation")
            if (side.problems.isNotEmpty()) add("$label photo needs a retake")
            if (side.centering == null && !side.centeringSkipped) add("$label centering incomplete")
            if ((Wear.CORNERS + Wear.EDGES).any { side.wearFindings[it] == null || side.wearFindings[it] == Wear.Finding.NOT_REVIEWED }) add("$label: review all four corners and four edges")
            if (side.surfaceFinding == SurfaceFinding.NOT_REVIEWED || side.surfacePhotos.size < 2) add("$label: add two lighting angles and review the surface")
        }
    }
    fun assess(front: PreGrader.Side?, back: PreGrader.Side?): Result? {
        if (missing(front, back).isNotEmpty()) return null
        val sides = listOf(front!!, back!!)
        var low = 8; var high = 10
        val reasons = mutableListOf<String>()
        val center = CenteringPotential.assess(front.centering, back.centering, true)
        if (center == CenteringPotential.Status.BELOW_10) { high = 9; low = 7; reasons += "Centering exceeds the published PSA 10 allowance" }
        if (center == CenteringPotential.Status.BORDERLINE_10) reasons += "Centering is borderline; guide placement matters"
        if (sides.any { it.centeringSkipped }) { low = 7; reasons += "Comparable printed frame unavailable; centering is unassessed" }
        val findings = sides.flatMap { it.wearFindings.values }
        if (Wear.Finding.WHITENING in findings || sides.any { it.surfaceFinding == SurfaceFinding.SCRATCHES }) {
            low = minOf(low, 6); high = minOf(high, 9); reasons += "Visible whitening/scuffing or surface scratches"
        }
        if (Wear.Finding.CHIP_OR_TEAR in findings || Wear.Finding.BEND_OR_DENT in findings || sides.any { it.surfaceFinding == SurfaceFinding.DENT_OR_CREASE }) {
            low = 1; high = minOf(high, 6); reasons += "Structural damage recorded; severity cannot be measured from these photos"
        }
        if (sides.any { !it.wear.complete }) reasons += "Some automatic colour checks are inconclusive; estimate uses your observations"
        if (sides.any { it.wear.zones.values.any(Wear::possibleDamage) } && findings.all { it == Wear.Finding.NO_VISIBLE_DAMAGE }) {
            low = minOf(low, 7); reasons += "Automatic anomalies remain despite clear manual observations"
        }
        if (sides.any { it.contours.values.any { e -> e == ContourCheck.Evidence.ASYMMETRIC } }) {
            low = minOf(low, 7); reasons += "Corner contour asymmetry needs closer inspection"
        }
        if (reasons.isEmpty()) reasons += "No damage recorded in the supplied views; unseen defects remain possible"
        return Result(low, high, reasons)
    }
}
