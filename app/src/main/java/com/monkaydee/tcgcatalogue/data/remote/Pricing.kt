package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlin.math.roundToInt

/**
 * The sentences of the price notes [Pricing] writes. The app passes [App] (its chosen language);
 * the default [English] keeps [Pricing] usable without Android resources (unit tests).
 */
interface PriceTexts {
    /** [other]'s listing didn't match the card, the markets being [times] apart; [chosen]'s price is used. */
    fun marketsDisagree(other: String, times: Int, chosen: String): String
    /** [condition] got the price of the better [better] because its own sales data was higher. */
    fun capped(condition: String, better: String): String
    fun tcgplayerSales(condition: String): String
    fun salesShare(condition: String, percent: Int): String
    fun estimatedShare(condition: String, percent: Int): String

    object English : PriceTexts {
        override fun marketsDisagree(other: String, times: Int, chosen: String) = "$other listing didn't match this card (${times}x apart); using $chosen"
        override fun capped(condition: String, better: String) = "$condition valued like $better (its sales data was higher)"
        override fun tcgplayerSales(condition: String) = "$condition price from TCGplayer sales"
        override fun salesShare(condition: String, percent: Int) = "$condition = $percent% of NM (TCGplayer sales)"
        override fun estimatedShare(condition: String, percent: Int) = "$condition estimated at $percent% of NM (no sales data)"
    }

    object App : PriceTexts {
        override fun marketsDisagree(other: String, times: Int, chosen: String) = AppStrings.get(R.string.price_note_markets_disagree, other, times, chosen)
        override fun capped(condition: String, better: String) = AppStrings.get(R.string.price_note_capped, condition, better)
        override fun tcgplayerSales(condition: String) = AppStrings.get(R.string.price_note_tcgplayer_sales, condition)
        override fun salesShare(condition: String, percent: Int) = AppStrings.get(R.string.price_note_sales_share, condition, percent)
        override fun estimatedShare(condition: String, percent: Int) = AppStrings.get(R.string.price_note_estimated_share, condition, percent)
    }
}

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
    fun pick(variant: Variant, rarity: String?, preferred: PriceSource, usdToEur: Double, texts: PriceTexts = PriceTexts.English): Price? {
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
            note = texts.marketsDisagree(other.label, ratio.roundToInt(), chosen.label),
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
    fun forCondition(base: Price?, condition: String, byCondition: Map<String, Double>?, texts: PriceTexts = PriceTexts.English): Price? {
        if (base == null) return single(null, condition, byCondition, texts)
        if (condition == "NM") return base
        var cap = base?.amount ?: byCondition?.get("Near Mint")
        var better = "NM"
        for (c in CONDITION_ORDER.drop(1)) {
            val p = single(base, c, byCondition, texts) ?: return null
            val capped = if (cap != null && p.amount > cap) {
                p.copy(amount = cap, note = listOfNotNull(base?.note, texts.capped(c, better)).joinToString(" · "))
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

    private fun single(base: Price?, condition: String, byCondition: Map<String, Double>?, texts: PriceTexts): Price? {
        val name = TcgPlayerApi.CONDITION_NAMES[condition] ?: return base
        val nm = byCondition?.get("Near Mint")
        val actual = byCondition?.get(name)
        if (base == null) {
            return actual?.let { Price(minOf(it, nm ?: it), PriceSource.TCGPLAYER, texts.tcgplayerSales(condition)) }
        }
        fun note(text: String) = listOfNotNull(base.note, text).joinToString(" · ")
        if (actual != null && base.source == PriceSource.TCGPLAYER) {
            return Price(minOf(actual, nm ?: actual), PriceSource.TCGPLAYER, base.note)
        }
        if (actual != null && nm != null && nm > 0) {
            val factor = (actual / nm).coerceIn(0.05, 1.0)
            return base.copy(amount = base.amount * factor, note = note(texts.salesShare(condition, (factor * 100).roundToInt())))
        }
        val factor = TcgPlayerApi.DEFAULT_FACTORS.getValue(condition)
        return base.copy(amount = base.amount * factor, note = note(texts.estimatedShare(condition, (factor * 100).roundToInt())))
    }
}
