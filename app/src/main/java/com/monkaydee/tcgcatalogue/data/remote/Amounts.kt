package com.monkaydee.tcgcatalogue.data.remote

object Amounts {
    /** "1.234,56" (German) and "1,234.56" (English) both work. */
    fun parse(raw: String): Double? {
        val s = raw.trim().trimEnd('.', ',')
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val normalized = when {
            lastComma > lastDot && s.length - lastComma - 1 == 2 -> s.replace(".", "").replace(',', '.')
            else -> s.replace(",", "")
        }
        return normalized.toDoubleOrNull()
    }
}
