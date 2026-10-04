package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder

/**
 * Pokémon card data and prices from TCGdex (https://tcgdex.dev), which carries
 * both Cardmarket (EUR) and TCGplayer (USD) prices.
 */
class TcgDexApi(private val http: Http, val lang: String = "en") {
    private val base = "https://api.tcgdex.net/v2/$lang"

    /**
     * Ids of other languages carry a prefix ("ja:SV2a-025", "ja:SV2a") so they never mix with the
     * English ones in the collection; [raw] strips it for the API.
     */
    val idPrefix: String = if (lang == "en") "" else "$lang:"
    private fun raw(id: String) = id.removePrefix(idPrefix)

    /** The same API for another card language (Japanese: "ja"). */
    fun forLanguage(other: String) = TcgDexApi(http, other)
    private val setsMutex = Mutex()
    private var setsCache: List<SetSummary>? = null

    suspend fun sets(): List<SetSummary> = setsMutex.withLock {
        setsCache ?: run {
            val list = http.getJson("$base/sets").arr().orEmpty().mapNotNull { s ->
                SetSummary(
                    id = idPrefix + (s["id"].str() ?: return@mapNotNull null),
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
        val s = http.getJson("$base/sets/${enc(raw(setId))}") ?: return null
        val summary = SetSummary(
            id = s["id"].str()?.let { idPrefix + it } ?: setId,
            name = s["name"].str().orEmpty(),
            official = s["cardCount"]["official"].int() ?: 0,
            total = s["cardCount"]["total"].int() ?: 0,
            logoUrl = s["logo"].str()?.let { "$it.png" },
            abbreviation = s["abbreviation"]["official"].str(),
        )
        return summary to s["releaseDate"].str()
    }

    private val abbreviations = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The set code printed on the cards of [setId] ("" when it has none), cached for the session. */
    suspend fun abbreviation(setId: String): String = abbreviations[setId] ?: run {
        val code = setDetails(setId)?.first?.abbreviation?.uppercase().orEmpty()
        abbreviations[setId] = code
        code
    }

    /** Card by set and printed number, e.g. ("sv03.5", "25") or ("swsh9tg", "TG01"). */
    suspend fun cardInSet(setId: String, localId: String): CardCandidate? =
        http.getJson("$base/sets/${enc(raw(setId))}/${enc(localId)}")?.let(::parseCard)

    /** Card ids (same as the English ones) and names whose name in [language] contains [name] ("de", "Evoli"). */
    suspend fun searchByNameIn(language: String, name: String, limit: Int = 250): List<CardBrief> =
        http.getJson("https://api.tcgdex.net/v2/${language.lowercase()}/cards?name=${enc(name)}&pagination:itemsPerPage=$limit").arr().orEmpty().mapNotNull { c ->
            CardBrief(
                game = Game.POKEMON,
                cardId = c["id"].str() ?: return@mapNotNull null,
                name = c["name"].str().orEmpty(),
                number = c["localId"].str().orEmpty(),
                imageUrl = null,
            )
        }

    /** The card's printed name in another language ("de" → "Flamara"), null when TCGdex has none. */
    suspend fun localName(cardId: String, language: String): String? =
        runCatching { http.getJson("https://api.tcgdex.net/v2/${language.lowercase()}/cards/${enc(raw(cardId))}")?.get("name").str() }.getOrNull()

    suspend fun card(cardId: String): CardCandidate? =
        http.getJson("$base/cards/${enc(raw(cardId))}")?.let(::parseCard)

    suspend fun searchByName(name: String, limit: Int = 60): List<CardBrief> {
        val url = "$base/cards?name=${enc(name)}&pagination:itemsPerPage=$limit"
        return http.getJson(url).arr().orEmpty().mapNotNull { c ->
            val id = c["id"].str() ?: return@mapNotNull null
            CardBrief(
                game = Game.POKEMON,
                cardId = idPrefix + id,
                name = c["name"].str().orEmpty(),
                number = c["localId"].str().orEmpty(),
                imageUrl = c["image"].str()?.let { "$it/low.webp" } ?: pokemonTcgImage(idPrefix + id, large = false),
            )
        }
    }

    private fun parseCard(c: kotlinx.serialization.json.JsonElement): CardCandidate? {
        val id = idPrefix + (c["id"].str() ?: return null)
        val localId = c["localId"].str().orEmpty()
        val set = c["set"]
        val official = set["cardCount"]["official"].int() ?: 0
        val prefix = localId.takeWhile { it.isLetter() }
        val number = if (official > 0) "$localId/${prefix}${official.toString().padStart(localId.length - prefix.length, '0')}" else localId
        val pricing = c["pricing"]
        val flags = c["variants"]
        val variants = buildList {
            if (flags["normal"].bool()) add(variant("normal", AppStrings.get(R.string.data_variant_normal), pricing))
            if (flags["holo"].bool()) add(variant("holo", AppStrings.get(R.string.data_variant_holo), pricing))
            if (flags["reverse"].bool()) add(variant("reverse", AppStrings.get(R.string.data_variant_reverse_holo), pricing))
            if (flags["firstEdition"].bool()) add(firstEdition(c, pricing))
            if (isEmpty()) add(variant("normal", AppStrings.get(R.string.data_variant_normal), pricing))
        }
        return CardCandidate(
            game = Game.POKEMON,
            cardId = id,
            name = c["name"].str().orEmpty(),
            number = number,
            setId = idPrefix + set["id"].str().orEmpty(),
            setName = set["name"].str().orEmpty(),
            setTotal = official,
            rarity = c["rarity"].str(),
            imageUrl = c["image"].str()?.let { "$it/high.webp" } ?: pokemonTcgImage(id, large = true),
            variants = variants,
            cardmarketId = pricing["cardmarket"]["idProduct"].str()?.toLongOrNull(),
            attacks = c["attacks"].arr().orEmpty().mapNotNull { it["name"].str() },
        )
    }

    private val setCardsCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, String>>>()
    private val cardCache = java.util.concurrent.ConcurrentHashMap<String, CardCandidate>()

    /** The cards of a set as (card id, name), cached for the session. */
    suspend fun setCards(setId: String): List<Pair<String, String>> = setCardsCache[setId] ?: run {
        val cards = http.getJson("$base/sets/${enc(raw(setId))}")?.get("cards").arr().orEmpty().mapNotNull { c ->
            val id = idPrefix + (c["id"].str() ?: return@mapNotNull null)
            id to c["name"].str().orEmpty()
        }
        if (cards.isNotEmpty()) setCardsCache[setId] = cards
        cards
    }

    /** Every card of a set, in set order. */
    suspend fun setChecklist(setId: String): List<ChecklistEntry> =
        http.getJson("$base/sets/${enc(raw(setId))}")?.get("cards").arr().orEmpty().mapNotNull { c ->
            val id = idPrefix + (c["id"].str() ?: return@mapNotNull null)
            ChecklistEntry(id, c["localId"].str().orEmpty(), c["name"].str().orEmpty(), c["image"].str()?.let { "$it/low.webp" } ?: pokemonTcgImage(id, large = false))
        }

    /** [card], cached for the session (used to compare look-alike cards of a set). */
    suspend fun cachedCard(cardId: String): CardCandidate? = cardCache[cardId] ?: card(cardId)?.also { cardCache[cardId] = it }

    private fun variant(key: String, label: String, pricing: kotlinx.serialization.json.JsonElement?): Variant {
        val tcg = pricing["tcgplayer"]
        val cm = pricing["cardmarket"]
        val tcgKeys = when (key) {
            "holo" -> listOf("holofoil", "normal")
            "reverse" -> listOf("reverse-holofoil", "reverseHolofoil")
            else -> listOf("normal", "holofoil")
        }
        val tcgKey = tcgKeys.firstOrNull { k -> tcg[k]["marketPrice"].dbl() != null || tcg[k]["midPrice"].dbl() != null }
        val tcgPrice = tcgKey?.let { k -> tcg[k]["marketPrice"].dbl() ?: tcg[k]["midPrice"].dbl() }
        // Cardmarket lists one product per card; its "-holo" fields are the reverse holo printing.
        val cmPrice = if (key == "reverse") {
            cm["trend-holo"].dbl()?.takeIf { it > 0 } ?: cm["avg-holo"].dbl()?.takeIf { it > 0 }
        } else {
            cm["trend"].dbl()?.takeIf { it > 0 } ?: cm["avg"].dbl()?.takeIf { it > 0 }
        }
        val productId = (tcgKey ?: tcgKeys.firstOrNull { tcg[it] != null })?.let { tcg[it]["productId"].str()?.toLongOrNull() }
        val details = cardmarketDetails(cm, if (key == "reverse") "-holo" else "") + tcgplayerDetails(tcgKey?.let { tcg[it] })
        return Variant(key, label, prices(cmPrice, tcgPrice), tcgplayerId = productId, tcgplayerPrinting = tcgKey?.let(::printingName), details = details)
    }

    private fun cardmarketDetails(cm: kotlinx.serialization.json.JsonElement?, suffix: String) = listOf(
        "trend" to R.string.price_label_trend, "avg" to R.string.price_label_average_sold, "low" to R.string.price_label_lowest_offer,
        "avg1" to R.string.price_label_avg1, "avg7" to R.string.price_label_avg7, "avg30" to R.string.price_label_avg30,
    ).mapNotNull { (k, label) -> cm["$k$suffix"].dbl()?.takeIf { it > 0 }?.let { PricePoint(PriceSource.CARDMARKET, AppStrings.get(label), it) } }

    private fun tcgplayerDetails(p: kotlinx.serialization.json.JsonElement?) = listOf(
        "marketPrice" to R.string.price_label_market, "lowPrice" to R.string.price_label_lowest_listing, "midPrice" to R.string.price_label_mid,
        "highPrice" to R.string.price_label_highest_listing, "directLowPrice" to R.string.price_label_direct_low,
    ).mapNotNull { (k, label) -> p[k].dbl()?.takeIf { it > 0 }?.let { PricePoint(PriceSource.TCGPLAYER, AppStrings.get(label), it) } }

    /** TCGdex's TCGplayer price keys as TCGplayer names the printings. */
    private fun printingName(key: String) = when (key) {
        "holofoil" -> "Holofoil"
        "reverse-holofoil", "reverseHolofoil" -> "Reverse Holofoil"
        "1stEditionHolofoil" -> "1st Edition Holofoil"
        "1stEditionNormal", "1stEdition" -> "1st Edition"
        "unlimitedHolofoil" -> "Unlimited Holofoil"
        else -> "Normal"
    }

    private fun firstEdition(c: kotlinx.serialization.json.JsonElement, pricing: kotlinx.serialization.json.JsonElement?): Variant {
        val detailed = c["variants_detailed"].arr().orEmpty().firstOrNull { v ->
            v["stamp"].arr().orEmpty().any { it.str() == "1st-edition" }
        }
        val p = detailed["pricing"]
        val tcg = p["tcgplayer"] ?: pricing["tcgplayer"]
        val tcgPrice = listOf("1stEditionHolofoil", "1stEditionNormal", "1stEdition", "holofoil", "normal")
            .firstNotNullOfOrNull { k -> tcg[k]["marketPrice"].dbl() }
        // Cardmarket often sells 1st Edition as the same product as Unlimited; its price is then the
        // Unlimited one, so only a separate product counts (otherwise TCGplayer's 1st Edition price is used).
        val ownProduct = p["cardmarket"]["idProduct"].str()?.takeIf { it != pricing["cardmarket"]["idProduct"].str() }
        val cmPrice = if (ownProduct == null) null else p["cardmarket"]["trend"].dbl()?.takeIf { it > 0 } ?: p["cardmarket"]["avg"].dbl()?.takeIf { it > 0 }
        val productId = detailed["thirdParty"]["tcgplayer"].str()?.toLongOrNull()
            ?: tcg?.let { t -> listOf("1stEditionHolofoil", "1stEditionNormal").firstNotNullOfOrNull { t[it]["productId"].str()?.toLongOrNull() } }
        val tcgFirst = tcg?.let { t -> listOf("1stEditionHolofoil", "1stEditionNormal", "1stEdition").firstNotNullOfOrNull { k -> t[k].takeIf { it != null } } }
        return Variant(
            "firstEdition", AppStrings.get(R.string.data_variant_first_edition), prices(cmPrice, tcgPrice), tcgplayerId = productId, tcgplayerPrinting = "1st Edition Holofoil",
            details = (if (ownProduct != null) cardmarketDetails(p["cardmarket"], "") else emptyList()) + tcgplayerDetails(tcgFirst),
        )
    }

    private fun prices(cm: Double?, tcg: Double?) = buildMap {
        cm?.let { put(PriceSource.CARDMARKET, it) }
        tcg?.let { put(PriceSource.TCGPLAYER, it) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/**
 * The picture of an English card on pokemontcg.io, for the ~7 % of cards TCGdex has no picture of
 * (trainer galleries, shiny vaults, many promos). TCGdex set ids map to theirs: "sv03.5" → "sv3pt5",
 * "swsh12.5gg" → "swsh12pt5gg", "sm7.5" → "sm75"; numbers without leading zeros. Null for other
 * languages (their ids carry a prefix such as "ja:").
 */
fun pokemonTcgImage(cardId: String, large: Boolean): String? {
    if (':' in cardId) return null
    val cut = cardId.lastIndexOf('-').takeIf { it > 0 } ?: return null
    var set = cardId.substring(0, cut).replace(Regex("^sv0(\\d)"), "sv$1")
    set = if (Regex("^(sv\\d|swsh12\\.5)").containsMatchIn(set)) set.replace(".5", "pt5") else set.replace(".", "")
    val raw = cardId.substring(cut + 1)
    val number = if (raw.all(Char::isDigit)) raw.trimStart('0').ifEmpty { "0" } else raw
    return "https://images.pokemontcg.io/$set/$number${if (large) "_hires" else ""}.png"
}

