package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Graded card prices (PSA / BGS / CGC / SGC / TAG / ACE 10s and Grade 1–9.5) from PriceCharting,
 * which tracks sold listings. Reads the public card pages, at most once per card per day.
 */
class PriceChartingApi(private val http: Http) {
    /** Prices by row label ("Ungraded", "Grade 9", "PSA 10", "BGS 10 Black", ...) in [currency]. */
    data class Table(val url: String, val title: String, val prices: Map<String, Double>, val currency: String = "USD")

    /** A lookup result with the reason when there is no table, shown to the user. */
    data class Lookup(val table: Table?, val problem: String?)

    private data class Cached(val table: Table?, val at: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    /** PriceCharting's prices for [card] in the [variant] printing, or null if it isn't listed. */
    suspend fun table(card: CardCandidate, variant: Variant?): Table? = lookup(card, variant).table

    suspend fun lookup(card: CardCandidate, variant: Variant?): Lookup {
        val key = "${card.game}/${card.cardId}/${variant?.key}"
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < (if (it.table == null) NOT_FOUND_TTL else DAY) }
            ?.let { return Lookup(it.table, if (it.table == null) "not listed" else null) }
        val result = attempt { find(card, variant) }
        // Only remember real answers; a failed request is retried next time.
        if (result.isSuccess) cache[key] = Cached(result.getOrNull(), System.currentTimeMillis())
        return result.fold(
            onSuccess = { Lookup(it, if (it == null) "not listed" else null) },
            onFailure = { Lookup(null, it.message ?: it.javaClass.simpleName) },
        )
    }

    private suspend fun find(card: CardCandidate, variant: Variant?): Table? {
        val number = searchNumber(card)
        val query = when (card.game) {
            Game.POKEMON -> "${card.name} ${card.setName} $number"
            Game.MAGIC -> "${card.name} ${card.setName} $number"
            else -> "${baseName(card.name)} $number"
        }
        val html = page("$BASE/search-products?type=prices&q=${enc(query)}") ?: return null
        // A unique match redirects straight to the card's page.
        if (html.contains("id=\"full-prices\"")) {
            return parseTable(canonical(html) ?: "$BASE/search", html)?.takeIf { matchesNumber(it.title, card.game, number) }
        }
        val best = parseResults(html)
            .filter { matchesNumber(it.title, card.game, number) }
            .map { it to score(it, card, variant) }
            // Never price an English card with a Japanese listing (those score below zero).
            .filter { it.second >= 0 }
            .maxByOrNull { it.second }?.first
            ?: return null
        return page(best.url)?.let { parseTable(best.url, it) }
    }

    /**
     * Fetches a page. When PriceCharting answers with something else than the page (bot check,
     * block or consent page), that is reported as "blocked", never as "not listed".
     */
    private suspend fun page(url: String): String? {
        val direct = attempt { http.getText(url, accept = "text/html", userAgent = BROWSER) }
        direct.getOrNull()?.takeIf { looksReal(it) }?.let { return it }
        if (direct.isSuccess && direct.getOrNull() == null) return null // 404
        val seen = direct.getOrNull()?.let { pageTitle.find(it)?.groupValues?.get(1)?.trim() }
        throw java.io.IOException(
            "blocked" + (seen?.takeIf { it.isNotBlank() }?.let { " (\"${it.take(60)}\")" } ?: direct.exceptionOrNull()?.message?.let { " ($it)" } ?: ""),
        )
    }

    /** A real search result, card page or "no results" page, not a bot check or block page. */
    private fun looksReal(html: String) =
        html.contains("id=\"games_table\"") || html.contains("id=\"full-prices\"") || html.contains("No results for", ignoreCase = true)

    internal data class Result(val url: String, val title: String, val console: String)

