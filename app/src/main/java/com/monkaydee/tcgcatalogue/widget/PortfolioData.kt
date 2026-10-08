package com.monkaydee.tcgcatalogue.widget

import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.data.db.SealedItem

/** What the Portfolio widget shows. [change] is null while there is no snapshot older than today to compare with. */
data class PortfolioData(
    val value: Double,
    val currency: String,
    /** Value now minus the value of the oldest snapshot of the last [DAYS] days. */
    val change: Double?,
    /** [change] relative to that older value, null when it was zero. */
    val changePercent: Double?,
    val cardCount: Int,
    /** When the prices were last refreshed (or when the widget was built if never). */
    val updatedAt: Long,
    val missingCopies: Int = 0,
    val hasKnownValue: Boolean = true,
    val sealedCount: Int = 0,
) {
    companion object {
        const val DAYS = 30L

        fun compute(
            cards: List<OwnedCard>,
            sealed: List<SealedItem>,
            snapshots: List<PortfolioSnapshot>,
            settings: AppSettings,
            today: Long,
            now: Long,
        ): PortfolioData {
            val currency = settings.currency
            val rate = settings.usdToEur
            val value = cards.sumOf { Money.value(it, currency, rate) } + sealed.sumOf { Money.sealedValue(it, currency, rate) }
            val coverage = Money.coverage(cards, currency, rate, sealed)
            val base = snapshots.filter { it.day in (today - DAYS) until today }.minByOrNull { it.day }
            val before = base?.let { if (currency == "EUR") it.valueEur else it.valueUsd }
            return PortfolioData(
                value = value,
                currency = currency,
                change = before?.takeIf { coverage.missingCopies == 0 }?.let { value - it },
                changePercent = before?.takeIf { it > 0.0 && coverage.missingCopies == 0 }?.let { (value - it) / it * 100.0 },
                cardCount = cards.sumOf { it.quantity },
                updatedAt = settings.lastPriceRefresh.takeIf { it > 0 } ?: now,
                missingCopies = coverage.missingCopies,
                hasKnownValue = coverage.amount != null,
                sealedCount = sealed.sumOf { it.quantity },
            )
        }
    }
}
