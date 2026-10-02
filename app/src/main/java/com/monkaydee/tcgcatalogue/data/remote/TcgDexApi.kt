package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder

/**
 * Pokémon card data and prices from TCGdex (https://tcgdex.dev), which carries
 * both Cardmarket (EUR) and TCGplayer (USD) prices.
 */
class TcgDexApi(private val http: Http) {
    private val base = "https://api.tcgdex.net/v2/en"
    private val setsMutex = Mutex()
    private var setsCache: List<SetSummary>? = null

    suspend fun sets(): List<SetSummary> = setsMutex.withLock {
        setsCache ?: run {
            val list = http.getJson("$base/sets").arr().orEmpty().mapNotNull { s ->
                SetSummary(
                    id = s["id"].str() ?: return@mapNotNull null,
                    name = s["name"].str().orEmpty(),
                    official = s["cardCount"]["official"].int() ?: 0,
                    total = s["cardCount"]["total"].int() ?: 0,
                    logoUrl = s["logo"].str()?.let { "$it.png" },
                )
            }
            setsCache = list
            list
        }
    }

    suspend fun setDetails(setId: String): Pair<SetSummary, String?>? {
        val s = http.getJson("$base/sets/${enc(setId)}") ?: return null
        val summary = SetSummary(
            id = s["id"].str() ?: setId,
            name = s["name"].str().orEmpty(),
            official = s["cardCount"]["official"].int() ?: 0,
            total = s["cardCount"]["total"].int() ?: 0,
            logoUrl = s["logo"].str()?.let { "$it.png" },
        )
        return summary to s["releaseDate"].str()
    }

    /** Card by set and printed number, e.g. ("sv03.5", "25") or ("swsh9tg", "TG01"). */
    suspend fun cardInSet(setId: String, localId: String): CardCandidate? =
        http.getJson("$base/sets/${enc(setId)}/${enc(localId)}")?.let(::parseCard)

    suspend fun card(cardId: String): CardCandidate? =
        http.getJson("$base/cards/${enc(cardId)}")?.let(::parseCard)

    suspend fun searchByName(name: String, limit: Int = 60): List<CardBrief> {
        val url = "$base/cards?name=${enc(name)}&pagination:itemsPerPage=$limit"
        return http.getJson(url).arr().orEmpty().mapNotNull { c ->
            val id = c["id"].str() ?: return@mapNotNull null
            CardBrief(
                game = Game.POKEMON,
                cardId = id,
                name = c["name"].str().orEmpty(),
                number = c["localId"].str().orEmpty(),
                imageUrl = c["image"].str()?.let { "$it/low.webp" },
            )
        }
    }

    private fun parseCard(c: kotlinx.serialization.json.JsonElement): CardCandidate? {
        val id = c["id"].str() ?: return null
        val localId = c["localId"].str().orEmpty()
        val set = c["set"]
        val official = set["cardCount"]["official"].int() ?: 0
        val prefix = localId.takeWhile { it.isLetter() }
        val number = if (official > 0) "$localId/${prefix}${official.toString().padStart(localId.length - prefix.length, '0')}" else localId
        val pricing = c["pricing"]
        val flags = c["variants"]
        val variants = buildList {
            if (flags["normal"].bool()) add(variant("normal", "Normal", pricing))
            if (flags["holo"].bool()) add(variant("holo", "Holo", pricing))
            if (flags["reverse"].bool()) add(variant("reverse", "Reverse Holo", pricing))
            if (flags["firstEdition"].bool()) add(firstEdition(c, pricing))
            if (isEmpty()) add(variant("normal", "Normal", pricing))
        }
        return CardCandidate(
            game = Game.POKEMON,
            cardId = id,
            name = c["name"].str().orEmpty(),
            number = number,
            setId = set["id"].str().orEmpty(),
            setName = set["name"].str().orEmpty(),
            setTotal = official,
            rarity = c["rarity"].str(),
            imageUrl = c["image"].str()?.let { "$it/high.webp" },
            variants = variants,
        )
    }

    private fun variant(key: String, label: String, pricing: kotlinx.serialization.json.JsonElement?): Variant {
        val tcg = pricing["tcgplayer"]
        val cm = pricing["cardmarket"]
        val tcgKeys = when (key) {
            "holo" -> listOf("holofoil", "normal")
            "reverse" -> listOf("reverse-holofoil", "reverseHolofoil")
            else -> listOf("normal", "holofoil")
        }
        val tcgPrice = tcgKeys.firstNotNullOfOrNull { k -> tcg[k]["marketPrice"].dbl() ?: tcg[k]["midPrice"].dbl() }
        // Cardmarket lists one product per card; its "-holo" fields are the reverse holo printing.
        val cmPrice = if (key == "reverse") {
            cm["trend-holo"].dbl()?.takeIf { it > 0 } ?: cm["avg-holo"].dbl()?.takeIf { it > 0 }
        } else {
            cm["trend"].dbl()?.takeIf { it > 0 } ?: cm["avg"].dbl()?.takeIf { it > 0 }
        }
        return Variant(key, label, prices(cmPrice, tcgPrice))
    }

    private fun firstEdition(c: kotlinx.serialization.json.JsonElement, pricing: kotlinx.serialization.json.JsonElement?): Variant {
        val detailed = c["variants_detailed"].arr().orEmpty().firstOrNull { v ->
            v["stamp"].arr().orEmpty().any { it.str() == "1st-edition" }
        }
        val p = detailed["pricing"]
        val tcg = p["tcgplayer"] ?: pricing["tcgplayer"]
        val tcgPrice = listOf("1stEditionHolofoil", "1stEditionNormal", "1stEdition", "holofoil", "normal")
            .firstNotNullOfOrNull { k -> tcg[k]["marketPrice"].dbl() }
        val cmPrice = p["cardmarket"]["trend"].dbl()?.takeIf { it > 0 } ?: p["cardmarket"]["avg"].dbl()?.takeIf { it > 0 }
        return Variant("firstEdition", "1st Edition", prices(cmPrice, tcgPrice))
    }

    private fun prices(cm: Double?, tcg: Double?) = buildMap {
        cm?.let { put(PriceSource.CARDMARKET, it) }
        tcg?.let { put(PriceSource.TCGPLAYER, it) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
