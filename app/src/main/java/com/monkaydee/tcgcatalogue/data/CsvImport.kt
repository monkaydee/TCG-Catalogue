package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.Variant

/** One card line from another app's export, before it is matched to a card of the catalogue. */
data class ImportRow(
    val line: Int,
    val name: String,
    val set: String? = null,
    val setCode: String? = null,
    val number: String? = null,
    val quantity: Int = 1,
    /** NM, LP, MP, HP or DMG; null when the file does not say. */
    val condition: String? = null,
    /** Two-letter code ("EN", "DE", "JA" …); null when the file does not say. */
    val language: String? = null,
    val foil: Boolean = false,
    val printing: String? = null,
    /** Price paid per copy, in [purchaseCurrency]. */
    val purchase: Double? = null,
    val purchaseCurrency: String? = null,
    val game: Game? = null,
)

/**
 * Reads collection exports of other apps: CSV with a header row (ManaBox, TCGplayer, Dragon Shield,
 * Collectr, our own export and similar; comma, semicolon or tab separated) or a plain card list
 * ("4 Lightning Bolt (M10) 146", "2x Charizard 4/102"). Columns are found by their names.
 */
object CsvImport {
    private val NAME = listOf("name", "card name", "product name", "card", "simple name")
    private val SET = listOf("set name", "set", "edition", "expansion", "set_name")
    private val SET_CODE = listOf("set code", "setcode", "set_code", "edition code")
    private val NUMBER = listOf("collector number", "card number", "number", "collector_number", "no", "#", "cardnumber")
    private val QUANTITY = listOf("quantity", "qty", "count", "amount", "copies")
    private val CONDITION = listOf("condition", "card condition")
    private val LANGUAGE = listOf("language", "lang")
    private val FOIL = listOf("foil", "finish")
    private val PRINTING = listOf("printing", "variant", "variance", "treatment")
    private val PURCHASE = listOf("purchase price", "price bought", "average cost paid", "price paid", "cost", "purchase_price", "buy price")
    private val CURRENCY = listOf("purchase price currency", "currency")
    private val GAME = listOf("game", "category", "product line")

    fun parse(text: String): List<ImportRow> {
        val clean = text.removePrefix("\uFEFF").trim()
        if (clean.isEmpty()) return emptyList()
        val first = clean.lineSequence().first()
        val delimiter = listOf(',', ';', '\t').maxBy { d -> first.count { it == d } }
        val table = table(clean, delimiter)
        val header = table.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
        return if (header.any { it in NAME } && table.size > 1) fromTable(header, table.drop(1)) else fromList(clean)
    }

