package com.monkaydee.tcgcatalogue.data

/** Reading PSA's population: grades high to low and the share of copies graded 10 ("gem rate"). */
object Population {
    /** Numeric grades first, highest first; "(Q)" and other labels after their grade; "Authentic" last. */
    fun ordered(byGrade: Map<String, Int>): List<Pair<String, Int>> = byGrade.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { Regex("""^\d+(\.5)?""").find(it.key)?.value?.toDoubleOrNull() ?: -1.0 }.thenBy { it.key })
        .map { it.key to it.value }

    /** Percentage of graded copies that are PSA 10, or null without a total. */
    fun gemRate(total: Int, byGrade: Map<String, Int>): Double? = if (total <= 0) null else (byGrade["10"] ?: 0) * 100.0 / total
}
