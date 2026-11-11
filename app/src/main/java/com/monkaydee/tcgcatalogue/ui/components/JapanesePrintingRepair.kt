package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.attempt

@Composable
fun JapanesePrintingRepair(row: OwnedCard, settings: AppSettings, repo: CardRepository,
                           onSelect: (CardCandidate) -> Unit) {
    var options by remember(row.id, row.cardId) { mutableStateOf<List<CardCandidate>>(emptyList()) }
    var loading by remember(row.id, row.cardId) { mutableStateOf(true) }
    var failed by remember(row.id, row.cardId) { mutableStateOf(false) }
    var retry by remember(row.id, row.cardId) { mutableIntStateOf(0) }
    LaunchedEffect(row.id, row.cardId, retry) {
        loading = true
        val result = attempt {
            val source = repo.candidateFor(row) ?: error("Catalogue unavailable")
            repo.japanesePrintings(source)
        }
        options = result.getOrDefault(emptyList())
        failed = result.isFailure
        loading = false
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.jp_printing_repair_title), style = MaterialTheme.typography.titleMedium)
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.jp_printing_loading), style = MaterialTheme.typography.bodySmall)
            } else if (options.isNotEmpty()) {
                JapanesePrintingChoices(options, null, settings, repo, onSelect)
            } else {
                Text(stringResource(if (failed) R.string.jp_printing_failed else R.string.jp_printing_empty),
                    style = MaterialTheme.typography.bodySmall)
                TextButton({ retry++ }) { Text(stringResource(R.string.card_refresh)) }
            }
        }
    }
}
