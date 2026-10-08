package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.clickable
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

/** Every card of the collection as a scrollable list: picture, name, slab and price, in a chosen order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(repo: CardRepository, onBack: () -> Unit, onOpenCard: (List<Long>, Long) -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val all by repo.cards.collectAsState(initial = null)
    val setList by repo.sets.collectAsState(initial = emptyList())
    var game by rememberSaveable { mutableStateOf<Game?>(null) }
    var sort by rememberSaveable { mutableStateOf(CollectionSort.VALUE_DESC) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.collection_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } },
            )
        },
    ) { padding ->
        val s = settings
        val cards = all
        if (s == null || cards == null) return@Scaffold
        val sets = remember(setList) { setList.associateBy { it.game to it.setId } }
        val shown = remember(cards, game, sort, sets, s.currency, s.usdToEur) {
            CollectionList.sorted(cards.filter { game == null || it.game == game }, sort, sets) { Money.unitOrNull(it, s.currency, s.usdToEur) }
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
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.collection_empty)) }
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(shown, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenCard(order, c.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
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
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
