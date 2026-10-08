package com.monkaydee.tcgcatalogue.scan

/**
 * What the barcode or QR code on a slab label says: the certificate number and, when the code is a
 * grader's lookup link, the company. A bare number does not name the company.
 */
object SlabCode {
    data class Result(val grader: String?, val cert: String)

    private val LINKS = listOf(
        "psacard.com" to "PSA", "cgccards.com" to "CGC", "beckett.com" to "BGS", "gosgc.com" to "SGC", "sgccard.com" to "SGC",
        "tagrading.com" to "TAG", "acegrading.co.uk" to "ACE",
    )

    fun parse(raw: String): Result? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()
        val grader = LINKS.firstOrNull { (host, _) -> host in lower }?.second
        // In a link the certificate is the last long run of digits (path or query); otherwise the code itself.
        val digits = Regex("""\d{6,14}""").findAll(text).map { it.value }.lastOrNull()
        val cert = when {
            grader != null || "://" in text -> digits
            text.all { it.isLetterOrDigit() || it == '-' } -> text.filter(Char::isLetterOrDigit).takeIf { it.length in 6..14 }
            else -> digits
        } ?: return null
        return Result(grader, cert)
    }

    /** "GEM MT 10", "NM-MT 8", "MINT 9" → "10", "8", "9"; "AUTHENTIC" → null. */
    fun psaGrade(label: String?): String? = label?.let { Regex("""\d+(?:\.5)?""").findAll(it).lastOrNull()?.value }
        ?.takeIf { it.toDoubleOrNull()?.let { g -> g in 1.0..10.0 } == true }
}
