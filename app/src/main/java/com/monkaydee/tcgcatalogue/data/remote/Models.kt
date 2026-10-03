package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game

enum class PriceSource(val label: String, val currency: String) {
    CARDMARKET("Cardmarket", "EUR"),
    TCGPLAYER("TCGplayer", "USD"),
}

data class Price(val amount: Double, val source: PriceSource, val note: String? = null) {
    val currency: String get() = source.currency
}

/** One number a market publishes for a card ("Cardmarket · 30-day avg"), for the price overview. */
data class PricePoint(val source: PriceSource, val label: String, val amount: Double)

/** A printing of a card (normal / holo / reverse / alt art ...) with its market prices. */
data class Variant(
    val key: String,
    val label: String,
    val prices: Map<PriceSource, Double>,
    /** Overrides the card image when the variant looks different (One Piece alt arts). */
    val imageUrl: String? = null,
    /** TCGplayer product of this printing, for per-condition prices. */
    val tcgplayerId: Long? = null,
    /** TCGplayer's name for the printing: "Normal", "Holofoil", "Reverse Holofoil", "Foil", ... */
    val tcgplayerPrinting: String? = null,
    /** Everything the markets publish for this printing (trend, averages, lowest offer ...). */
    val details: List<PricePoint> = emptyList(),
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
    /** Shown in the add sheet, e.g. when the name on the scan doesn't match this card. */
    val warning: String? = null,
    /** Pokémon: the Cardmarket product TCGdex links this card to (checked against look-alikes). */
    val cardmarketId: Long? = null,
    /** Pokémon: attack names, which tell look-alike cards of a set apart on Cardmarket. */
    val attacks: List<String> = emptyList(),
    /** The printing the scan recognised (1st Edition stamp, alt art picture), preselected when adding. */
    val preferredVariant: String? = null,
) {
    /** The printing to preselect: the recognised one, otherwise the first. */
    val defaultVariant: Variant get() = variants.firstOrNull { it.key == preferredVariant } ?: variants.first()
}

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

data class SetSummary(
    val id: String,
    val name: String,
    val official: Int,
    val total: Int,
    val logoUrl: String?,
    /** The set code printed on the card since Scarlet & Violet ("PAL", "MEW"). */
    val abbreviation: String? = null,
)
