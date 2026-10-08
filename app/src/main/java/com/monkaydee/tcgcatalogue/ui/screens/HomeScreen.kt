package com.monkaydee.tcgcatalogue.ui.screens

import androidx.annotation.StringRes
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.components.Backdrop
import com.monkaydee.tcgcatalogue.ui.components.EmptyIllustration
import com.monkaydee.tcgcatalogue.ui.components.EmptyKind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentSize
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.theme.LocalLook
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import com.monkaydee.tcgcatalogue.ui.components.AppButton
import com.monkaydee.tcgcatalogue.ui.components.ActionStyle
import com.monkaydee.tcgcatalogue.ui.components.AppSelector
import com.monkaydee.tcgcatalogue.ui.components.SelectorOption
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.map
import com.monkaydee.tcgcatalogue.ui.components.StandardButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.monkaydee.tcgcatalogue.ui.components.StandardTonalButton as FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.monkaydee.tcgcatalogue.ui.components.StandardOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.ui.components.ValueChart
import com.monkaydee.tcgcatalogue.ui.theme.Gain
import com.monkaydee.tcgcatalogue.ui.theme.Loss
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class SetSummaryUi(
    val game: Game,
    val setId: String,
    val name: String,
    val logoUrl: String?,
    val owned: Int,
    val total: Int,
    val copies: Int,
    val value: Double?,
    val lastAdded: Long,
)

data class HomeState(
    val settings: AppSettings = AppSettings(),
    val cards: List<OwnedCard> = emptyList(),
    val sets: List<SetSummaryUi> = emptyList(),
    val snapshots: List<PortfolioSnapshot> = emptyList(),
    val loaded: Boolean = false,
)