    /** RFC 4180 fields: quoted fields may hold the delimiter, quotes ("") and line breaks. */
    fun table(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                ch == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && ch == delimiter -> { row += field.toString(); field.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    private fun fromTable(header: List<String>, rows: List<List<String>>): List<ImportRow> {
        fun col(names: List<String>) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val name = col(NAME) ?: return emptyList()
        val set = col(SET); val code = col(SET_CODE); val number = col(NUMBER); val qty = col(QUANTITY)
        val cond = col(CONDITION); val lang = col(LANGUAGE); val foil = col(FOIL); val printing = col(PRINTING)
        val purchase = col(PURCHASE); val currency = col(CURRENCY); val game = col(GAME)
        return rows.mapIndexedNotNull { i, r ->
            fun at(c: Int?) = c?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            val n = at(name) ?: return@mapIndexedNotNull null
            val printingText = at(printing)
            ImportRow(
                line = i + 2,
                name = n,
                set = at(set),
                setCode = at(code),
                number = at(number),
                quantity = at(qty)?.toDoubleOrNull()?.toInt()?.coerceIn(1, 9999) ?: 1,
                condition = at(cond)?.let(::condition),
                language = at(lang)?.let(::language),
                foil = at(foil)?.let { f -> f.lowercase() in setOf("foil", "true", "yes", "1", "etched", "holo") } == true ||
                    printingText?.contains(Regex("(?i)foil|holo")) == true,
                printing = printingText,
                purchase = at(purchase)?.let(::amount),
                purchaseCurrency = at(currency)?.uppercase()?.takeIf { it.length == 3 }
                    ?: at(purchase)?.let { p -> when { '€' in p -> "EUR"; '$' in p -> "USD"; else -> null } },
                game = at(game)?.let(::game),
            )
        }
    }

    private val LIST_LINE = Regex("""^\s*(\d+)\s*[x×]?\s+(.+?)(?:\s+\(([A-Za-z0-9-]{2,8})\))?(?:\s+([A-Za-z]*\d+[A-Za-z]?(?:/[A-Za-z]*\d+)?))?\s*(\*[Ff]\*)?\s*$""")

    private fun fromList(text: String): List<ImportRow> = text.lines().mapIndexedNotNull { i, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("//") || line.endsWith(":")) return@mapIndexedNotNull null
        val m = LIST_LINE.matchEntire(line)
        if (m == null) ImportRow(i + 1, line)
        else ImportRow(i + 1, m.groupValues[2].trim(), setCode = m.groupValues[3].ifEmpty { null },
            number = m.groupValues[4].ifEmpty { null }, quantity = m.groupValues[1].toInt().coerceIn(1, 9999), foil = m.groupValues[5].isNotEmpty())
    }

    /** "12,50 €", "$3.99", "1.234,56" → number; null when not a price. */
    fun amount(raw: String): Double? {
        var t = raw.filter { it.isDigit() || it == '.' || it == ',' }
        if (t.isEmpty()) return null
        val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
        t = if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        return t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }

    fun condition(raw: String): String? = when (raw.trim().lowercase().replace('_', ' ')) {
        "m", "mint", "nm", "near mint", "nm-m", "nm/m", "near mint or better", "mt" -> "NM"
        "ex", "excellent", "lp", "lightly played", "light played", "sp", "slightly played", "ex+" -> "LP"
        "gd", "good", "mp", "moderately played", "played", "vg", "very good" -> "MP"
        "pl", "hp", "heavily played", "heavy played", "poor played" -> "HP"
        "po", "poor", "dmg", "damaged" -> "DMG"
        else -> null
    }

    private val LANGUAGES = mapOf(
        "english" to "EN", "german" to "DE", "deutsch" to "DE", "french" to "FR", "français" to "FR", "italian" to "IT",
        "spanish" to "ES", "español" to "ES", "portuguese" to "PT", "japanese" to "JA", "jp" to "JA", "korean" to "KO",
        "chinese" to "ZH", "simplified chinese" to "ZH", "traditional chinese" to "ZH", "chinese simplified" to "ZH",
        "chinese traditional" to "ZH", "russian" to "RU", "dutch" to "NL", "polish" to "PL", "thai" to "TH", "indonesian" to "ID",
    )

    fun language(raw: String): String? {
        val t = raw.trim().lowercase()
        return LANGUAGES[t] ?: t.uppercase().takeIf { it.length == 2 && it.all(Char::isLetter) }?.let { if (it == "JP") "JA" else it }
    }

    fun game(raw: String): Game? {
        val t = raw.lowercase()
        return when {
            "pok" in t -> Game.POKEMON
            "one piece" in t -> Game.ONE_PIECE
            "magic" in t || t == "mtg" -> Game.MAGIC
            "fusion world" in t -> Game.DRAGON_BALL_FW
            "dragon ball" in t -> Game.DRAGON_BALL_SUPER
            "union arena" in t -> Game.UNION_ARENA
            "weiss" in t -> Game.WEISS_SCHWARZ
            "naruto" in t -> Game.NARUTO
            else -> null
        }
    }

    /** "004/102", "4", "#4" → "4"; letters kept ("TG05" → "TG5", "OP05-060" → "OP05-60"). */
    fun numberKey(raw: String?): String? = raw?.substringBefore('/')?.trim()?.removePrefix("#")
        ?.replace(Regex("(?<=^|[^0-9])0+(?=\\d)"), "")?.uppercase()?.takeIf { it.isNotEmpty() }
}

/** Catalogue cards an imported row may be, best first; [sure] when exactly one fits its number (and set, if given). */
data class ImportMatch(val candidates: List<CardCandidate>, val sure: Boolean)

/** The printing an imported row names: by label, then foil/holo (reverse only when the file says so), else the default. */
fun CardCandidate.printingFor(row: ImportRow): Variant {
    val p = row.printing?.trim()?.lowercase()
    if (p != null) variants.firstOrNull { it.label.lowercase() == p || it.key.lowercase() == p }?.let { return it }
    if (row.foil) {
        val reverse = p?.contains("reverse") == true
        variants.firstOrNull { v -> Regex("(?i)foil|holo").containsMatchIn(v.label) && v.label.contains("reverse", true) == reverse }?.let { return it }
    }
    return defaultVariant
}
