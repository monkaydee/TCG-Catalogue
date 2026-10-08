package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import android.widget.Toast
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.CollectionList
import com.monkaydee.tcgcatalogue.data.CollectionSort
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.ui.components.AppSelector
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.SelectorOption
import com.monkaydee.tcgcatalogue.ui.components.appBarColors

private enum class BulkDialog { BINDER, LOT, DELETE }

/** Market movers compare with the price a week ago. */
internal const val MOVER_DAYS = 7

/** "▲ 12 %" in the gain colour, "▼ 8 %" in the loss colour. */
@Composable
internal fun MoveLabel(percent: Double) {
    Text(
        (if (percent >= 0) "▲ " else "▼ ") + "%.0f %%".format(kotlin.math.abs(percent)),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        color = if (percent >= 0) com.monkaydee.tcgcatalogue.ui.theme.Gain else com.monkaydee.tcgcatalogue.ui.theme.Loss,
    )
}

/** Every card of the collection as a scrollable list: picture, name, slab and price, in a chosen order. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CollectionScreen(repo: CardRepository, onBack: () -> Unit, onOpenCard: (List<Long>, Long) -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val all by repo.cards.collectAsState(initial = null)
    val setList by repo.sets.collectAsState(initial = emptyList())
    var game by rememberSaveable { mutableStateOf<Game?>(null) }
    var sort by rememberSaveable { mutableStateOf(CollectionSort.VALUE_DESC) }
    // Long-press starts selecting; then taps add or remove cards and the bar acts on all of them.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var dialog by remember { mutableStateOf<BulkDialog?>(null) }
    val binders by repo.binders.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    androidx.activity.compose.BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    Scaffold(
        topBar = {
            if (selected.isEmpty()) TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.collection_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } },
            ) else TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.binder_selected, selected.size)) },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Outlined.Close, stringResource(android.R.string.cancel)) } },
                actions = {
                    IconButton(onClick = { dialog = BulkDialog.BINDER }) { Icon(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.bulk_add_to_binder)) }
                    IconButton(onClick = { dialog = BulkDialog.LOT }) { Icon(Icons.Outlined.Calculate, stringResource(R.string.lot_title)) }
                    IconButton(onClick = {
                        val chosen = all.orEmpty().filter { it.id in selected }
                        scope.launch { chosen.forEach { repo.setForTrade(it, true) } }
                        Toast.makeText(context, context.getString(R.string.bulk_trade_done, chosen.size), Toast.LENGTH_SHORT).show()
                        selected = emptySet()
                    }) { Icon(Icons.Outlined.SwapHoriz, stringResource(R.string.bulk_trade)) }
                    IconButton(onClick = { dialog = BulkDialog.DELETE }) { Icon(Icons.Outlined.Delete, stringResource(R.string.bulk_delete)) }
                },
            )
        },
    ) { padding ->
        val s = settings
        val cards = all
        if (s == null || cards == null) return@Scaffold
        val sets = remember(setList) { setList.associateBy { it.game to it.setId } }
        val history by remember { repo.recentHistory(MOVER_DAYS + 7) }.collectAsState(initial = emptyList())
        val moves = remember(history, s.currency, s.usdToEur) {
            com.monkaydee.tcgcatalogue.data.Movers.moves(history, MOVER_DAYS) { p, c -> Money.convert(p, c, s.currency, s.usdToEur) }
        }
        val shown = remember(cards, game, sort, sets, s.currency, s.usdToEur, moves) {
            CollectionList.sorted(cards.filter { game == null || it.game == game }, sort, sets, { moves[it.id]?.percent }) { Money.unitOrNull(it, s.currency, s.usdToEur) }
        }
        val order = shown.map { it.id }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val games = Game.entries.filter { g -> cards.any { it.game == g } }
                AppSelector(stringResource(R.string.design_game), game,
                    listOf(SelectorOption<Game?>(null, stringResource(R.string.home_all_games))) + games.map { SelectorOption<Game?>(it, it.short) },
                    { game = it }, Modifier.weight(1f), Icons.Outlined.Collections)
                AppSelector(stringResource(R.string.design_sort), sort,
                    CollectionSort.entries.map { SelectorOption(it, stringResource(it.label)) },
                    { sort = it }, Modifier.weight(1f), Icons.AutoMirrored.Outlined.Sort)
            }
            val coverage = Money.coverage(shown, s.currency, s.usdToEur)
            Text(
                stringResource(R.string.collection_summary, shown.sumOf { it.quantity }, coverage.text(s.currency)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (coverage.missingCopies > 0) {
                Text(stringResource(R.string.price_coverage_missing, coverage.missingCopies), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (selected.isNotEmpty()) {
                TextButton(onClick = { selected = shown.map { it.id }.toSet() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.bulk_select_all, shown.size)) }
            }
            when (dialog) {
                BulkDialog.LOT -> com.monkaydee.tcgcatalogue.ui.components.LotCalculator(
                    shown.filter { it.id in selected }.flatMap { c -> List(c.quantity) { c.name to Money.unitOrNull(c, s.currency, s.usdToEur) } }, s.currency,
                ) { dialog = null }
                BulkDialog.BINDER -> AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.bulk_add_to_binder)) },
                    text = {
                        Column {
                            if (binders.isEmpty()) Text(stringResource(R.string.bulk_no_binders))
                            binders.forEach { b ->
                                TextButton(onClick = {
                                    val rows = shown.filter { it.id in selected }.map { it.id }
                                    dialog = null; selected = emptySet()
                                    scope.launch {
                                        repo.addToBinder(b.id, rows, s.binderSpread)
                                        Toast.makeText(context, context.getString(R.string.bulk_added_to_binder, rows.size, b.name), Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text(b.name) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) } },
                )
                BulkDialog.DELETE -> AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.bulk_delete)) },
                    text = { Text(stringResource(R.string.bulk_delete_confirm, selected.size)) },
                    confirmButton = { TextButton(onClick = {
                        val chosen = cards.filter { it.id in selected }
                        dialog = null; selected = emptySet()
                        scope.launch { chosen.forEach { repo.delete(it) } }
                    }) { Text(stringResource(R.string.bulk_delete)) } },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) } },
                )
                null -> {}
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.collection_empty)) }
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(shown, key = { it.id }) { c ->
                    val on = c.id in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (selected.isEmpty()) onOpenCard(order, c.id) else selected = if (on) selected - c.id else selected + c.id },
                                onLongClick = { selected = if (on) selected - c.id else selected + c.id },
                            )
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.width(52.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(4.dp))) { CardOrSlab(c, Modifier.fillMaxSize(), thumb = true) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(c.setName, c.number, c.language).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val slab = CollectionList.slab(c)
                            if (slab != null) {
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text(slab, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            } else {
                                Text(c.condition, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.unitText(c, s.currency, s.usdToEur), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (c.quantity > 1) Text("×${c.quantity}", style = MaterialTheme.typography.labelSmall)
                            moves[c.id]?.takeIf { it.change != 0.0 }?.let { m -> MoveLabel(m.percent) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
