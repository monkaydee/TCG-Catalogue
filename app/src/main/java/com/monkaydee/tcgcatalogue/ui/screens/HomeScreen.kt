package com.monkaydee.tcgcatalogue.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val value: Double,
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
                value = rows.sumOf { Money.value(it, s.currency, s.usdToEur) },
                lastAdded = rows.maxOf { it.addedAt },
            )
        }
        HomeState(s, cards, summaries, snaps, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

private enum class SetSort(val label: String) { VALUE("Value"), NAME("Name"), RECENT("Recent") }
private enum class Range(val label: String, val days: Long?) { M1("1M", 30), M3("3M", 90), Y1("1Y", 365), ALL("All", null) }

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
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(repo) }
    val state by vm.state.collectAsState()
    var gameFilter by rememberSaveable { mutableStateOf<Game?>(null) }
    var sort by rememberSaveable { mutableStateOf(SetSort.VALUE) }
    var range by rememberSaveable { mutableStateOf(Range.M3) }
    val s = state.settings
    val refreshing = refreshState == WorkInfo.State.RUNNING || refreshState == WorkInfo.State.ENQUEUED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TCG Catalogue") },
                actions = {
                    IconButton(onClick = onBinder) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Binder") }
                    IconButton(onClick = onPhotos) { Icon(Icons.Default.AddPhotoAlternate, "Import photos") }
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search cards") }
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh prices") }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loaded && state.cards.isEmpty()) {
            EmptyState(Modifier.padding(padding), onScan, onPhotos)
            return@Scaffold
        }
        val cards = state.cards.filter { gameFilter == null || it.game == gameFilter }
        val total = cards.sumOf { Money.value(it, s.currency, s.usdToEur) }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (gameFilter == null) "Portfolio value" else "${gameFilter!!.label} value", style = MaterialTheme.typography.labelLarge)
                        Text(Money.format(total, s.currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        if (gameFilter == null && history.size >= 2) {
                            val change = history.last() - history.first()
                            val pct = if (history.first() > 0) change / history.first() * 100 else 0.0
                            Text(
                                "%s%s (%+.1f%%) · %s".format(if (change >= 0) "+" else "", Money.format(change, s.currency), pct, range.label),
                                color = if (change >= 0) Gain else Loss,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text("${cards.sumOf { it.quantity }} cards · ${cards.size} unique", style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = onBinder, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open binder")
                        }
                        if (gameFilter == null) {
                            Spacer(Modifier.height(12.dp))
                            if (history.size >= 2) ValueChart(history)
                            else Text("The value chart fills in as prices are refreshed each day.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Range.entries.forEach { r -> FilterChip(r == range, { range = r }, { Text(r.label) }) }
                            }
                        }
                        if (s.lastPriceRefresh > 0) {
                            Text(
                                "Prices updated ${android.text.format.DateUtils.getRelativeTimeSpanString(s.lastPriceRefresh)}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            item {
                val owned = state.cards.map { it.game }.toSet()
                GameChips(gameFilter, { gameFilter = it }, Game.entries.filter { it in owned }, nullLabel = "All")
            }
            if (top.isNotEmpty()) {
                item { Text("Most valuable", style = MaterialTheme.typography.titleMedium) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(top, key = { it.id }) { c ->
                            Column(Modifier.width(96.dp).clickable { CardBrowse.open(byValue.map { it.id }, c.id, onOpenCard) }) {
                                CardOrSlab(c, thumb = true)
                                Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sets (${sets.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SetSort.entries.forEach { o ->
                        FilterChip(o == sort, { sort = o }, { Text(o.label) }, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            items(sets, key = { "${it.game}/${it.setId}" }) { set -> SetRow(set, s.currency) { onOpenSet(set.game, set.setId) } }
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
                    "${set.game.short} · " + if (set.total > 0) "${set.owned}/${set.total} cards" else "${set.owned} cards",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (set.total > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { (set.owned.toFloat() / set.total).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(set.value, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${set.copies} copies", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onScan: () -> Unit, onPhotos: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your collection is empty", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan a card from Pokémon, One Piece, Magic, Dragon Ball, Union Arena, Weiss Schwarz or Naruto. " +
                "The app reads the number printed on the card (e.g. 025/165, OP05-060, FB01-139) and looks up the card and its market price. " +
                "Graded slabs are recognised too.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onScan) {
            Icon(Icons.Default.CameraAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("Scan a card")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPhotos) {
            Icon(Icons.Default.AddPhotoAlternate, null)
            Spacer(Modifier.width(8.dp))
            Text("Import photos")
        }
    }
}
