package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
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
import com.monkaydee.tcgcatalogue.ui.components.PriceLinks
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
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
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.ui.components.CONDITIONS
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.QuantityStepper
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.monkaydee.tcgcatalogue.R

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
fun CardScreen(repo: CardRepository, id: Long, onBack: () -> Unit, onReplace: (Long) -> Unit = {}) {
    val all by repo.cards.collectAsState(initial = null)
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val order = remember(id) { CardBrowse.ids.takeIf { id in it } ?: listOf(id) }
    // Cards removed meanwhile drop out of the pager.
    val cards = all?.associateBy { it.id }?.let { byId -> order.mapNotNull { byId[it] } }.orEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    // Edit: the card's current data from the API, so printings and prices can be changed.
    var editing by remember { mutableStateOf<OwnedCard?>(null) }
    var editCandidate by remember { mutableStateOf<CardCandidate?>(null) }
    var editLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(editing) {
        val card = editing ?: return@LaunchedEffect
        editLoading = true
        editCandidate = repo.candidateFor(card)
        editLoading = false
        if (editCandidate == null) {
            Toast.makeText(context, context.getString(R.string.card_load_failed), Toast.LENGTH_LONG).show()
            editing = null
        }
    }

    LaunchedEffect(all, cards.size) {
        if (all != null && cards.isEmpty()) onBack()
    }
    val pager = rememberPagerState(initialPage = order.indexOf(id).coerceAtLeast(0)) { cards.size }
    val current = cards.getOrNull(pager.currentPage.coerceAtMost(cards.lastIndex))

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = {
                    Column {
                        Text(current?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (cards.size > 1) {
                            Text(stringResource(R.string.card_pager_position, pager.currentPage + 1, cards.size), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.card_back)) } },
                actions = {
                    if (editLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { editing = current }) { Icon(Icons.Default.Edit, stringResource(R.string.card_edit)) }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.card_remove_from_collection)) }
                },
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

    val toEdit = editing
    val candidate = editCandidate
    if (toEdit != null && candidate != null) {
        AddCardSheet(
            candidates = listOf(candidate),
            settings = s,
            repo = repo,
            initial = toEdit,
            confirmLabel = stringResource(R.string.card_save),
            onChangeCard = {
                editing = null
                editCandidate = null
                onReplace(toEdit.id)
            },
            onAdd = { r ->
                editing = null
                editCandidate = null
                scope.launch { repo.saveEdit(toEdit, r) }
            },
            onDismiss = { editing = null; editCandidate = null },
        )
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.card_remove_title)) },
            text = { Text(pluralStringResource(R.plurals.card_remove_message, current.quantity, current.quantity, current.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.delete(current) }
                }) { Text(stringResource(R.string.card_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.card_cancel)) } },
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
            c.marketLabel?.let { Text(stringResource(R.string.card_cardmarket_label, it), style = MaterialTheme.typography.bodySmall) }
            Text(
                Money.format(Money.value(c, s.currency, s.usdToEur), s.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (c.price != null) {
                val unit = Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency)
                val original = Money.format(c.price, c.priceCurrency)
                Text(
                    c.priceSource?.let { stringResource(R.string.card_unit_price_on_source, unit, original, it) }
                        ?: stringResource(R.string.card_unit_price_on_market, unit, original),
                    style = MaterialTheme.typography.bodySmall,
                )
                c.priceUpdatedAt?.let {
                    Text(stringResource(R.string.card_price_updated, android.text.format.DateUtils.getRelativeTimeSpanString(it)), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(stringResource(R.string.card_no_market_price), style = MaterialTheme.typography.bodySmall)
            }
            c.manualPrice?.let {
                val own = Money.format(Money.convert(it, c.manualCurrency ?: s.currency, s.currency, s.usdToEur), s.currency)
                Text(
                    c.price?.let { m -> stringResource(R.string.card_own_value_with_market, own, Money.format(Money.convert(m, c.priceCurrency, s.currency, s.usdToEur), s.currency)) }
                        ?: stringResource(R.string.card_own_value, own),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            c.priceNote?.takeIf { c.manualPrice == null }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            c.purchasePrice?.let { paid ->
                val paidShown = Money.convert(paid, c.priceCurrency, s.currency, s.usdToEur)
                val now = Money.unit(c, s.currency, s.usdToEur)
                val diff = now - paidShown
                val paidText = Money.format(paidShown, s.currency)
                val diffText = (if (diff >= 0) "+" else "") + Money.format(diff, s.currency)
                Text(
                    if (paidShown > 0) stringResource(R.string.card_bought_for_with_percent, paidText, diffText, diff / paidShown * 100)
                    else stringResource(R.string.card_bought_for, paidText, diffText),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diff >= 0) com.monkaydee.tcgcatalogue.ui.theme.Gain else com.monkaydee.tcgcatalogue.ui.theme.Loss,
                )
            }
            if (c.graded) {
                Text(
                    listOfNotNull(c.condition, c.certNumber?.let { stringResource(R.string.card_cert, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(stringResource(R.string.card_graded_hint), style = MaterialTheme.typography.labelSmall)
            } else {
                Text(stringResource(R.string.card_raw_hint), style = MaterialTheme.typography.labelSmall)
            }
        }
        PriceLinks(c, s)
        PriceOverview(c, s, repo)
        Text(stringResource(R.string.card_quantity), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
        QuantityStepper(c.quantity, { q -> scope.launch { repo.update(c.copy(quantity = q)) } })
        if (c.game == Game.ONE_PIECE && s.pokemonSource == PriceSource.CARDMARKET) {
            var listings by remember(c.id) { mutableStateOf<List<CardmarketApi.Listing>>(emptyList()) }
            LaunchedEffect(c.id) { listings = repo.cardmarketListings(c) }
            if (listings.isNotEmpty()) {
                Text(stringResource(R.string.card_cardmarket_listing), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listings.forEach { l ->
                        FilterChip(l.productId == c.marketProductId, {
                            scope.launch { runCatching { repo.changeListing(c, l) } }
                        }, { Text("${l.label} · ${l.price?.let { Money.format(Money.convert(it, "EUR", s.currency, s.usdToEur), s.currency) } ?: "–"}") })
                    }
                }
            }
        }
        if (!c.graded) {
            Text(stringResource(R.string.card_condition), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
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

/** All prices from all sources for this card, loaded on request (it queries several sites). */
@Composable
private fun PriceOverview(c: OwnedCard, s: AppSettings, repo: CardRepository) {
    var groups by remember(c.id) { mutableStateOf<List<CardRepository.PriceGroup>?>(null) }
    var loading by remember(c.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.card_all_prices), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        loading = true
                        scope.launch {
                            groups = repo.priceOverview(c)
                            loading = false
                        }
                    }) { Text(stringResource(if (groups == null) R.string.card_show else R.string.card_refresh)) }
                }
            }
            groups?.forEach { g ->
                HorizontalDivider()
                Text(g.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                g.lines.forEach { p ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(p.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        val converted = Money.convert(p.amount, p.source.currency, s.currency, s.usdToEur)
                        Text(
                            Money.format(converted, s.currency) +
                                if (p.source.currency != s.currency) "  (${Money.format(p.amount, p.source.currency)})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                g.problem?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            }
        }
    }
}

/**
 * Opens this card on eBay (sold listings), PriceCharting, Cardmarket and TCGplayer in the phone's
 * browser, to check prices by hand and enter your own value (pencil -> "My value").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceLinks(c: OwnedCard, s: AppSettings) {
    val open = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.card_check_prices), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceLinks.forCard(c, s.currency).forEach { (label, url) ->
                AssistChip(onClick = { runCatching { open.openUri(url) } }, label = { Text(label) })
            }
        }
    }
}
