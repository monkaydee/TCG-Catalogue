package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Graded-card prices from eBay's sold listings: the average of the last few sales of the same
 * card in the same grade by the same company. Used when PriceCharting has no data.
 * eBay refuses plain HTTP clients, so the results page is read through a [Browser].
 */
class EbayApi(private val browser: Browser) {
    data class Sale(val text: String, val price: Double, val currency: String)

    data class Quote(val average: Double, val currency: String, val count: Int, val site: String)

    private data class Cached(val quote: Quote?, val at: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    /** Average of the last [take] sales of [card] graded [grader] [grade], or null if there are none. */
    suspend fun gradedAverage(card: CardCandidate, grader: String, grade: String, qualifier: String?, preferEuro: Boolean, take: Int = 5): Quote? {
        val key = "${card.game}/${card.cardId}/$grader/$grade/$qualifier/$preferEuro"
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < TTL }?.let { return it.quote }
        val number = searchNumber(card)
        val query = listOfNotNull(baseName(card.name), number, grader.takeUnless { it == "Other" }, grade, qualifier).joinToString(" ")
        val sites = if (preferEuro) listOf("www.ebay.de" to "EUR", "www.ebay.com" to "USD") else listOf("www.ebay.com" to "USD", "www.ebay.de" to "EUR")
        var best: Quote? = null
        var failures = 0
        var lastProblem: String? = null
        for ((site, currency) in sites) {
            val loaded = attempt { results(site, query) }
            val blocks = loaded.getOrNull()
            if (blocks == null) {
                failures++
                lastProblem = loaded.exceptionOrNull()?.message ?: "couldn't load ${site.removePrefix("www.")}"
                continue
            }
            val sales = parseSales(blocks)
                .filter { it.currency == currency && matches(it.text, number, grader, grade, qualifier) }
                .take(take)
            if (sales.isNotEmpty() && (best == null || sales.size > best.count)) {
                best = Quote(sales.map { it.price }.average(), currency, sales.size, site.removePrefix("www."))
            }
            if (best != null && best.count >= take) break
        }
        // Don't remember a failure to load eBay, only real answers.
        if (failures < sites.size) cache[key] = Cached(best, System.currentTimeMillis())
        if (best == null && failures == sites.size) throw java.io.IOException(lastProblem ?: "couldn't load eBay")
        return best
    }

    /**
     * Text of every result card on the sold-listings page, newest sale first. A page that is
     * neither results nor "no results" (captcha, "Pardon our interruption") is an error.
     */
    private suspend fun results(site: String, query: String): List<String> {
        val url = "https://$site/sch/i.html?_nkw=${URLEncoder.encode(query, "UTF-8")}&LH_Sold=1&LH_Complete=1&_sop=13&_ipg=60"
        val json = browser.evaluate(url, SCRIPT, READY) ?: throw java.io.IOException("${site.removePrefix("www.")} didn't load")
        val root = Json.parseToJsonElement(json) as? JsonObject ?: throw java.io.IOException("unexpected page")
        val items = (root["items"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        val isResultsPage = (root["results"] as? JsonPrimitive)?.content == "true"
        if (!isResultsPage) {
            val title = (root["title"] as? JsonPrimitive)?.content.orEmpty()
            throw java.io.IOException("${site.removePrefix("www.")} blocked (\"${title.take(60)}\")")
        }
        return items
    }

    companion object {
        private const val TTL = 12 * 60 * 60 * 1000L

        /** Each result's visible text; class names change often, the text doesn't. */
        private const val SCRIPT = """JSON.stringify({title: document.title, results: String(!!document.querySelector('ul.srp-results, .srp-river-results, .srp-save-null-search, li.s-item, li.s-card, .srp-controls')), items: Array.from(new Set(document.querySelectorAll('ul.srp-results > li, li.s-item, li.s-card'))).map(function(li){return (li.innerText||'').trim();}).filter(function(t){return t.length>0;})})"""
        private const val READY = """document.readyState === 'complete' && !!document.querySelector('ul.srp-results, .srp-river-results, .srp-save-null-search, li.s-item, li.s-card')"""

        private val soldMarker = Regex("""(?i)\b(sold|verkauft)\b""")
        private val pricePattern = Regex("""(\+\s*)?(?:(US\s?\$|\$|EUR|€)\s?(\d[\d.,]*)|(\d[\d.,]*)\s?(EUR|€))""")
        private val excluded = Regex("""(?i)\b(lot|bundle|set of|proxy|reprint|custom|orica|japanese|japan|jpn|korean|chinese|empty|case only|sticker)\b""")

        /** Sold listings with their price; shipping costs ("+EUR 5,00 Versand") are skipped. */
        fun parseSales(blocks: List<String>): List<Sale> = blocks.mapNotNull { text ->
            if (!soldMarker.containsMatchIn(text)) return@mapNotNull null
            val m = pricePattern.findAll(text).firstOrNull { it.groupValues[1].isEmpty() } ?: return@mapNotNull null
            val symbol = m.groupValues[2].ifEmpty { m.groupValues[5] }
            val amount = parseAmount(m.groupValues[3].ifEmpty { m.groupValues[4] }) ?: return@mapNotNull null
            Sale(text, amount, if (symbol.contains("$")) "USD" else "EUR")
        }

        /** "1.234,56" (German) and "1,234.56" (English) both work. */
        fun parseAmount(raw: String): Double? {
            val s = raw.trim().trimEnd('.', ',')
            val lastComma = s.lastIndexOf(',')
            val lastDot = s.lastIndexOf('.')
            val normalized = when {
                lastComma > lastDot && s.length - lastComma - 1 == 2 -> s.replace(".", "").replace(',', '.')
                lastComma > lastDot -> s.replace(",", "")
                else -> s.replace(",", "")
            }
            return normalized.toDoubleOrNull()
        }

        /** The listing is the same card ([number]) in exactly this grade, and a single card. */
        fun matches(text: String, number: String, grader: String, grade: String, qualifier: String?): Boolean {
            val t = text.uppercase()
            if (excluded.containsMatchIn(t)) return false
            val digits = number.filter { it.isDigit() }.trimStart('0')
            if (digits.isNotEmpty() && !Regex("""(?<![\d])0*${Regex.escape(digits)}(?![\d])""").containsMatchIn(t)) return false
            val company = when (grader) {
                "BGS" -> "(?:BGS|BECKETT)"
                "Other" -> "(?:[A-Z]{2,5})"
                else -> Regex.escape(grader)
            }
            val words = """(?:GEM\s*MINT|GEM\s*MT|MINT\+?|PRISTINE|NM-MT\+?|NM\+?|EX-MT|EX|VG|GOOD)?"""
            val gradeRe = Regex("""\b$company\s*$words\s*${Regex.escape(grade)}(?![\d.])""")
            if (!gradeRe.containsMatchIn(t)) return false
            if (qualifier == "Black Label" && !t.contains("BLACK LABEL")) return false
            if (qualifier == null && grader == "BGS" && grade == "10" && t.contains("BLACK LABEL")) return false
            if (qualifier == "Pristine" && !t.contains("PRISTINE")) return false
            return true
        }

        internal fun searchNumber(card: CardCandidate): String = when (card.game) {
            Game.POKEMON -> card.number.substringBefore('/').trimStart('0').ifEmpty { "0" }
            Game.MAGIC -> card.number.substringAfter(' ')
            else -> card.number
        }

        private fun baseName(name: String) = name.substringBefore(" - ").replace(Regex("""\s*\([^)]*\)"""), "").trim()
    }
}
