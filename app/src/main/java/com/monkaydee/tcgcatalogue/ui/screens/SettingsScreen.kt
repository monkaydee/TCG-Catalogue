package com.monkaydee.tcgcatalogue.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.BuildConfig
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Backup
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.ui.components.CONDITIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: CardRepository, onRefresh: () -> Unit) {
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = backupJson.encodeToString(Backup.serializer(), repo.exportBackup())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            }
            Toast.makeText(context, if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                repo.importBackup(backupJson.decodeFromString(Backup.serializer(), text))
            }
            Toast.makeText(context, if (result.isSuccess) "Collection restored" else "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Display currency", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EUR", "USD").forEach { c -> FilterChip(s.currency == c, { scope.launch { repo.settings.setCurrency(c) } }, { Text(c) }) }
            }
            Text("1 USD = %.4f EUR (ECB rate, refreshed with prices)".format(s.usdToEur), style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Games", style = MaterialTheme.typography.titleSmall)
            Text(
                "The scanner looks for these games. Dragon Ball, Union Arena, Weiss Schwarz and Naruto download a card list (up to a few MB, refreshed daily).",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Game.entries.forEach { g ->
                    FilterChip(g in s.enabledGames, {
                        val next = if (g in s.enabledGames) s.enabledGames - g else s.enabledGames + g
                        if (next.isNotEmpty()) scope.launch { repo.settings.setEnabledGames(next) }
                    }, { Text(g.short) })
                }
            }

            HorizontalDivider()
            Text("Price source (Pokémon, Magic & One Piece)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PriceSource.CARDMARKET, PriceSource.TCGPLAYER).forEach { p ->
                    FilterChip(s.pokemonSource == p, {
                        scope.launch { repo.settings.setPokemonSource(p); onRefresh() }
                    }, { Text("${p.label} (${p.currency})") })
                }
            }
            Text(
                "Cardmarket trend price for European prices, TCGplayer market price for US prices. For One Piece you pick the exact Cardmarket listing (set and V.1/V.2…) of your copy. The other games only have TCGplayer prices. " +
                    "When the two markets disagree by more than 3×, one of them is linked to the wrong card and the app uses the other one (the card shows a note). " +
                    "Graded cards are priced from PriceCharting's sold listings.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick add while scanning", style = MaterialTheme.typography.titleSmall)
                    Text("Adds clearly recognised cards without asking, so you can scan a stack quickly.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(s.quickAdd, { v -> scope.launch { repo.settings.setQuickAdd(v) } })
            }
            Text("Default condition", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITIONS.forEach { c -> FilterChip(s.defaultCondition == c, { scope.launch { repo.settings.setDefaultCondition(c) } }, { Text(c) }) }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Full screen", style = MaterialTheme.typography.titleSmall)
                    Text("Hides the status and navigation bars. Swipe from the top or bottom edge to show them.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(s.fullScreen, { v -> scope.launch { repo.settings.setFullScreen(v) } })
            }

            HorizontalDivider()
            Text("Prices", style = MaterialTheme.typography.titleSmall)
            Text("Prices refresh automatically once a day, which also records the value history.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRefresh) { Text("Refresh prices now") }

            HorizontalDivider()
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            Text("Your collection is stored only on this phone. Export a backup before uninstalling or switching phones.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("tcg-catalogue-${LocalDate.now()}.json") }) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Import (replaces)") }
            }

            HorizontalDivider()
            Text("About", style = MaterialTheme.typography.titleSmall)
            Text(
                "Version ${BuildConfig.VERSION_NAME}\n" +
                    "Pokémon data & prices: TCGdex (tcgdex.dev)\n" +
                    "One Piece data & prices: OPTCG API (optcgapi.com)\n" +
                    "Magic data & prices: Scryfall (scryfall.com)\n" +
                    "Dragon Ball, Union Arena, Weiss Schwarz, Naruto: TCGplayer via TCGCSV (tcgcsv.com)\n" +
                    "Graded prices: PriceCharting (pricecharting.com)\n" +
                    "Exchange rates: Frankfurter (ECB)\n" +
                    "Text recognition runs on-device with Google ML Kit.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
