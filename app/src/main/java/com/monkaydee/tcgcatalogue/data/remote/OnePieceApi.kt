package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.serialization.json.JsonElement

/** One Piece card data and TCGplayer prices from https://optcgapi.com. */
class OnePieceApi(private val http: Http) {
    private val base = "https://optcgapi.com/api"

    /** Looks up a card code like "OP05-060", "ST01-001", "EB01-012" or "P-001", including all alt arts. */
    suspend fun card(code: String): CardCandidate? {
        val endpoints = when {
            code.startsWith("ST") -> listOf("decks", "sets", "promos")
            code.startsWith("P-") -> listOf("promos", "sets", "decks")
            else -> listOf("sets", "decks", "promos")
        }
        for (endpoint in endpoints) {
            val rows = http.getJson("$base/$endpoint/card/$code/").arr().orEmpty()
            if (rows.isNotEmpty()) return parse(code, rows)
        }
        return null
    }

    /** Number of distinct cards in a set, e.g. "OP-05" -> 121. Reprints from other sets are not counted. */
    suspend fun setSize(setId: String): Int {
        if (setId == "P") return 0
        val endpoint = if (setId.startsWith("ST")) "decks" else "sets"
        val prefix = setId.replace("-", "")
        return http.getJson("$base/$endpoint/$setId/").arr().orEmpty()
            .mapNotNull { it["card_set_id"].str() }
            .filter { it.startsWith(prefix) }
            .toSet().size
    }

    private fun parse(code: String, rows: List<JsonElement>): CardCandidate {
        // The API also lists reprints from later sets (e.g. an SPR of EB01-001 in EB-02);
        // the card belongs to the set its code names, and the plain printing comes first.
        val expectedSet = expectedSetId(code)
        val ordered = rows.sortedWith(
            compareByDescending<JsonElement> { it["card_image_id"].str() == code }
                .thenByDescending { it["set_id"].str() == expectedSet },
        )
        val home = ordered.firstOrNull { it["set_id"].str() == expectedSet } ?: ordered.first()
        val variants = ordered.mapIndexed { i, r ->
            val imageId = r["card_image_id"].str()
            val label = variantLabel(r["card_name"].str().orEmpty())
                .ifBlank { if (imageId == code || i == 0) "Standard" else "Variant ${i + 1}" }
            val price = r["market_price"].dbl()?.takeIf { it > 0 } ?: r["inventory_price"].dbl()?.takeIf { it > 0 }
            val details = listOfNotNull(
                r["market_price"].dbl()?.takeIf { it > 0 }?.let { PricePoint(PriceSource.TCGPLAYER, "Market", it) },
                r["inventory_price"].dbl()?.takeIf { it > 0 }?.let { PricePoint(PriceSource.TCGPLAYER, "Lowest listing", it) },
            )
            Variant(
                key = imageId ?: "$code#$i",
                label = label,
                prices = buildMap { price?.let { put(PriceSource.TCGPLAYER, it) } },
                imageUrl = r["card_image"].str(),
                details = details,
            )
        }.distinctBy { it.key }
        return CardCandidate(
            game = Game.ONE_PIECE,
            cardId = code,
            name = baseName(home["card_name"].str().orEmpty()),
            number = code,
            setId = home["set_id"].str() ?: expectedSet,
            setName = home["set_name"].str().orEmpty(),
            setTotal = 0,
            rarity = home["rarity"].str(),
            imageUrl = ordered.first()["card_image"].str(),
            variants = variants,
            score = 1.0,
        )
    }

    companion object {
        private val parens = Regex("""\(([^)]*)\)""")

        /** "OP05-060" -> "OP-05", "PRB01-001" -> "PRB-01", "P-001" -> "P" */
        fun expectedSetId(code: String): String {
            val prefix = code.substringBefore('-')
            if (prefix == "P") return "P"
            val letters = prefix.takeWhile { it.isLetter() }
            return "$letters-${prefix.drop(letters.length)}"
        }

        /** "Monkey.D.Luffy (060) (Alternate Art)" -> "Monkey.D.Luffy" */
        fun baseName(full: String) = full.replace(parens, "").replace(Regex("\\s+"), " ").trim()

        /** "Monkey.D.Luffy (060) (Alternate Art)" -> "Alternate Art"; the "(060)" number tag is dropped. */
        fun variantLabel(full: String) = parens.findAll(full).map { it.groupValues[1].trim() }
            .filterNot { it.all(Char::isDigit) }
            .joinToString(" · ")
    }
}
