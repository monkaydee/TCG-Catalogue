package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.data.remote.Variant

val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")

fun Variant.displayPrice(game: Game, s: AppSettings): String {
    val source = if (game == Game.POKEMON) s.pokemonSource else PriceSource.TCGPLAYER
    val p = price(source) ?: return "no price"
    return Money.format(Money.convert(p.amount, p.currency, s.currency, s.usdToEur), s.currency)
}

/** Lets the user confirm the recognised card, pick the printing, condition and quantity. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(
    candidates: List<CardCandidate>,
    settings: AppSettings,
    onAdd: (CardCandidate, Variant, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (candidates.isEmpty()) return
    var selected by remember(candidates) { mutableIntStateOf(0) }
    val card = candidates[selected.coerceIn(candidates.indices)]
    var variantKey by remember(card) { mutableStateOf(card.variants.first().key) }
    val variant = card.variants.firstOrNull { it.key == variantKey } ?: card.variants.first()
    var quantity by remember(card) { mutableIntStateOf(1) }
    var condition by remember { mutableStateOf(settings.defaultCondition) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (candidates.size > 1) {
                Text("${candidates.size} possible matches — tap the right one", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(candidates) { i, c ->
                        Card(
                            Modifier.width(96.dp).clickable { selected = i },
                            border = if (i == selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(),
                        ) {
                            CardImage(c.imageUrl, thumb = true)
                            Text(c.setName, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardImage(variant.imageUrl ?: card.imageUrl, Modifier.width(140.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(card.setName, style = MaterialTheme.typography.bodyMedium)
                    Text("${card.number}${card.rarity?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(variant.displayPrice(card.game, settings), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("per copy · ${variant.label}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (card.variants.size > 1) {
                Text("Printing", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.variants.forEach { v ->
                        FilterChip(
                            selected = v.key == variant.key,
                            onClick = { variantKey = v.key },
                            label = { Text("${v.label} · ${v.displayPrice(card.game, settings)}") },
                        )
                    }
                }
            }
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITIONS.forEach { c -> FilterChip(selected = c == condition, onClick = { condition = c }, label = { Text(c) }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                QuantityStepper(quantity, { quantity = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onAdd(card, variant, quantity, condition) }) { Text("Add") }
                }
            }
        }
    }
}
