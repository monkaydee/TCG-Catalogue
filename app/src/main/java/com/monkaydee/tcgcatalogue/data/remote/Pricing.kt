package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game

/** Chooses which market price to show for a card. */
object Pricing {
    /** Two markets further apart than this mean one of them is linked to the wrong card. */
    private const val MAX_RATIO = 3.0

    private val ordinaryRarities = setOf("common", "uncommon", "rare", "none", "c", "u", "r")

    /** Pokémon and Magic have prices from both markets; the other games only from TCGplayer. */
    fun sourceFor(game: Game, preferred: PriceSource): PriceSource =
        if (game == Game.POKEMON || game == Game.MAGIC) preferred else PriceSource.TCGPLAYER

    /**
     * The price of [variant] from the [preferred] market, cross-checked against the other one.
     *
     * TCGdex sometimes links a card to the wrong Cardmarket product when a set has two cards
     * with the same name (e.g. Zekrom 51/113 and the gold Zekrom 115/113), so a gold secret
     * rare would show the price of the common. When the markets disagree by more than 3x, the
     * cheaper price is taken for ordinary cards and the higher one for special rarities.
     */
    fun pick(variant: Variant, rarity: String?, preferred: PriceSource, usdToEur: Double): Price? {
        val cm = variant.prices[PriceSource.CARDMARKET]
        val tcg = variant.prices[PriceSource.TCGPLAYER]
        if (cm == null || tcg == null || cm <= 0 || tcg <= 0) return variant.price(preferred)
        val tcgInEur = tcg * usdToEur
        val ratio = maxOf(cm, tcgInEur) / minOf(cm, tcgInEur)
        if (ratio <= MAX_RATIO) return variant.price(preferred)
        val special = rarity?.lowercase()?.trim() !in ordinaryRarities
        val cmIsHigher = cm > tcgInEur
        val useCardmarket = if (special) cmIsHigher else !cmIsHigher
        val chosen = if (useCardmarket) PriceSource.CARDMARKET else PriceSource.TCGPLAYER
        val other = if (useCardmarket) PriceSource.TCGPLAYER else PriceSource.CARDMARKET
        return Price(
            amount = if (useCardmarket) cm else tcg,
            source = chosen,
            note = "${other.label} listing didn't match this card (%.0fx apart); using ${chosen.label}".format(ratio),
        )
    }
}
