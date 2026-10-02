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

    /**
     * Adjusts the Near Mint [base] price to [condition] using TCGplayer's market prices per
     * condition for this card ([byCondition], keyed "Near Mint", "Lightly Played", ...).
     *
     * - TCGplayer as the source: the real market price for that condition.
     * - Cardmarket as the source (it has no per-condition prices): the Cardmarket price times
     *   TCGplayer's ratio between that condition and Near Mint for the same card.
     * - No sales data for the condition: a typical discount (LP 85 %, MP 70 %, HP 50 %, DMG 35 %).
     *
     * Prices never go up as the condition gets worse (single odd sales on low-volume cards can
     * put an LP above NM or an HP above MP).
     */
    fun forCondition(base: Price?, condition: String, byCondition: Map<String, Double>?): Price? {
        if (condition == "NM") return base
        var cap = base?.amount ?: byCondition?.get("Near Mint")
        var better = "NM"
        for (c in CONDITION_ORDER.drop(1)) {
            val p = single(base, c, byCondition) ?: return null
            val capped = if (cap != null && p.amount > cap) {
                p.copy(amount = cap, note = listOfNotNull(base?.note, "$c valued like $better (its sales data was higher)").joinToString(" · "))
            } else {
                p
            }
            cap = capped.amount
            better = c
            if (c == condition) return capped
        }
        return base
    }

    private val CONDITION_ORDER = listOf("NM", "LP", "MP", "HP", "DMG")

    private fun single(base: Price?, condition: String, byCondition: Map<String, Double>?): Price? {
        val name = TcgPlayerApi.CONDITION_NAMES[condition] ?: return base
        val nm = byCondition?.get("Near Mint")
        val actual = byCondition?.get(name)
        if (base == null) {
            return actual?.let { Price(minOf(it, nm ?: it), PriceSource.TCGPLAYER, "$condition price from TCGplayer sales") }
        }
        fun note(text: String) = listOfNotNull(base.note, text).joinToString(" · ")
        if (actual != null && base.source == PriceSource.TCGPLAYER) {
            return Price(minOf(actual, nm ?: actual), PriceSource.TCGPLAYER, base.note)
        }
        if (actual != null && nm != null && nm > 0) {
            val factor = (actual / nm).coerceIn(0.05, 1.0)
            return base.copy(amount = base.amount * factor, note = note("$condition = %.0f%% of NM (TCGplayer sales)".format(factor * 100)))
        }
        val factor = TcgPlayerApi.DEFAULT_FACTORS.getValue(condition)
        return base.copy(amount = base.amount * factor, note = note("$condition estimated at %.0f%% of NM (no sales data)".format(factor * 100)))
    }
}
