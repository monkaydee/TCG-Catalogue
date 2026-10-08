package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardLanguage
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/** Price, printed identity and freshness stay together; full provenance is one tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardPriceSummary(card: OwnedCard, settings: AppSettings, modifier: Modifier = Modifier) {
    val valued = Money.unitOrNull(card, settings.currency, settings.usdToEur) != null
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(CardLanguage.displayCode(card.language))
                StatusBadge(if (card.graded) listOfNotNull(card.grader, card.grade, card.gradeQualifier).joinToString(" ")
                    else stringResource(R.string.add_raw) + " · " + card.condition, highlighted = card.graded)
                card.priceSource?.takeIf { it.contains("asking", ignoreCase = true) }?.let {
                    StatusBadge(stringResource(R.string.sealed_asking_reference))
                }
            }
            Text(if (valued) Money.valueText(card, settings.currency, settings.usdToEur)
                else stringResource(if (card.graded) R.string.card_no_graded_price else R.string.card_no_market_price),
                style = if (valued) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = if (valued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (card.manualPrice != null) {
                Text(stringResource(R.string.card_own_value, Money.format(Money.convert(card.manualPrice,
                    card.manualCurrency ?: settings.currency, settings.currency, settings.usdToEur), settings.currency)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            card.priceUpdatedAt?.let { Text(stringResource(R.string.card_price_updated,
                android.text.format.DateUtils.getRelativeTimeSpanString(it)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // Matching/freshness warnings remain visible even with the evidence section collapsed.
            card.priceNote?.takeIf { card.manualPrice == null && it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!card.priceSource.isNullOrBlank() || card.price != null || card.certNumber != null) {
                ExpandablePanel(stringResource(R.string.design_evidence)) {
                    Text(listOfNotNull(CardLanguage.displayCode(card.language), card.variantLabel,
                        if (card.graded) listOfNotNull(card.grader, card.grade).joinToString(" ") else card.condition).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                    card.price?.takeIf { it.isFinite() && it > 0 }?.let {
                        Text(Money.format(it, card.priceCurrency), style = MaterialTheme.typography.titleSmall)
                    }
                    card.priceSource?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    card.certNumber?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.card_cert, it), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(if (card.graded) R.string.design_slab_hint else R.string.card_raw_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
