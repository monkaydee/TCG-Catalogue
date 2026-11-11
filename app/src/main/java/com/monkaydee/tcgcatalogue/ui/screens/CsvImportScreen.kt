package com.monkaydee.tcgcatalogue.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.monkaydee.tcgcatalogue.data.CsvImport
import com.monkaydee.tcgcatalogue.data.ImportMatch
import com.monkaydee.tcgcatalogue.data.ImportRow
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.printingFor
import com.monkaydee.tcgcatalogue.ui.components.ActionStyle
import com.monkaydee.tcgcatalogue.ui.components.AppButton
import com.monkaydee.tcgcatalogue.ui.components.AppSelector
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.SelectorOption
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One imported line with its match and the user's choice. */
private class ImportLine(val row: ImportRow) {
    var match by mutableStateOf<ImportMatch?>(null)
    var choice by mutableStateOf(0)
    var include by mutableStateOf(false)
    var added by mutableStateOf(false)
}

/**
 * Import from other apps: pick a CSV or text export, every line is matched to a catalogue card,
 * sure matches are ticked, unsure ones show alternatives to choose from; then the ticked cards
 * are added with their quantity, condition, language, printing and price paid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(repo: CardRepository, onBack: () -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    var game by rememberSaveable { mutableStateOf(Game.POKEMON) }
    val lines = remember { mutableStateListOf<ImportLine>() }
    var matching by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun match() {
        matching = true
        scope.launch {
            val gate = Semaphore(3)
            kotlinx.coroutines.coroutineScope {
                lines.filter { it.match == null }.forEach { l ->
                    launch { gate.withPermit { val m = repo.matchImport(l.row, game); l.match = m; l.include = m.sure } }
                }
            }
            matching = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val rows = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.let(CsvImport::parse)
            }.getOrElse { emptyList() }
            lines.clear(); lines.addAll(rows.take(2000).map(::ImportLine))
            message = if (rows.isEmpty()) context.getString(R.string.csv_nothing_found) else null
            if (rows.isNotEmpty()) match()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.csv_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.csv_intro), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppSelector(stringResource(R.string.design_game), game, Game.entries.map { SelectorOption(it, it.short) },
                    { game = it; lines.forEach { l -> if (l.row.game == null) { l.match = null; l.include = false } }; if (lines.isNotEmpty()) match() },
                    Modifier.weight(1f), Icons.Outlined.Collections)
                AppButton(stringResource(R.string.csv_pick), { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                    style = ActionStyle.TONAL)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (lines.isNotEmpty()) {
                val sure = lines.count { it.match?.sure == true }
                val ticked = lines.filter { it.include && !it.added && it.match?.candidates?.isNotEmpty() == true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.csv_summary, lines.size, sure, lines.count { it.match?.candidates?.isEmpty() == true }),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (matching || adding) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                AppButton(stringResource(R.string.csv_add, ticked.sumOf { it.row.quantity }), {
                    adding = true
                    scope.launch {
                        var done = 0
                        ticked.forEach { l ->
                            val card = l.match!!.candidates[l.choice.coerceIn(l.match!!.candidates.indices)]
                            val paid = l.row.purchase?.let { p ->
                                when (val c = l.row.purchaseCurrency) { null, s.currency -> p; "EUR", "USD" -> Money.convert(p, c, s.currency, s.usdToEur); else -> null }
                            }
                            val ok = runCatching {
                                repo.add(card, card.printingFor(l.row), l.row.quantity, l.row.condition ?: s.defaultCondition, purchasePrice = paid, language = l.row.language ?: card.language ?: "EN")
                            }.isSuccess
                            if (ok) { l.added = true; done++ }
                        }
                        message = context.getString(R.string.csv_added, done)
                        adding = false
                    }
                }, Modifier.fillMaxWidth(), enabled = ticked.isNotEmpty() && !adding && !matching)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(lines) { _, l -> ImportLineRow(l) }
            }
        }
    }
}

@Composable
private fun ImportLineRow(l: ImportLine) {
    val m = l.match
    val card = m?.candidates?.getOrNull(l.choice)
    var choosing by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = (m?.candidates?.size ?: 0) > 1 && !l.added) { choosing = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(l.include || l.added, { l.include = it }, enabled = card != null && !l.added)
        Box(Modifier.width(40.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(3.dp))) {
            card?.imageUrl?.let { CardImage(it, Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text("${l.row.quantity}× ${l.row.name}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(l.row.set ?: l.row.setCode, l.row.number, l.row.condition, l.row.language, if (l.row.foil) "Foil" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                l.added -> stringResource(R.string.csv_status_added)
                m == null -> stringResource(R.string.csv_status_matching)
                card == null -> stringResource(R.string.csv_status_none)
                m.sure -> stringResource(R.string.csv_status_sure, "${card.setName} ${card.number}")
                else -> stringResource(R.string.csv_status_check, "${card.setName} ${card.number}", m.candidates.size)
            }
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = if (m?.sure == true || l.added) com.monkaydee.tcgcatalogue.ui.theme.Gain else MaterialTheme.colorScheme.tertiary)
        }
        DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
            m?.candidates?.forEachIndexed { i, c ->
                DropdownMenuItem(text = { Text("${c.name} · ${c.setName} ${c.number}") }, onClick = { l.choice = i; l.include = true; choosing = false })
            }
        }
    }
}
