package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Binder
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetScreen(repo: CardRepository, game: Game, setId: String, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val cards by remember(game, setId) { repo.observeSet(game, setId) }.collectAsState(initial = emptyList())
    val set by remember(game, setId) { repo.sets.map { list -> list.firstOrNull { it.game == game && it.setId == setId } } }
        .collectAsState(initial = null)
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    var byNumber by rememberSaveable { mutableStateOf(false) }
    val sorted = if (byNumber) cards.sortedBy(Binder::numberKey) else cards.sortedByDescending { Money.value(it, s.currency, s.usdToEur) }
    val name = set?.name ?: cards.firstOrNull()?.setName ?: setId
    val owned = cards.map { it.cardId }.distinct().size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text(
                        Money.format(cards.sumOf { Money.value(it, s.currency, s.usdToEur) }, s.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val total = set?.total ?: 0
                    Text(
                        if (total > 0) "$owned of $total cards (${owned * 100 / total}%) · ${cards.sumOf { it.quantity }} copies"
                        else "$owned cards · ${cards.sumOf { it.quantity }} copies",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (total > 0) LinearProgressIndicator(progress = { (owned.toFloat() / total).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!byNumber, { byNumber = false }, { Text("By value") })
                        FilterChip(byNumber, { byNumber = true }, { Text("By number") })
                    }
                }
            }
            items(sorted, key = { it.id }) { c -> OwnedCardRow(c, s) { CardBrowse.open(sorted.map { it.id }, c.id, onOpenCard) } }
        }
    }
}

@Composable
fun OwnedCardRow(c: OwnedCard, s: AppSettings, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardOrSlab(c, Modifier.width(if (c.graded) 60.dp else 52.dp), thumb = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.number} · ${c.variantLabel} · ${c.condition}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                c.rarity?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(Money.value(c, s.currency, s.usdToEur), s.currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (c.price == null) "no price" else "${c.quantity} × ${Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