    companion object {
        private const val BASE = "https://www.pricecharting.com"
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val NOT_FOUND_TTL = 6 * 60 * 60 * 1000L
        private const val BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

        private val resultRow = Regex("""<tr[^>]*id="product-\d+"[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        private val resultTitle = Regex("""<td class="title">\s*<a href="([^"]+)"[^>]*>\s*([^<]+)""")
        private val tableRow = Regex("""<tr[^>]*>\s*<td[^>]*>([^<]+)</td>\s*<td[^>]*>([^<]+)</td>""")
        private val pageTitle = Regex("""<title>([^<]*)""")
        private val canonicalLink = Regex("""<link rel="canonical" href="([^"]+)"""")

        private fun canonical(html: String) = canonicalLink.find(html)?.groupValues?.get(1)?.let(::unescape)

        /** Search results: card page URL, title ("Pikachu [Reverse Holo] #25") and set slug. */
        internal fun parseResults(html: String): List<Result> = resultRow.findAll(html).mapNotNull { row ->
            val m = resultTitle.find(row.groupValues[1]) ?: return@mapNotNull null
            val url = unescape(m.groupValues[1]).let { if (it.startsWith("http")) it else BASE + it }
            Result(url, unescape(m.groupValues[2]).trim(), url.substringAfter("/game/").substringBefore('/'))
        }.toList()

        /** The "full prices" table of a card page. */
        internal fun parseTable(url: String, html: String): Table? {
            val start = html.indexOf("id=\"full-prices\"")
            if (start < 0) return null
            val section = html.substring(start, minOf(html.length, start + 12_000))
            // The site converts prices to the visitor's currency (e.g. "€14,50" in Germany).
            val cells = tableRow.findAll(section).mapNotNull { m ->
                val label = m.groupValues[1].trim()
                parsePrice(m.groupValues[2])?.let { (amount, currency) -> Triple(label, amount, currency) }
            }.toList()
            if (cells.isEmpty()) {
                if (section.contains(Regex("""\d"""))) throw java.io.IOException("couldn't read the prices")
                return null
            }
            val currency = cells.groupingBy { it.third }.eachCount().maxBy { it.value }.key
            val prices = cells.filter { it.third == currency }.associate { it.first to it.second }
            val title = pageTitle.find(html)?.groupValues?.get(1)?.substringBefore(" Prices")?.trim().orEmpty()
            return Table(url, unescape(title), prices, currency)
        }

        /** "$1,234.56" -> (1234.56, USD), "€14,50" / "14,50 €" -> (14.5, EUR); other currencies are not used. */
        internal fun parsePrice(cell: String): Pair<Double, String>? {
            val t = unescape(cell).replace("&euro;", "€").trim()
            if (t.isEmpty() || t == "-") return null
            val currency = when {
                t.contains("€") || t.contains("EUR") -> "EUR"
                Regex("""(^|[^A-Z])\$""").containsMatchIn(t) && !Regex("""[A-Z]\$""").containsMatchIn(t) -> "USD"
                else -> return null
            }
            val number = Regex("""\d[\d.,]*""").find(t)?.value ?: return null
            return Amounts.parse(number)?.let { it to currency }
        }

        /** The number as PriceCharting writes it: "#25" for Pokémon and Magic, the code for the others. */
        internal fun searchNumber(card: CardCandidate): String = when (card.game) {
            Game.POKEMON -> card.number.substringBefore('/').trimStart('0').ifEmpty { "0" }
            Game.MAGIC -> card.number.substringAfter(' ')
            else -> card.number
        }

        internal fun matchesNumber(title: String, game: Game, number: String): Boolean = when (game) {
            Game.POKEMON, Game.MAGIC -> title.substringAfterLast('#', "").trim().equals(number, ignoreCase = true)
            else -> title.contains(number, ignoreCase = true)
        }

        private val genericPrintings = setOf("normal", "standard", "nonfoil", "holo", "holofoil", "unlimited")

        /** What sets this printing apart: "Reverse Holo", "Parallel", "Alternate Art", "Foil", ... */
        internal fun printing(card: CardCandidate, variant: Variant?): String? {
            val fromName = Regex("""\(([^)]*)\)""").findAll(card.name).map { it.groupValues[1] }
                .filterNot { it.all(Char::isDigit) }.lastOrNull()
            return (fromName ?: variant?.label)?.lowercase()?.takeUnless { it in genericPrintings }
        }

        /** Prefers the English set with the same name and the same printing (Reverse Holo, Alternate Art, ...). */
        internal fun score(r: Result, card: CardCandidate, variant: Variant?): Double {
            var s = 0.0
            val console = r.console.replace('-', ' ')
            if (console.contains("japanese") || console.contains("chinese")) s -= 5
            val setWords = card.setName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
            s += setWords.count { console.contains(it) }.toDouble() / maxOf(1, setWords.size) * 3
            val bracket = Regex("""\[([^]]+)]""").find(r.title)?.groupValues?.get(1)?.lowercase()?.takeUnless { it in genericPrintings }
            val wanted = printing(card, variant)
            // One Piece calls TCGplayer's "Parallel" an "Alternate Art".
            val aliases = if (wanted == "parallel") setOf("parallel", "alternate art") else setOfNotNull(wanted)
            s += when {
                wanted == null && bracket == null -> 3.0
                // A plain card must never be priced as an alt art / special printing.
                wanted == null -> -100.0
                bracket == null -> 0.0
                bracket in aliases -> 3.0
                aliases.any { bracket.contains(it) || it.contains(bracket) } -> 1.5
                aliases.any { a -> a.split(' ').any { it.length > 3 && bracket.contains(it) } } -> 1.0
                else -> 0.0
            }
            return s
        }

        /** Price for a slab of [grader] [grade], with a note when it is only an estimate. */
        fun priceFor(table: Table, grader: String, grade: String, qualifier: String?): Pair<Double, String?>? {
            val exactRow = if (grade == "10") {
                when (grader) {
                    "PSA" -> "PSA 10"
                    "BGS" -> if (qualifier == "Black Label") "BGS 10 Black" else "BGS 10"
                    "CGC" -> if (qualifier == "Pristine") "CGC 10 Pristine" else "CGC 10"
                    "SGC" -> "SGC 10"
                    "TAG" -> "TAG 10"
                    "ACE" -> "ACE 10"
                    else -> null
                }
            } else {
                "Grade $grade"
            }
            exactRow?.let { row -> table.prices[row]?.let { return it to null } }
            // No sales for this exact slab: fall back to the closest general grade.
            val number = grade.toDoubleOrNull() ?: return null
            val fallbacks = listOf("10" to 10.0, "9.5" to 9.5) + (9 downTo 1).map { "$it" to it.toDouble() }
            for ((g, value) in fallbacks) {
                if (value > number) continue
                val row = if (g == "10") null else "Grade $g"
                table.prices[row]?.let { return it to "Estimate: no $grader $grade sales on PriceCharting, using Grade $g" }
            }
            return null
        }

        private fun baseName(name: String) = name.substringBefore(" - ").replace(Regex("""\s*\([^)]*\)"""), "").trim()

        private fun unescape(s: String) = s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&#x27;", "'")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
