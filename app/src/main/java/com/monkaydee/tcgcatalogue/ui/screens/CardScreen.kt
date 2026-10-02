package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.components.CONDITIONS
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.QuantityStepper
import kotlinx.coroutines.launch

/**
 * The list a card was opened from (a set sorted by value, the most valuable cards, ...), so the
 * card screen can swipe to the previous and next card in the same order.
 */
object CardBrowse {
    @Volatile
    var ids: List<Long> = emptyList()

    fun open(ids: List<Long>, id: Long, navigate: (Long) -> Unit) {
        this.ids = ids
        navigate(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(repo: CardRepository, id: Long, onBack: () -> Unit) {
    val all by repo.cards.collectAsState(initial = null)
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val order = remember(id) { CardBrowse.ids.takeIf { id in it } ?: listOf(id) }
    // Cards removed meanwhile drop out of the pager.
    val cards = all?.associateBy { it.id }?.let { byId -> order.mapNotNull { byId[it] } }.orEmpty()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(all, cards.size) {
        if (all != null && cards.isEmpty()) onBack()
    }
    val pager = rememberPagerState(initialPage = order.indexOf(id).coerceAtLeast(0)) { cards.size }
    val current = cards.getOrNull(pager.currentPage.coerceAtMost(cards.lastIndex))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (cards.size > 1) {
                            Text("${pager.currentPage + 1} / ${cards.size} · swipe for more", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Remove from collection") } },
            )
        },
    ) { padding ->
        if (cards.isEmpty()) return@Scaffold
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(padding).fillMaxSize(),
            key = { cards[it].id },
            beyondViewportPageCount = 1,
        ) { page ->
            CardDetail(cards[page], s, repo)
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove card?") },
            text = { Text("This removes all ${current.quantity} copies of ${current.name} from your collection.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.delete(current) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetail(c: OwnedCard, s: AppSettings, repo: CardRepository) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardOrSlab(c, Modifier.fillMaxWidth(if (c.graded) 0.8f else 0.75f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${c.setName} · ${c.number}", style = MaterialTheme.typography.bodyMedium)
            Text(listOfNotNull(c.game.label, c.rarity, c.variantLabel).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text(
                Money.format(Money.value(c, s.currency, s.usdToEur), s.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (c.price != null) {
                Text(
                    "${Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency)} per copy" +
                        " (${Money.format(c.price, c.priceCurrency)} on ${c.priceSource ?: "market"})",
                    style = MaterialTheme.typography.bodySmall,
                )
                c.priceUpdatedAt?.let {
                    Text("Updated ${android.text.format.DateUtils.getRelativeTimeSpanString(it)}", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text("No market price available", style = MaterialTheme.typography.bodySmall)
            }
            c.priceNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            if (c.graded) {
                Text(
                    listOfNotNull(c.condition, c.certNumber?.let { "Cert #$it" }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text("Graded price from sold listings (PriceCharting, otherwise the last 5 eBay sales).", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Raw-card market price for this condition.", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("Quantity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
        QuantityStepper(c.quantity, { q -> scope.launch { repo.update(c.copy(quantity = q)) } })
        if (!c.graded) {
            Text("Condition", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITIONS.forEach { cond ->
                    FilterChip(cond == c.condition, {
                        scope.launch { runCatching { repo.changeCondition(c, cond) } }
                    }, { Text(cond) })
                }
            }
        }
    }
}
