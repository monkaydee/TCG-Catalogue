package com.monkaydee.tcgcatalogue.data.remote

import java.util.concurrent.ConcurrentHashMap

/**
 * Market prices per condition (Near Mint ... Damaged) from TCGplayer's sales history, the same
 * data as the price-history chart on a TCGplayer product page. Language is matched explicitly; markets never imply the printed language.
 */
class TcgPlayerApi(private val http: Http) {
    /** printing ("Normal", "Holofoil", ...) -> condition ("Near Mint", ...) -> market price in USD */
    data class ConditionPrices(val byPrinting: Map<String, Map<String, Double>>)

    private data class Cached(val prices: ConditionPrices?, val at: Long)

    private val cache = ConcurrentHashMap<Pair<Long, String>, Cached>()

    suspend fun conditionPrices(productId: Long, language: String = "EN"): ConditionPrices? {
        val key = productId to language
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < TTL }?.let { return it.prices }
        val root = http.getText("https://infinite-api.tcgplayer.com/price/history/$productId/detailed?range=quarter", accept = "application/json", userAgent = BROWSER)
            ?.takeIf { it.isNotBlank() }?.let(http.json::parseToJsonElement)
        val byPrinting = mutableMapOf<String, MutableMap<String, Double>>()
        root["result"].arr().orEmpty().forEach { r ->
            val expected = if (language == "JA") "Japanese" else if (language == "EN") "English" else return@forEach
            if (!r["language"].str().equals(expected, ignoreCase = true)) return@forEach
            val printing = r["variant"].str() ?: return@forEach
            val condition = r["condition"].str() ?: return@forEach
            // Buckets are newest first; take the latest known market price.
            val price = r["buckets"].arr().orEmpty().firstNotNullOfOrNull { b -> b["marketPrice"].str()?.toDoubleOrNull()?.takeIf { it > 0 } }
                ?: return@forEach
            byPrinting.getOrPut(printing) { mutableMapOf() }[condition] = price
        }
        val result = byPrinting.takeIf { it.isNotEmpty() }?.let { ConditionPrices(it) }
        cache[key] = Cached(result, System.currentTimeMillis())
        return result
    }

    companion object {
        private const val TTL = 12 * 60 * 60 * 1000L
        private const val BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

        /** The app's condition codes as TCGplayer names them. */
        val CONDITION_NAMES = mapOf(
            "NM" to "Near Mint",
            "LP" to "Lightly Played",
            "MP" to "Moderately Played",
            "HP" to "Heavily Played",
            "DMG" to "Damaged",
        )

        /** Typical share of the Near Mint price, used when TCGplayer has no sales for a condition. */
        val DEFAULT_FACTORS = mapOf("NM" to 1.0, "LP" to 0.85, "MP" to 0.70, "HP" to 0.50, "DMG" to 0.35)

        /** The condition prices of the printing that matches [printing] (or the only one there is). */
        fun forPrinting(prices: ConditionPrices, printing: String?): Map<String, Double>? {
            val all = prices.byPrinting
            if (printing != null) all[printing]?.let { return it }
            if (all.size == 1) return all.values.first()
            val p = printing?.lowercase().orEmpty()
            val key = all.keys.firstOrNull { k ->
                val l = k.lowercase()
                when {
                    "reverse" in p -> "reverse" in l
                    "1st" in p -> "1st" in l
                    "foil" in p || "holo" in p -> ("foil" in l || "holo" in l) && "reverse" !in l && "1st" !in l
                    else -> l == "normal" || l.startsWith("unlimited")
                }
            }
            return key?.let { all[it] }
        }
    }
}
