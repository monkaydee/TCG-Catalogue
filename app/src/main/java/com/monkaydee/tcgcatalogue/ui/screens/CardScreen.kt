package com.monkaydee.tcgcatalogue.ui.screens

import com.monkaydee.tcgcatalogue.data.CardLanguage
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.HoloCard
import com.monkaydee.tcgcatalogue.ui.components.PriceHistoryCard
import com.monkaydee.tcgcatalogue.ui.components.rememberDeviceTilt
import com.monkaydee.tcgcatalogue.ui.components.rememberNotificationPermissionRequest

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
fun CardScreen(repo: CardRepository, id: Long, onBack: () -> Unit, onReplace: (Long) -> Unit = {}, onPreGrade: (String, Game) -> Unit = { _, _ -> }) {
    val all by repo.cards.collectAsState(initial = null)
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val order = remember(id) { CardBrowse.ids.takeIf { id in it } ?: listOf(id) }
    // Cards removed meanwhile drop out of the pager.
    val cards = all?.associateBy { it.id }?.let { byId -> order.mapNotNull { byId[it] } }.orEmpty()
    // Dialogs keep the card's row id, so they always act on its latest data.
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var sellId by remember { mutableStateOf<Long?>(null) }
    var alertId by remember { mutableStateOf<Long?>(null) }
    val askNotifications = rememberNotificationPermissionRequest()
    // One tilt sensor listener for all pages of the pager.
    val tilt = rememberDeviceTilt()
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
            val card = cards[page]
            CardDetail(
                c = card,
                s = s,
                repo = repo,
                tilt = tilt,
                actions = {
                    CardActionBar(
                        card = card,
                        editLoading = editLoading && editing?.id == card.id,
                        onEdit = { if (!editLoading) editing = card },
                        onSell = { sellId = card.id },
                        onToggleTrade = { on -> scope.launch { repo.setForTrade(card, on) } },
                        onAlert = { alertId = card.id },
                        onDelete = { deleteId = card.id },
                    )
                },
                onAlert = { alertId = card.id },
                onPreGrade = { onPreGrade("${card.name} · ${card.number}", card.game) },
            )
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

    val toDelete = deleteId?.let { id -> cards.firstOrNull { it.id == id } }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.card_remove_title)) },
            text = { Text(pluralStringResource(R.plurals.card_remove_message, toDelete.quantity, toDelete.quantity, toDelete.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteId = null
                    scope.launch { repo.delete(toDelete) }
                }) { Text(stringResource(R.string.card_remove)) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.card_cancel)) } },
        )
    }

    val toSell = sellId?.let { id -> cards.firstOrNull { it.id == id } }
    if (toSell != null) {
        SellDialog(toSell, s, onDismiss = { sellId = null }) { quantity, each, fees ->
            sellId = null
            scope.launch {
                runCatching { repo.sell(toSell, quantity, each, s.currency, fees = fees) }
                    .onSuccess { Toast.makeText(context, AppStrings.get(R.string.sold_done, quantity, toSell.name), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    val toAlert = alertId?.let { id -> cards.firstOrNull { it.id == id } }
    if (toAlert != null) {
        PriceAlertDialog(toAlert, s, onDismiss = { alertId = null }) { above, below ->
            alertId = null
            if (above != null || below != null) askNotifications()
            scope.launch { repo.setAlerts(toAlert, above, below, s.currency) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetail(
    c: OwnedCard,
    s: AppSettings,
    repo: CardRepository,
    tilt: State<Offset>,
    actions: @Composable () -> Unit,
    onAlert: () -> Unit,
    onPreGrade: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val history by remember(c.id) { repo.priceHistory(c.id) }.collectAsState(initial = emptyList())
    LaunchedEffect(c.id, c.variant, c.language, c.condition, c.graded, c.grader, c.grade, c.gradeQualifier) {
        if (Money.unitOrNull(c, s.currency, s.usdToEur) == null) runCatching { repo.refreshPrice(c.id) }
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Room around the card so its shadow and lean aren't cut off.
        HoloCard(
            tilt = tilt,
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(if (c.graded) 0.8f else 0.75f),
            // Slab corners scale with its width (see GradedSlab).
            shape = if (c.graded) RoundedCornerShape(percent = 6) else RoundedCornerShape(12.dp),
        ) {
            CardOrSlab(c, Modifier.fillMaxWidth())
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${c.setName} · ${c.number}", style = MaterialTheme.typography.bodyMedium)
            Text(listOfNotNull(c.game.label, c.rarity, c.variantLabel).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            c.marketLabel?.let { Text(stringResource(R.string.card_cardmarket_label, it), style = MaterialTheme.typography.bodySmall) }
            Text(
                Money.unitOrNull(c, s.currency, s.usdToEur)?.let { Money.valueText(c, s.currency, s.usdToEur) } ?: stringResource(if (c.graded) R.string.card_no_graded_price else R.string.card_no_market_price),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (c.price != null && c.price.isFinite() && c.price > 0) {
                val unit = Money.unitText(c, s.currency, s.usdToEur)
                val original = Money.format(c.price, c.priceCurrency)
                Text(
                    c.priceSource?.let { stringResource(R.string.card_unit_price_on_source, unit, original, it) }
                        ?: stringResource(R.string.card_unit_price_on_market, unit, original),
                    style = MaterialTheme.typography.bodySmall,
                )
                c.priceUpdatedAt?.let {
                    Text(stringResource(R.string.card_price_updated, android.text.format.DateUtils.getRelativeTimeSpanString(it)), style = MaterialTheme.typography.labelSmall)
                }
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
            if (c.graded) RawReferencePanel(c, s, repo)
            CardLedgerPanel(repo, c, s)
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
        actions()
        if (!c.graded) {
            OutlinedButton(onClick = onPreGrade, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Straighten, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.grade_open))
            }
        }
        ActiveAlerts(c, s, onAlert)
        PriceHistoryCard(history, s)
        PriceLinks(c, s)
        PriceOverview(c, s, repo)
        if (s.hasServer) GradedPanel(c, s, repo)
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

/** The raw quote remains visible even when this exact grader/grade has no quote. */
@Composable
private fun RawReferencePanel(c: OwnedCard, s: AppSettings, repo: CardRepository) {
    var quote by remember(c.id, c.variant, c.language, s.pokemonSource) { mutableStateOf<com.monkaydee.tcgcatalogue.data.remote.Price?>(null) }
    var loading by remember(c.id) { mutableStateOf(true) }
    var failed by remember(c.id) { mutableStateOf(false) }
    var refresh by remember(c.id) { mutableStateOf(0) }
    LaunchedEffect(c.id, c.variant, c.language, s.pokemonSource, refresh) {
        loading = true
        val result = com.monkaydee.tcgcatalogue.data.remote.attempt { repo.rawReferenceFor(c) }
        quote = result.getOrNull()
        failed = result.isFailure
        loading = false
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.card_raw_reference), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.card_refresh)) }
            }
            quote?.let { q ->
                Text(Money.format(Money.convert(q.amount, q.currency, s.currency, s.usdToEur), s.currency), style = MaterialTheme.typography.titleLarge)
                Text(listOf(CardLanguage.displayCode(c.language), q.source.label).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                q.note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            if (!loading && quote == null) Text(stringResource(if (failed) R.string.card_raw_reference_failed else R.string.card_raw_reference_missing), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.card_raw_reference_hint, listOfNotNull(c.grader, c.grade, c.gradeQualifier).joinToString(" ")), style = MaterialTheme.typography.labelSmall)
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
                            if (!c.graded) runCatching { repo.refreshPrice(c.id) }
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
 * Graded prices of this card from the app's price server (every company and grade it knows),
 * loaded on request; the copy's own grade is marked. Slabs from PSA can have their cert checked.
 */
@Composable
private fun GradedPanel(c: OwnedCard, s: AppSettings, repo: CardRepository) {
    var prices by remember(c.id) { mutableStateOf<List<com.monkaydee.tcgcatalogue.data.remote.PriceServerApi.Graded>?>(null) }
    var failure by remember(c.id) { mutableStateOf<Int?>(null) }
    var loading by remember(c.id) { mutableStateOf(false) }
    var cert by remember(c.id) { mutableStateOf<String?>(null) }
    var certLoading by remember(c.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.graded_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        loading = true
                        scope.launch {
                            if (c.graded) runCatching { repo.refreshPrice(c.id) }
                            val r = com.monkaydee.tcgcatalogue.data.remote.attempt { repo.gradedPricesFor(c) }
                            failure = r.exceptionOrNull()?.let { if (it is com.monkaydee.tcgcatalogue.data.remote.PriceServerApi.ProvidersUnavailableException) R.string.graded_providers_unavailable else R.string.graded_failed }
                            prices = r.getOrNull()
                            loading = false
                        }
                    }) { Text(stringResource(if (prices == null) R.string.card_show else R.string.card_refresh)) }
                }
            }
            when {
                failure != null -> Text(stringResource(failure!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                prices?.isEmpty() == true -> Text(stringResource(R.string.graded_no_quote_table), style = MaterialTheme.typography.bodySmall)
            }
            prices.orEmpty().sortedWith(compareBy({ it.grader }, { -(it.grade.toDoubleOrNull() ?: 0.0) })).forEach { g ->
                val mine = c.graded && g.grader.equals(c.grader, ignoreCase = true) && g.grade.toDoubleOrNull() == c.grade?.toDoubleOrNull() && c.gradeQualifier == g.qualifier
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.graded_row_source, listOfNotNull(g.grader, g.grade, g.qualifier).joinToString(" "), g.source) + if (mine) " · " + stringResource(R.string.graded_yours) else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    val converted = Money.convert(g.price, g.currency, s.currency, s.usdToEur)
                    Text(
                        Money.format(converted, s.currency) + if (g.currency != s.currency) "  (${Money.format(g.price, g.currency)})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (prices.orEmpty().any { (it.listings ?: 0) > 0 }) {
                Text(stringResource(R.string.graded_asking_reference), style = MaterialTheme.typography.bodySmall)
            }
            val certNumber = c.certNumber?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() && c.grader == "PSA" }
            if (certNumber != null) {
                HorizontalDivider()
                if (certLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        certLoading = true
                        scope.launch {
                            val r = com.monkaydee.tcgcatalogue.data.remote.attempt { repo.verifyCert(certNumber) }
                            val found = r.getOrNull()
                            cert = when {
                                r.isFailure -> AppStrings.get(R.string.cert_failed)
                                found == null -> AppStrings.get(R.string.cert_unknown)
                                else -> listOfNotNull(
                                    listOfNotNull(found.year, found.set, found.description, found.cardNumber?.let { "#$it" }).joinToString(" "),
                                    found.grade?.let { AppStrings.get(R.string.cert_grade, it) },
                                    found.population?.let { p -> AppStrings.get(R.string.cert_population, p, found.higher ?: 0) },
                                    AppStrings.get(R.string.cert_mismatch).takeIf {
                                        val psaGrade = found.grade?.let { g -> Regex("""\d+(\.\d)?""").findAll(g).lastOrNull()?.value }
                                        psaGrade != null && c.grade != null && psaGrade.toDoubleOrNull() != c.grade.toDoubleOrNull()
                                    },
                                ).joinToString("\n")
                            }
                            certLoading = false
                        }
                    }) { Text(stringResource(R.string.cert_check)) }
                }
                cert?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
