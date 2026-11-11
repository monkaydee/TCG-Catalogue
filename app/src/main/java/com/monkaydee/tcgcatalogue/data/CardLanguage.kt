package com.monkaydee.tcgcatalogue.data

/** TCG display shorthand; stored/API language identifiers retain their ISO language code. */
object CardLanguage {
    fun displayCode(code: String): String = if (code.equals("JA", ignoreCase = true) || code.equals("JP", ignoreCase = true)) "JP" else code
}
