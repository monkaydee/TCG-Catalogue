package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.Badges
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.ui.components.appBarColors

/** Collector challenges: earned ones in colour, the others with how far along they are. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(repo: CardRepository, onBack: () -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val cards by repo.cards.collectAsState(initial = emptyList())
    val sets by repo.sets.collectAsState(initial = emptyList())
    val binders by repo.binders.collectAsState(initial = emptyList())
    val sold by repo.sold.collectAsState(initial = emptyList())
    val sealed by repo.sealed.collectAsState(initial = emptyList())
    Scaffold(
        topBar = {
            TopAppBar(colors = appBarColors(), title = { Text(stringResource(R.string.badges_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } })
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        val badges = remember(cards, sets, binders, sold, sealed, s.usdToEur) {
            Badges.all(Badges.Input(cards, sets.associateBy { it.game to it.setId }, binders.size, sold.size, sealed.sumOf { it.quantity },
                cards.sumOf { Money.value(it, "EUR", s.usdToEur) }))
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.badges_summary, badges.count { it.earned }, badges.size), style = MaterialTheme.typography.titleMedium)
            }
            items(badges.sortedByDescending { it.earned }, key = { it.id }) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (b.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (b.earned) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock, null,
                                tint = if (b.earned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(b.title), fontWeight = FontWeight.Bold)
                            Text(stringResource(b.description), style = MaterialTheme.typography.bodySmall)
                            if (!b.earned) {
                                LinearProgressIndicator(progress = { b.progress.toFloat() / b.goal }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                Text("${b.progress} / ${b.goal}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
