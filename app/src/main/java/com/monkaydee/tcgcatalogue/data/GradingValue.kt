package com.monkaydee.tcgcatalogue.data

/**
 * "Worth grading?": what a raw card would be worth graded, after the cost of grading it, for every
 * grade the price server quoted. Only exact quotes are used; nothing here predicts which grade a
 * card will get.
 */
object GradingValue {
    data class Outcome(val grader: String, val grade: String, val qualifier: String?, val graded: Double, val gain: Double)

    /** [quotes] are (grader, grade, qualifier, price in the same currency as [raw] and [cost]). Unknown raw value: nothing. */
    fun outcomes(raw: Double?, cost: Double, quotes: List<Outcome>): List<Outcome> {
        if (raw == null || raw <= 0) return emptyList()
        return quotes.map { it.copy(gain = it.graded - raw - cost) }
            .sortedWith(compareBy<Outcome> { it.grader }.thenByDescending { it.grade.toDoubleOrNull() ?: 0.0 }.thenBy { it.qualifier ?: "" })
    }

    /** Per company, the lowest numeric grade (without qualifier) from which grading pays off, or null when none of its quotes does. */
    fun breakEven(outcomes: List<Outcome>): Map<String, String?> = outcomes.groupBy { it.grader }.mapValues { (_, list) ->
        list.filter { it.qualifier == null && it.gain > 0 }.minByOrNull { it.grade.toDoubleOrNull() ?: Double.MAX_VALUE }?.grade
    }
}
