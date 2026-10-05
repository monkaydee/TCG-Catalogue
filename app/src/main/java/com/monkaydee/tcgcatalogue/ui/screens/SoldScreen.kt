package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.SoldCard
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.ListEmptyState
import com.monkaydee.tcgcatalogue.ui.components.ListSummaryCard
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.components.signedMoney
import com.monkaydee.tcgcatalogue.ui.theme.Gain
import com.monkaydee.tcgcatalogue.ui.theme.Loss
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Sale price per copy in the display currency. */
private fun SoldCard.saleIn(s: AppSettings) = Money.convert(salePrice, saleCurrency, s.currency, s.usdToEur)

/** Purchase price per copy in the display currency, null when it wasn't entered. */
private fun SoldCard.paidIn(s: AppSettings) = purchasePrice?.let { Money.convert(it, purchaseCurrency, s.currency, s.usdToEur) }

/** Profit of the whole entry (all copies) in the display currency, null without a purchase price. */
private fun SoldCard.profitIn(s: AppSettings) = (totalBasis?.let { Money.convert(it, saleCurrency, s.currency, s.usdToEur) } ?: paidIn(s)?.takeIf { condition in setOf("NM", "LP", "MP", "HP", "DMG") }?.times(quantity))?.let { saleIn(s) * quantity - it - Money.convert(saleFees, saleCurrency, s.currency, s.usdToEur) }

/**
 * Cards the user sold: sale price, purchase price and profit per entry, and the realised profit
 * of all sales whose purchase price is known. Entries can be deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoldScreen(repo: CardRepository, onBack: () -> Unit) {
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val list by repo.sold.collectAsState(initial = null)
    var deleting by remember { mutableStateOf<SoldCard?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.sold_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.lists_back)) } },
            )
        },
    ) { padding ->
        val sold = list ?: return@Scaffold
        if (sold.isEmpty()) {
            ListEmptyState(
                Icons.Default.Paid,
                stringResource(R.string.sold_empty_title),
                stringResource(R.string.sold_empty_text),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        val sorted = sold.sortedByDescending { it.soldAt }
        val profit = sold.sumOf { it.profitIn(s) ?: 0.0 }
        val revenue = sold.sumOf { it.saleIn(s) * it.quantity }
        val unknown = sold.count { it.profitIn(s) == null }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListSummaryCard(
                    stringResource(R.string.sold_realised),
                    signedMoney(profit, s.currency),
                    amountColor = if (profit >= 0) Gain else Loss,
                ) {
                    Text(stringResource(R.string.sold_revenue, Money.format(revenue, s.currency)), style = MaterialTheme.typography.bodySmall)
                    if (unknown > 0) {
                        Text(pluralStringResource(R.plurals.sold_unknown_cost, unknown, unknown), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(sorted, key = { it.id }) { c -> SoldRow(c, s, onDelete = { deleting = c }) }
        }
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.sold_delete_title)) },
            text = { Text(stringResource(R.string.sold_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repo.deleteSold(c) }
                }) { Text(stringResource(R.string.lists_remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.lists_cancel)) } },
        )
    }
}

@Composable
private fun SoldRow(c: SoldCard, s: AppSettings, onDelete: () -> Unit) {
    val paid = c.paidIn(s)
    val profit = c.profitIn(s)
    val date = remember(c.soldAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.soldAt)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardImage(c.imageUrl, Modifier.width(52.dp), thumb = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.setName} · ${c.number} · ${c.condition}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.sold_sale_line, c.quantity, Money.format(c.saleIn(s), s.currency)), style = MaterialTheme.typography.labelMedium)
                Text(
                    paid?.let { stringResource(R.string.sold_paid_line, Money.format(it, s.currency)) } ?: stringResource(R.string.sold_paid_unknown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.sold_on, date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    profit?.let { signedMoney(it, s.currency) } ?: Money.format(c.saleIn(s) * c.quantity, s.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        profit == null -> MaterialTheme.colorScheme.onSurface
                        profit >= 0 -> Gain
                        else -> Loss
                    },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.sold_delete), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
