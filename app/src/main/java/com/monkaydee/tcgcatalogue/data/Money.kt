package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    /** Currencies prices can be shown in (ECB reference rates); collection amounts stay in EUR or USD. */
    val DISPLAY = listOf("GBP", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "TRY", "JPY", "KRW", "CNY", "HKD",
        "SGD", "THB", "IDR", "PHP", "MYR", "INR", "AUD", "NZD", "CAD", "MXN", "BRL", "ZAR", "ILS")

    @Volatile private var display: String? = null
    @Volatile private var rates: Map<String, Double> = emptyMap()

    /** Shows every formatted amount in [currency] (null: as stored) using [eurRates] (units per 1 EUR). */
    fun show(currency: String?, eurRates: Map<String, Double>, usdToEur: Double) {
        rates = eurRates + ("EUR" to 1.0) + (if (usdToEur > 0) mapOf("USD" to 1 / usdToEur) else emptyMap())
        display = currency?.takeIf { it in rates }
    }

    /** [amount] in [currency] converted to the display currency, or null when no display currency or rate applies. */
    fun displayed(amount: Double, currency: String): Pair<Double, String>? {
        val target = display ?: return null
        if (target == currency) return null
        val perEurFrom = rates[currency] ?: return null
        val perEurTo = rates[target] ?: return null
        return Pair(amount / perEurFrom * perEurTo, target)
    }

    fun convert(amount: Double, from: String, to: String, usdToEur: Double): Double = when {
        from == to -> amount
        from == "USD" && to == "EUR" -> amount * usdToEur
        from == "EUR" && to == "USD" -> amount / usdToEur
        else -> amount
    }

    /** Value of all copies of [card] in [currency], 0 when no price is known. */
    fun value(card: OwnedCard, currency: String, usdToEur: Double): Double =
        unit(card, currency, usdToEur) * card.quantity

    /** Value of one copy: the user's own value if set, otherwise the market price. */
    fun unit(card: OwnedCard, currency: String, usdToEur: Double): Double = unitOrNull(card, currency, usdToEur) ?: 0.0

    /** Missing automatic quotes are unknown. An explicitly entered manual zero is valid. */
    fun unitOrNull(card: OwnedCard, currency: String, usdToEur: Double): Double? =
        card.manualPrice?.takeIf { it.isFinite() && it >= 0 }?.let { convert(it, card.manualCurrency ?: currency, currency, usdToEur) }
            ?: card.price?.takeIf { it.isFinite() && it > 0 }?.let { convert(it, card.priceCurrency, currency, usdToEur) }

    fun unitText(card: OwnedCard, currency: String, usdToEur: Double): String =
        unitOrNull(card, currency, usdToEur)?.let { format(it, currency) } ?: "—"

    fun valueText(card: OwnedCard, currency: String, usdToEur: Double): String =
        unitOrNull(card, currency, usdToEur)?.let { format(it * card.quantity, currency) } ?: "—"

    data class Coverage(val amount: Double?, val missingCopies: Int) {
        fun text(currency: String): String = amount?.let { format(it, currency) } ?: "—"
    }

    fun coverage(cards: List<OwnedCard>, currency: String, usdToEur: Double,
                 sealed: List<com.monkaydee.tcgcatalogue.data.db.SealedItem> = emptyList()): Coverage {
        val cardValues = cards.map { unitOrNull(it, currency, usdToEur)?.times(it.quantity) }
        val sealedValues = sealed.map { row -> row.price?.takeIf { it.isFinite() && it > 0 }?.let { convert(it, row.priceCurrency, currency, usdToEur) * row.quantity } }
        val known = (cardValues + sealedValues).filterNotNull()
        val missing = cards.zip(cardValues).sumOf { (row, value) -> if (value == null) row.quantity else 0 } +
            sealed.zip(sealedValues).sumOf { (row, value) -> if (value == null) row.quantity else 0 }
        return Coverage(if (known.isEmpty() && missing > 0) null else known.sum(), missing)
    }

    /** Value of all items of a sealed product in [currency]. */
    fun sealedValue(item: com.monkaydee.tcgcatalogue.data.db.SealedItem, currency: String, usdToEur: Double): Double =
        (item.price?.let { convert(it, item.priceCurrency, currency, usdToEur) } ?: 0.0) * item.quantity

    fun format(amount: Double, currency: String): String {
        val (value, shown) = displayed(amount, currency) ?: (amount to currency)
        val f = NumberFormat.getCurrencyInstance(Locale.getDefault())
        f.currency = Currency.getInstance(shown)
        return f.format(value)
    }
}
