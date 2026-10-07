package com.monkaydee.tcgcatalogue.ui.screens

import com.monkaydee.tcgcatalogue.data.CardLanguage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.SealedItem
import com.monkaydee.tcgcatalogue.data.remote.SealedProduct
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.ui.components.QuantityStepper
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Sealed products (booster boxes, packs, decks, ETBs …) with their TCGplayer market value, which
 * counts towards the portfolio. Products are added by searching the daily product list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealedScreen(repo: CardRepository, onBack: () -> Unit) {
    val items by repo.sealed.collectAsState(initial = emptyList())
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SealedItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.sealed_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.binder_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.sealed_add)) })
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.sealed_value), style = MaterialTheme.typography.labelLarge)
                        Text(
                            Money.coverage(emptyList(), s.currency, s.usdToEur, items).text(s.currency),
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        )
                        val coverage = Money.coverage(emptyList(), s.currency, s.usdToEur, items)
                        if (coverage.missingCopies > 0) Text(stringResource(R.string.price_coverage_missing, coverage.missingCopies), style = MaterialTheme.typography.bodySmall)
                        Text(pluralStringResource(R.plurals.sealed_count, items.sumOf { it.quantity }, items.sumOf { it.quantity }), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (items.isEmpty()) {
                item { Text(stringResource(R.string.sealed_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(items, key = { it.id }) { item -> SealedRow(item, s) { editing = item } }
        }
    }

    if (adding) {
        AddSealedSheet(repo, s, onDismiss = { adding = false })
    }
    editing?.let { item ->
        var qty by remember(item) { mutableIntStateOf(item.quantity) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.groupName, style = MaterialTheme.typography.bodySmall)
                    QuantityStepper(qty, { qty = it }, min = 0)
                    if (qty == 0) Text(stringResource(R.string.sealed_remove_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.updateSealed(item.copy(quantity = qty)) }
                    editing = null
                }) { Text(stringResource(R.string.ui_ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
}

@Composable
private fun SealedRow(item: SealedItem, s: AppSettings, onClick: () -> Unit) {
    val unitPrice = item.price?.takeIf { it.isFinite() && it > 0 }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SealedImage(item.imageUrl, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${item.game.short} · ${CardLanguage.displayCode(item.language)} · ${item.groupName}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.priceSource.isNotBlank()) Text(item.priceSource, style = MaterialTheme.typography.labelSmall)
                item.referencePrice?.let { value -> Text("Aggregate reference: ${Money.format(value, item.referenceCurrency ?: "EUR")} · not language-specific", style = MaterialTheme.typography.labelSmall) }
                item.priceUpdatedAt?.let { Text("Updated ${java.text.DateFormat.getDateInstance().format(java.util.Date(it))}", style = MaterialTheme.typography.labelSmall) }
                item.purchasePrice?.let { paid ->
                    val now = unitPrice ?: return@let
                    val diff = (Money.convert(now, item.priceCurrency, s.currency, s.usdToEur) - Money.convert(paid, item.purchaseCurrency ?: item.priceCurrency, s.currency, s.usdToEur)) * item.quantity
                    Text(
                        stringResource(R.string.sealed_gain, (if (diff >= 0) "+" else "") + Money.format(diff, s.currency)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (diff >= 0) com.monkaydee.tcgcatalogue.ui.theme.Gain else com.monkaydee.tcgcatalogue.ui.theme.Loss,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(unitPrice?.let { Money.format(Money.sealedValue(item, s.currency, s.usdToEur), s.currency) } ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    unitPrice?.let { "${item.quantity} × ${Money.format(Money.convert(it, item.priceCurrency, s.currency, s.usdToEur), s.currency)}" }
                        ?: stringResource(R.string.sealed_no_price),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Search the day's sealed product list for a game and add a product. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSealedSheet(repo: CardRepository, s: AppSettings, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var game by remember { mutableStateOf<Game?>(s.enabledGames.firstOrNull() ?: Game.POKEMON) }
    var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("EN") }
    var confirmedLanguage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var quoting by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SealedProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<SealedProduct?>(null) }
    var qty by remember { mutableIntStateOf(1) }
    var paid by remember { mutableStateOf("") }
    // Only composed search rows fetch prices; scrolling never starts an 80-item burst.
    // Results survive query edits within this language so revisiting a row reuses its quote.
    val previews = remember(game, language, s.currency) { mutableStateMapOf<Long, SealedProduct>() }
    val previewLocks = remember(game, language, s.currency) { mutableMapOf<Long, Mutex>() }
    val previewLimit = remember { Semaphore(2) }

    suspend fun pricePreview(product: SealedProduct): SealedProduct {
        if (product.price != null) return product
        return previewLocks.getOrPut(product.productId) { Mutex() }.withLock {
            previews[product.productId] ?: previewLimit.withPermit {
                val quote = try { repo.sealedQuote(product) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { product.copy(quoteReason = "unavailable") }
                previews[product.productId] = quote
                quote
            }
        }
    }

    LaunchedEffect(game, query, language) {
        val g = game ?: return@LaunchedEffect
        if (query.isBlank()) { results = emptyList(); loading = false; error = null; return@LaunchedEffect }
        loading = true
        results = emptyList(); error = null
        try { kotlinx.coroutines.delay(250); results = repo.searchSealed(g, query, language) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { error = "Catalogue could not be fetched. Check your connection and try again." }
        finally { loading = false }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = picked
            if (p == null) {
                Text(stringResource(R.string.sealed_add), style = MaterialTheme.typography.titleLarge)
                GameChips(selected = game, onSelect = {
                    if (it != null) {
                        if (it == Game.ONE_PIECE && language == "DE") language = "EN"
                        game = it
                    }
                }, games = Game.entries, nullLabel = null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EN" to "English", "DE" to "Deutsch", "JA" to "日本語 (JP)").forEach { (code, label) ->
                        FilterChip(selected = language == code, onClick = { language = code }, enabled = game != Game.ONE_PIECE || code != "DE", label = { Text(label) })
                    }
                }
                Text(stringResource(if (game == Game.ONE_PIECE) R.string.sealed_one_piece_no_german else R.string.sealed_printed_language_hint), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    query, { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text(stringResource(R.string.sealed_search_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                if (!loading && error == null && query.isNotBlank() && results.isEmpty()) {
                    Text(stringResource(R.string.sealed_no_results), style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.fillMaxWidth().aspectRatio(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results, key = { it.productId }) { r ->
                        val priced = previews[r.productId] ?: r
                        LaunchedEffect(r.game, r.productId, r.language, s.currency) {
                            if (r.price == null && !previews.containsKey(r.productId)) {
                                pricePreview(r)
                            }
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            picked = priced; confirmedLanguage = !priced.requiresLanguageConfirmation; error = null; quoting = priced.price == null && priced.quoteReason == null
                            scope.launch {
                                try {
                                    val quote = pricePreview(priced)
                                    if (picked?.productId == r.productId && picked?.language == r.language) picked = quote
                                }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { error = "Language-specific price unavailable; you can still add the product without a valuation." }
                                finally { quoting = false }
                            }
                        }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SealedImage(priced.imageUrl, Modifier.size(48.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(r.groupName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(100.dp)) {
                                Text(priced.price?.let { Money.format(Money.convert(it, priced.currency, s.currency, s.usdToEur), s.currency) }
                                    ?: stringResource(if (priced.quoteReason == null) R.string.sealed_checking_price else if (priced.quoteReason == "not_found") R.string.sealed_no_matching_price else R.string.sealed_price_unavailable), style = MaterialTheme.typography.labelLarge)
                                if (priced.priceScope == "language-specific-asking" && priced.price != null) {
                                    Text(stringResource(if (priced.quoteEvidence == "limited") R.string.sealed_limited_reference else R.string.sealed_asking_reference), style = MaterialTheme.typography.labelSmall)
                                    priced.quoteListings?.let { count -> Text(pluralStringResource(R.plurals.data_listings_count, count, count), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SealedImage(p.imageUrl, Modifier.size(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(p.groupName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            p.price?.let { Money.format(Money.convert(it, p.currency, s.currency, s.usdToEur), s.currency) } ?: stringResource(R.string.sealed_no_price),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text("Printed language: ${CardLanguage.displayCode(p.language)}", style = MaterialTheme.typography.labelLarge)
                if (quoting) { CircularProgressIndicator(Modifier.size(24.dp)); Text("Fetching matching-language listings…", style = MaterialTheme.typography.bodySmall) }
                if (p.source.isNotBlank()) Text(p.source, style = MaterialTheme.typography.bodySmall)
                p.quoteReason?.let { Text(stringResource(if (it == "not_found") R.string.sealed_no_matching_price else R.string.sealed_price_unavailable), style = MaterialTheme.typography.bodySmall) }
                p.availabilityEvidence?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                p.referencePrice?.let { Text("Cardmarket aggregate reference: ${Money.format(it, p.referenceCurrency ?: "EUR")} · not a price for ${CardLanguage.displayCode(p.language)} specifically", style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (p.requiresLanguageConfirmation) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmedLanguage, onCheckedChange = { confirmedLanguage = it })
                    Text("I verified this exact sealed product exists in ${CardLanguage.displayCode(p.language)} and matches my item", style = MaterialTheme.typography.bodySmall)
                }
                QuantityStepper(qty, { qty = it })
                OutlinedTextField(
                    paid, { paid = it },
                    label = { Text(stringResource(R.string.add_purchase_price, s.currency)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { picked = null }) { Text(stringResource(R.string.ui_cancel)) }
                    TextButton(enabled = confirmedLanguage && !quoting, onClick = {
                        scope.launch {
                            repo.addSealed(p, qty, paid.replace(',', '.').toDoubleOrNull())
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.add_confirm)) }
                }
            }
        }
    }
}

/** Keep missing/failed product photos visible as a product placeholder instead of blank space. */
@Composable
private fun SealedImage(url: String?, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp)) } },
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}
