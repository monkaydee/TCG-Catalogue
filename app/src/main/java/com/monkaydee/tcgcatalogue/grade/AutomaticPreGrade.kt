package com.monkaydee.tcgcatalogue.grade

/** Automatic workflow. Surface has no validated phone-photo model, so the score uses centering only. */
object AutomaticPreGrade {
    enum class Scope { CENTERING_ONLY, FRONT_CENTERING_ONLY }
    enum class Reason { CENTERED, FRONT_OFF_CENTER, BACK_OFF_CENTER, BORDERLINE, BACK_MISSING, SURFACE_UNAVAILABLE, ADJUSTED_GUIDES }
    data class Result(val grade: Int, val low: Int, val high: Int, val scope: Scope, val reasons: List<Reason>)

    fun assess(front: PreGrader.Side?, back: PreGrader.Side?): Result? {
        if (front == null || front.problems.isNotEmpty()) return null
        val f = front.centering?.takeIf(::valid) ?: return null
        // A bad back photo is not silently treated like an omitted back.
        if (back != null && (back.problems.isNotEmpty() || back.centering?.let(::valid) != true)) return null
        val b = back?.centering
        var score = score(f.worst, b?.worst)
        val reasons = mutableListOf<Reason>()
        if (f.worst > 55.0 + 1e-9) reasons += Reason.FRONT_OFF_CENTER
        if (b != null && b.worst > 75.0 + 1e-9) reasons += Reason.BACK_OFF_CENTER
        if (b != null && CenteringPotential.assess(f, b, true) == CenteringPotential.Status.BORDERLINE_10) {
            score = minOf(score, 9)
            reasons += Reason.BORDERLINE
        }
        if (b == null) { score = minOf(score, 9); reasons += Reason.BACK_MISSING }
        if (reasons.isEmpty()) reasons += Reason.CENTERED
        reasons += Reason.SURFACE_UNAVAILABLE
        if (front.manualCentering || back?.manualCentering == true) reasons += Reason.ADJUSTED_GUIDES
        val maximum = if (b == null || Reason.FRONT_OFF_CENTER in reasons || Reason.BACK_OFF_CENTER in reasons || Reason.BORDERLINE in reasons) 9 else 10
        return Result(score, (score - if (b == null) 2 else 1).coerceAtLeast(1), (score + 1).coerceAtMost(maximum),
            if (b == null) Scope.FRONT_CENTERING_ONLY else Scope.CENTERING_ONLY, reasons)
    }

    private fun valid(c: Centering.Result): Boolean =
        listOf(c.left, c.right, c.top, c.bottom).all { it.isFinite() && it > 0 } &&
            c.cuts.size == 4 && c.cuts.all { it.isFinite() && it >= 0 } && c.rotationDegrees.isFinite()

    /** Experimental ordinal score, not an implementation of PSA's overall grading standard. */
    internal fun score(front: Double, back: Double?): Int {
        val thresholds = listOf(55.0, 60.0, 65.0, 70.0, 75.0, 80.0, 85.0, 90.0, 95.0)
        val frontScore = 10 - thresholds.indexOfFirst { front <= it + 1e-9 }.let { if (it < 0) 9 else it }
        val backScore = if (back == null) 10 else
            10 - listOf(75.0, 80.0, 85.0, 90.0, 95.0, 96.0, 97.0, 98.0, 99.0)
                .indexOfFirst { back <= it + 1e-9 }.let { if (it < 0) 9 else it }
        return minOf(frontScore, backScore).coerceIn(1, 10)
    }
}
