package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
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
    fun unit(card: OwnedCard, currency: String, usdToEur: Double): Double =
        card.manualPrice?.let { convert(it, card.manualCurrency ?: currency, currency, usdToEur) }
            ?: card.price?.let { convert(it, card.priceCurrency, currency, usdToEur) }
            ?: 0.0

    /** Value of all items of a sealed product in [currency]. */
    fun sealedValue(item: com.monkaydee.tcgcatalogue.data.db.SealedItem, currency: String, usdToEur: Double): Double =
        (item.price?.let { convert(it, item.priceCurrency, currency, usdToEur) } ?: 0.0) * item.quantity

    fun format(amount: Double, currency: String): String {
        val f = NumberFormat.getCurrencyInstance(Locale.getDefault())
        f.currency = Currency.getInstance(currency)
        return f.format(amount)
    }
}
