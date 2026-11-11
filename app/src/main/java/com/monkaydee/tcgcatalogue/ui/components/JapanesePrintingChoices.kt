package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate

/** Native product references are visible before selection; names alone never pick a printing. */
@Composable
fun JapanesePrintingChoices(options: List<CardCandidate>, selected: String?, settings: AppSettings,
                            repo: CardRepository, onSelect: (CardCandidate) -> Unit) {
    Text(stringResource(R.string.jp_printing_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.jp_printing_match_number), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options, key = { it.cardId }) { card ->
            OutlinedCard(onClick = { onSelect(card) }, modifier = Modifier.width(218.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = if (selected == card.cardId)
                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${card.setId.substringAfter(':')} · ${card.number}", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CardImage(card.imageUrl, Modifier.width(46.dp), thumb = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(card.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(card.setName, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            val reference = repo.rawPrice(card, card.defaultVariant, settings)
                            Text(reference?.let { Money.format(Money.convert(it.amount, it.currency, settings.currency, settings.usdToEur), settings.currency) }
                                ?: stringResource(R.string.add_no_price), color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(stringResource(R.string.jp_printing_select), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    Text(stringResource(R.string.jp_printing_price_reference), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
