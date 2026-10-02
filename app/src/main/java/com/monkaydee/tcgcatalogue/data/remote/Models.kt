package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game

enum class PriceSource(val label: String, val currency: String) {
    CARDMARKET("Cardmarket", "EUR"),
    TCGPLAYER("TCGplayer", "USD"),
    PRICECHARTING("PriceCharting", "USD"),
}

data class Price(val amount: Double, val source: PriceSource, val note: String? = null) {
    val currency: String get() = source.currency
}

/** A printing of a card (normal / holo / reverse / alt art ...) with its market prices. */
data class Variant(
    val key: String,
    val label: String,
    val prices: Map<PriceSource, Double>,
    /** Overrides the card image when the variant looks different (One Piece alt arts). */
    val imageUrl: String? = null,
) {
    /** The price from [preferred] if known, otherwise any other source. See [Pricing] for the checked version. */
    fun price(preferred: PriceSource): Price? =
        prices[preferred]?.let { Price(it, preferred) }
            ?: prices.entries.firstOrNull()?.let { Price(it.value, it.key) }
}

/** A card the API returned for a scan or search, not yet in the collection. */
data class CardCandidate(
    val game: Game,
    val cardId: String,
    val name: String,
    val number: String,
    val setId: String,
    val setName: String,
    val setTotal: Int,
    val rarity: String?,
    val imageUrl: String?,
    val variants: List<Variant>,
    /** How well this candidate matches what the camera read, higher is better. */
    val score: Double = 0.0,
)

/** Brief search result; details are fetched when the user picks it unless already known ([candidate]). */
data class CardBrief(
    val game: Game,
    val cardId: String,
    val name: String,
    val number: String,
    val imageUrl: String?,
    val candidate: CardCandidate? = null,
)

fun CardCandidate.toBrief() = CardBrief(game, cardId, name, number, imageUrl, this)

data class SetSummary(val id: String, val name: String, val official: Int, val total: Int, val logoUrl: String?)
