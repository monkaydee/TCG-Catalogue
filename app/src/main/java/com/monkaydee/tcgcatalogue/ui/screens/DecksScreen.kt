package com.monkaydee.tcgcatalogue.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.DeckCheck
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.DeckCard
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.ui.components.AppSelector
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.SelectorOption
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import kotlinx.coroutines.launch

/** The user's decks with their card count; a new deck starts from a name and a game. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(repo: CardRepository, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val decks by repo.decks.collectAsState(initial = emptyList())
    val cards by repo.deckCards.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(colors = appBarColors(), title = { Text(stringResource(R.string.decks_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.decks_new)) })
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.decks_empty), style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(decks, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(d.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Style, null)
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.Bold)
                            Text(d.game.short + " · " + stringResource(R.string.deck_cards, cards.filter { it.deckId == d.id }.sumOf { it.quantity }),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var game by remember { mutableStateOf(Game.POKEMON) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.decks_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.binder_name)) }, singleLine = true)
                    AppSelector(stringResource(R.string.design_game), game, Game.entries.map { SelectorOption(it, it.short) }, { game = it })
                }
            },
            confirmButton = { TextButton(onClick = { creating = false; scope.launch { onOpen(repo.createDeck(name, game)) } }) { Text(stringResource(R.string.binder_save)) } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/**
 * One deck: its cards with copies to adjust, the basic checks of its game, what it is worth and
 * what is still missing compared with the collection. Cards come from a pasted list or the collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(repo: CardRepository, deckId: Long, onBack: () -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val decks by repo.decks.collectAsState(initial = null)
    val allCards by repo.deckCards.collectAsState(initial = emptyList())
    val owned by repo.cards.collectAsState(initial = emptyList())
    val deck = decks?.firstOrNull { it.id == deckId }
    var pasting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cards = allCards.filter { it.deckId == deckId }
    Scaffold(
        topBar = {
            TopAppBar(colors = appBarColors(), title = { Text(deck?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } },
                actions = {
                    IconButton(onClick = { pasting = true }) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.deck_import)) }
                    IconButton(onClick = { picking = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.deck_add_owned)) }
                    IconButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, deck?.name).putExtra(Intent.EXTRA_TEXT, DeckCheck.text(cards)), null))
                    }, enabled = cards.isNotEmpty()) { Icon(Icons.Outlined.Share, stringResource(R.string.deck_share)) }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.deck_delete)) }
                })
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        if (deck == null) return@Scaffold
        val price = { c: DeckCard -> c.price?.let { Money.convert(it, c.priceCurrency, s.currency, s.usdToEur) } }
        val summary = DeckCheck.summary(cards, owned, deck.game, price)
        val have = owned.filter { it.game == deck.game }.groupBy { it.cardId }.mapValues { (_, r) -> r.sumOf { it.quantity } }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(deck.game.short + " · " + stringResource(R.string.deck_cards, summary.cards), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.deck_value, Money.format(summary.value, s.currency)), style = MaterialTheme.typography.bodyMedium)
                    Text(if (summary.missing == 0) stringResource(R.string.deck_missing_none)
                        else stringResource(R.string.deck_missing, summary.missing, Money.format(summary.missingCost, s.currency)), style = MaterialTheme.typography.bodyMedium)
                    if (summary.unknownPrices > 0) Text(stringResource(R.string.lot_unknown, summary.unknownPrices), style = MaterialTheme.typography.labelSmall)
                    DeckCheck.problems(deck.game, cards).forEach { p ->
                        Text(
                            when (p) {
                                is DeckCheck.Problem.Total -> stringResource(R.string.deck_rule_total, deck.game.short, p.needed, p.has)
                                is DeckCheck.Problem.Minimum -> stringResource(R.string.deck_rule_min, deck.game.short, p.needed, p.has)
                                is DeckCheck.Problem.Copies -> stringResource(R.string.deck_rule_copies, p.max, p.names.joinToString(", "))
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Text(stringResource(R.string.deck_rules_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    if (notFound.isNotEmpty()) Text(stringResource(R.string.deck_not_found, notFound.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    HorizontalDivider()
                }
            }
            items(cards.sortedBy { it.name }, key = { it.cardId }) { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(36.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(3.dp))) { c.imageUrl?.let { CardImage(it, Modifier.fillMaxSize(), thumb = true) } }
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(c.setName, c.number).filter { it.isNotBlank() }.joinToString(" · ") + " · " +
                            stringResource(R.string.deck_owned, (have[c.cardId] ?: 0).coerceAtMost(c.quantity), c.quantity),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if ((have[c.cardId] ?: 0) >= c.quantity) com.monkaydee.tcgcatalogue.ui.theme.Gain else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(price(c)?.let { Money.format(it, s.currency) } ?: "—", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { scope.launch { repo.setDeckQuantity(c, c.quantity - 1) } }) { Icon(Icons.Outlined.Remove, null) }
                    Text("${c.quantity}", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { scope.launch { repo.setDeckQuantity(c, c.quantity + 1) } }) { Icon(Icons.Outlined.Add, null) }
                }
            }
        }
        if (pasting) {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { pasting = false },
                title = { Text(stringResource(R.string.deck_import)) },
                text = {
                    OutlinedTextField(text, { text = it.take(20_000) }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        placeholder = { Text(stringResource(R.string.deck_import_hint)) })
                },
                confirmButton = { TextButton(onClick = {
                    pasting = false; importing = true
                    scope.launch { notFound = repo.importDeckList(deckId, deck.game, text); importing = false }
                }, enabled = text.isNotBlank()) { Text(stringResource(R.string.lot_add)) } },
                dismissButton = { TextButton(onClick = { pasting = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        if (picking) {
            BinderCardPicker(owned.filter { it.game == deck.game }, emptySet(), s, onDismiss = { picking = false }, onSave = { rows ->
                picking = false
                scope.launch { repo.addOwnedToDeck(deckId, rows) }
            })
        }
        if (deleting) {
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text(stringResource(R.string.deck_delete)) },
                text = { Text(stringResource(R.string.deck_delete_confirm, deck.name)) },
                confirmButton = { TextButton(onClick = { deleting = false; scope.launch { repo.deleteDeck(deckId); onBack() } }) { Text(stringResource(R.string.deck_delete)) } },
                dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
    }
}