class HomeViewModel(repo: CardRepository) : ViewModel() {
    val state = combine(repo.cards, repo.sets, repo.snapshots, repo.settings.flow) { cards, sets, snaps, s ->
        val setMeta = sets.associateBy { it.game to it.setId }
        val summaries = cards.groupBy { it.game to it.setId }.map { (key, rows) ->
            val meta = setMeta[key]
            SetSummaryUi(
                game = key.first,
                setId = key.second,
                name = meta?.name ?: rows.first().setName,
                logoUrl = meta?.logoUrl,
                owned = rows.map { it.cardId }.distinct().size,
                total = meta?.total ?: 0,
                copies = rows.sumOf { it.quantity },
                value = Money.coverage(rows, s.currency, s.usdToEur).amount,
                lastAdded = rows.maxOf { it.addedAt },
            )
        }
        HomeState(s, cards, summaries, snaps, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

private enum class SetSort(@StringRes val label: Int) { VALUE(R.string.home_sort_value), NAME(R.string.home_sort_name), RECENT(R.string.home_sort_recent) }
private enum class Range(@StringRes val label: Int, val days: Long?) { M1(R.string.home_range_1m, 30), M3(R.string.home_range_3m, 90), Y1(R.string.home_range_1y, 365), ALL(R.string.home_range_all, null) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: CardRepository,
    refreshState: WorkInfo.State?,
    onRefresh: () -> Unit,
    onOpenSet: (Game, String) -> Unit,
    onOpenCard: (Long) -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onPhotos: () -> Unit,
    onBinder: () -> Unit = {},
    onCollection: () -> Unit = {},
    onWishlist: () -> Unit = {},
    onTradeList: () -> Unit = {},
    onSold: () -> Unit = {},
    onSealed: () -> Unit = {},
    onSettings: () -> Unit = {},
    onPreGrade: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(repo) }
    val state by vm.state.collectAsState()
    val wishCount by remember { repo.wishlist.map { it.size } }.collectAsState(initial = 0)
    val soldCount by remember { repo.sold.map { it.size } }.collectAsState(initial = 0)
    val tradeCount = state.cards.count { it.forTrade }
    val sealedItems by repo.sealed.collectAsState(initial = emptyList())
    val sealedCount = sealedItems.sumOf { it.quantity }
    var gameFilter by rememberSaveable { mutableStateOf<Game?>(null) }
    var sort by rememberSaveable { mutableStateOf(SetSort.VALUE) }
    var range by rememberSaveable { mutableStateOf(Range.M3) }
    val s = state.settings
    val refreshing = refreshState == WorkInfo.State.RUNNING || refreshState == WorkInfo.State.ENQUEUED

    var more by remember { mutableStateOf(false) }
    val picture = LocalLook.current.homeImage
    Backdrop(picture) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = appBarColors(overPicture = picture != null),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.home_search)) }
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.design_more)) }
                        DropdownMenu(more, onDismissRequest = { more = false }) {
                            listOf(
                                Triple(R.string.home_binder, Icons.AutoMirrored.Outlined.MenuBook, onBinder),
                                Triple(R.string.grade_open, Icons.Outlined.Straighten, onPreGrade),
                                Triple(R.string.home_import_photos, Icons.Outlined.AddPhotoAlternate, onPhotos),
                                Triple(R.string.home_refresh, Icons.Outlined.Refresh, onRefresh),
                            ).forEach { (label, icon, action) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, null) },
                                    onClick = { more = false; action() }, enabled = label != R.string.home_refresh || !refreshing)
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.loaded && state.cards.isNotEmpty()) ExtendedFloatingActionButton(onClick = onScan,
                icon = { Icon(Icons.Outlined.CameraAlt, null) }, text = { Text(stringResource(R.string.nav_scan)) },
                shape = RoundedCornerShape(16.dp))
        },
    ) { padding ->
        if (state.loaded && state.cards.isEmpty()) {
            // Sold everything or only wishing so far: the lists stay reachable above the empty state.
            if (wishCount + soldCount + sealedCount > 0) ListsRow(wishCount, tradeCount, soldCount, sealedCount, onWishlist, onTradeList, onSold, onSealed, Modifier.padding(padding).padding(16.dp))
            EmptyState(Modifier.padding(padding), onScan, onPhotos)
            return@Scaffold
        }
        val cards = state.cards.filter { gameFilter == null || it.game == gameFilter }
        // Sealed products count towards the whole portfolio (not towards a single game's value).
        val coverage = Money.coverage(cards, s.currency, s.usdToEur, if (gameFilter == null) sealedItems else emptyList())
        val sets = state.sets.filter { gameFilter == null || it.game == gameFilter }.let { list ->
            when (sort) {
                SetSort.VALUE -> list.sortedByDescending { it.value }
                SetSort.NAME -> list.sortedBy { it.name }
                SetSort.RECENT -> list.sortedByDescending { it.lastAdded }
            }
        }
        val from = range.days?.let { LocalDate.now().toEpochDay() - it } ?: Long.MIN_VALUE
        // History is only tracked for the whole portfolio, so the chart ignores the game filter.
        val history = state.snapshots.filter { it.day >= from }.map { if (s.currency == "EUR") it.valueEur else it.valueUsd }
        val byValue = cards.sortedByDescending { Money.unit(it, s.currency, s.usdToEur) }
        val top = byValue.take(10)

        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // A newer collection was saved by another phone to the cloud backup file.
                val newer by com.monkaydee.tcgcatalogue.data.CloudBackup.pending.collectAsState()
                if (newer != null) {
                    Card(
                        Modifier.fillMaxWidth().clickable(onClick = onSettings),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.home_cloud_newer_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.home_cloud_newer_text), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (gameFilter == null) stringResource(R.string.home_portfolio_value) else stringResource(R.string.home_game_value, gameFilter!!.label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(coverage.text(s.currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        if (coverage.missingCopies > 0) Text(stringResource(R.string.price_coverage_missing, coverage.missingCopies), style = MaterialTheme.typography.bodySmall)
                        if (gameFilter == null && coverage.missingCopies == 0 && history.size >= 2) {
                            val change = history.last() - history.first()
                            val pct = if (history.first() > 0) change / history.first() * 100 else 0.0
                            Text(
                                stringResource(R.string.home_change, (if (change >= 0) "+" else "") + Money.format(change, s.currency), pct, stringResource(range.label)),
                                color = if (change >= 0) Gain else Loss,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.home_counts,
                                pluralStringResource(R.plurals.home_cards, cards.sumOf { it.quantity }, cards.sumOf { it.quantity }),
                                pluralStringResource(R.plurals.home_unique, cards.size, cards.size),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(stringResource(R.string.home_open_binder), onBinder, Modifier.weight(1f),
                                Icons.AutoMirrored.Outlined.MenuBook, style = ActionStyle.TONAL)
                            AppButton(stringResource(R.string.home_open_collection), onCollection, Modifier.weight(1f),
                                Icons.AutoMirrored.Outlined.List, style = ActionStyle.TONAL)
                        }
                        if (gameFilter == null) {
                            Spacer(Modifier.height(12.dp))
                            if (history.size >= 2) ValueChart(history)
                            else Text(stringResource(R.string.home_chart_empty), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Range.entries.forEach { r -> FilterChip(r == range, { range = r }, { Text(stringResource(r.label)) }) }
                            }
                        }
                        if (s.lastPriceRefresh > 0) {
                            Text(
                                stringResource(R.string.home_prices_updated, android.text.format.DateUtils.getRelativeTimeSpanString(s.lastPriceRefresh)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            item { ListsRow(wishCount, tradeCount, soldCount, sealedCount, onWishlist, onTradeList, onSold, onSealed) }
            item {
                val owned = state.cards.map { it.game }.toSet()
                GameChips(gameFilter, { gameFilter = it }, Game.entries.filter { it in owned }, nullLabel = stringResource(R.string.home_all_games))
            }
            if (top.isNotEmpty()) {
                item { Text(stringResource(R.string.home_most_valuable), style = MaterialTheme.typography.titleMedium) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(top, key = { it.id }) { c ->
                            Column(Modifier.width(96.dp).clickable { CardBrowse.open(byValue.map { it.id }, c.id, onOpenCard) }) {
                                CardOrSlab(c, thumb = true)
                                Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Money.unitText(c, s.currency, s.usdToEur), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_sets, sets.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                AppSelector(stringResource(R.string.design_sort), sort,
                    SetSort.entries.map { SelectorOption(it, stringResource(it.label)) }, { sort = it })
            }
            items(sets, key = { "${it.game}/${it.setId}" }) { set -> SetRow(set, s.currency) { onOpenSet(set.game, set.setId) } }
        }
    }
    }
}

@Composable
private fun SetRow(set: SetSummaryUi, currency: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (set.logoUrl != null) AsyncImage(set.logoUrl, null, Modifier.fillMaxSize())
                else Text(set.setId, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(set.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (set.total > 0) stringResource(R.string.home_set_progress, set.game.short, set.owned, set.total) else stringResource(R.string.home_set_owned, set.game.short, set.owned),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (set.total > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { (set.owned.toFloat() / set.total).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(set.value?.let { Money.format(it, currency) } ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(pluralStringResource(R.plurals.home_copies, set.copies, set.copies), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Shortcuts to the wishlist, trade list and sold cards, with their counts. */
@Composable
private fun ListsRow(
    wishCount: Int,
    tradeCount: Int,
    soldCount: Int,
    sealedCount: Int,
    onWishlist: () -> Unit,
    onTradeList: () -> Unit,
    onSold: () -> Unit,
    onSealed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lists_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListShortcut(Icons.Outlined.Favorite, stringResource(R.string.wish_title), wishCount, onWishlist, Modifier.weight(1f))
            ListShortcut(Icons.Outlined.SwapHoriz, stringResource(R.string.trade_title), tradeCount, onTradeList, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListShortcut(Icons.Outlined.Paid, stringResource(R.string.sold_title), soldCount, onSold, Modifier.weight(1f))
            ListShortcut(Icons.Outlined.Inventory2, stringResource(R.string.sealed_title), sealedCount, onSealed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ListShortcut(icon: ImageVector, label: String, count: Int, onClick: () -> Unit, modifier: Modifier) {
    Card(modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onScan: () -> Unit, onPhotos: () -> Unit) {
    EmptyIllustration(
        kind = EmptyKind.COLLECTION,
        title = stringResource(R.string.home_empty_title),
        text = stringResource(R.string.home_empty_text),
        modifier = modifier.fillMaxSize().padding(32.dp).wrapContentSize(Alignment.Center),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScan) {
                Icon(Icons.Outlined.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_scan_card))
            }
            OutlinedButton(onClick = onPhotos) {
                Icon(Icons.Outlined.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_import_photos))
            }
        }
    }
}
