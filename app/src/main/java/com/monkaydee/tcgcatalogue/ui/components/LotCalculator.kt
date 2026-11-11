package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.LotValue
import com.monkaydee.tcgcatalogue.data.Money

/**
 * Lot calculator: the market value of some cards (and amounts typed in by hand, e.g. someone
 * else's cards at a show) and what a lot costs at a chosen share of it.
 */
@Composable
fun LotCalculator(initial: List<Pair<String, Double?>>, currency: String, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf(initial) }
    var percent by remember { mutableIntStateOf(70) }
    var entry by remember { mutableStateOf("") }
    val result = LotValue.of(items.map { it.second }, percent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lot_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    itemsIndexed(items) { i, (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(value?.let { Money.format(it, currency) } ?: "—", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { items = items.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Outlined.Close, stringResource(R.string.lot_remove)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(entry, { v -> entry = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9) }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.lot_add_value, currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    TextButton(onClick = {
                        entry.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { v ->
                            items = items + (Money.format(v, currency) to v); entry = ""
                        }
                    }) { Text(stringResource(R.string.lot_add)) }
                }
                Text(stringResource(R.string.lot_market, Money.format(result.market, currency), items.size), style = MaterialTheme.typography.bodyMedium)
                if (result.unknown > 0) Text(stringResource(R.string.lot_unknown, result.unknown), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.lot_percent, percent), style = MaterialTheme.typography.labelLarge)
                Slider(percent.toFloat(), { percent = (it / 5).toInt() * 5 }, valueRange = 30f..120f, steps = 17)
                Text(stringResource(R.string.lot_offer, Money.format(result.offer, currency)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}
