package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

/** Magic: The Gathering card data and prices (Cardmarket EUR and TCGplayer USD) from Scryfall. */
class ScryfallApi(private val http: Http) {
    private val base = "https://api.scryfall.com"

    /** Card by set code and collector number, e.g. ("dmu", "107"). */
    suspend fun card(set: String, number: String): CardCandidate? =
        http.getJson("$base/cards/${enc(set.lowercase())}/${enc(number)}")?.let(::parse)

    /** Best match for a (possibly misspelled) card name, optionally within a set. */
    suspend fun named(name: String, set: String? = null): CardCandidate? =
        http.getJson("$base/cards/named?fuzzy=${enc(name)}" + (set?.let { "&set=${enc(it)}" } ?: ""))?.let(::parse)

    /** All printings matching [query] (Scryfall search syntax, e.g. a card name), newest first. */
    suspend fun search(query: String, limit: Int = 60): List<CardCandidate> =
        http.getJson("$base/cards/search?q=${enc(query)}&unique=prints&order=released")?.get("data")
            .arr().orEmpty().take(limit).mapNotNull(::parse)

    /** Set name and card count. */
    suspend fun set(code: String): Pair<String, Int>? {
        val s = http.getJson("$base/sets/${enc(code)}") ?: return null
        return s["name"].str().orEmpty() to (s["printed_size"].int() ?: s["card_count"].int() ?: 0)
    }

    private fun parse(c: JsonElement): CardCandidate? {
        if (c["object"].str() != "card") return null
        val set = c["set"].str() ?: return null
        val number = c["collector_number"].str() ?: return null
        val prices = c["prices"]
        val finishes = c["finishes"].arr().orEmpty().mapNotNull { it.str() }.ifEmpty { listOf("nonfoil") }
        val variants = finishes.map { f ->
            val (usd, eur) = when (f) {
                "foil" -> prices["usd_foil"] to prices["eur_foil"]
                "etched" -> prices["usd_etched"] to prices["eur_etched"]
                else -> prices["usd"] to prices["eur"]
            }
            Variant(
                key = f,
                label = when (f) { "foil" -> "Foil"; "etched" -> "Etched foil"; else -> "Normal" },
                prices = buildMap {
                    eur.str()?.toDoubleOrNull()?.let { put(PriceSource.CARDMARKET, it) }
                    usd.str()?.toDoubleOrNull()?.let { put(PriceSource.TCGPLAYER, it) }
                },
                tcgplayerId = (if (f == "etched") c["tcgplayer_etched_id"] else c["tcgplayer_id"]).str()?.toLongOrNull(),
                tcgplayerPrinting = if (f == "nonfoil") "Normal" else "Foil",
            )
        }
        val image = c["image_uris"]["normal"].str() ?: c["card_faces"].arr()?.firstOrNull()?.get("image_uris")?.get("normal").str()
        return CardCandidate(
            game = Game.MAGIC,
            cardId = "$set/$number",
            name = c["name"].str().orEmpty(),
            number = "${set.uppercase()} $number",
            setId = set,
            setName = c["set_name"].str().orEmpty(),
            setTotal = 0,
            rarity = c["rarity"].str()?.replaceFirstChar { it.uppercase() },
            imageUrl = image,
            variants = variants,
            score = 1.0,
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
