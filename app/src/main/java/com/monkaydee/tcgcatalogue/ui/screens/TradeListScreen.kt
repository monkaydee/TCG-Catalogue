package com.monkaydee.tcgcatalogue.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.ListEmptyState
import com.monkaydee.tcgcatalogue.ui.components.ListSummaryCard
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import kotlinx.coroutines.launch

/**
 * The cards offered for trade (marked "For trade" on the card page), with their value and
 * condition, and a "Share list" button that sends the list as plain text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeListScreen(repo: CardRepository, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val all by repo.cards.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trade = all?.filter { it.forTrade }?.sortedByDescending { Money.unit(it, s.currency, s.usdToEur) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.trade_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.lists_back)) } },
                actions = {
                    if (!trade.isNullOrEmpty()) {
                        IconButton(onClick = { shareTradeList(context, trade, s) }) { Icon(Icons.Default.Share, stringResource(R.string.trade_share)) }
                    }
                },
            )
        },
    ) { padding ->
        val cards = trade ?: return@Scaffold
        if (cards.isEmpty()) {
            ListEmptyState(
                Icons.Default.SwapHoriz,
                stringResource(R.string.trade_empty_title),
                stringResource(R.string.trade_empty_text),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListSummaryCard(stringResource(R.string.trade_total), Money.format(cards.sumOf { Money.value(it, s.currency, s.usdToEur) }, s.currency)) {
                    val copies = cards.sumOf { it.quantity }
                    Text(pluralStringResource(R.plurals.home_cards, copies, copies), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = { shareTradeList(context, cards, s) }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.trade_share))
                    }
                }
            }
            items(cards, key = { it.id }) { c ->
                TradeRow(
                    c, s,
                    onClick = { CardBrowse.open(cards.map { it.id }, c.id, onOpenCard) },
                    onRemove = { scope.launch { repo.setForTrade(c, false) } },
                )
            }
        }
    }
}

@Composable
private fun TradeRow(c: OwnedCard, s: AppSettings, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardOrSlab(c, Modifier.width(if (c.graded) 60.dp else 52.dp), thumb = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.setName} · ${c.number}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${c.variantLabel} · ${c.condition}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(Money.value(c, s.currency, s.usdToEur), s.currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (c.quantity > 1) {
                    Text("${c.quantity} × ${Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency)}", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.RemoveCircleOutline, stringResource(R.string.trade_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The trade list as plain text: name, set, number, condition or grade and value of each card. */
private fun tradeListText(cards: List<OwnedCard>, s: AppSettings): String = buildString {
    val copies = cards.sumOf { it.quantity }
    appendLine(AppStrings.context().resources.getQuantityString(R.plurals.trade_share_heading, copies, copies))
    appendLine()
    cards.forEach { c ->
        val value = Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency)
        appendLine(
            AppStrings.get(
                R.string.trade_share_line,
                if (c.quantity > 1) "${c.quantity}× " else "",
                c.name, c.setName, c.number,
                listOf(c.variantLabel, c.condition).filter { it.isNotBlank() }.joinToString(" · "),
                value,
            ),
        )
    }
    appendLine()
    appendLine(AppStrings.get(R.string.trade_share_total, Money.format(cards.sumOf { Money.value(it, s.currency, s.usdToEur) }, s.currency)))
    append(AppStrings.get(R.string.trade_share_footer, AppStrings.get(R.string.app_name)))
}

private fun shareTradeList(context: Context, cards: List<OwnedCard>, s: AppSettings) {
    val subject = AppStrings.get(R.string.trade_share_subject)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, tradeListText(cards, s))
    runCatching { context.startActivity(Intent.createChooser(send, subject)) }
}
