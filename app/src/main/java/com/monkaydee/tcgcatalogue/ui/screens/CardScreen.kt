package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.ui.components.CONDITIONS
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.QuantityStepper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardScreen(repo: CardRepository, id: Long, onBack: () -> Unit) {
    val card by remember(id) { repo.observeCard(id) }.collectAsState(initial = null)
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(card) {
        if (card != null) loaded = true else if (loaded) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.name.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Remove from collection") } },
            )
        },
    ) { padding ->
        val c = card ?: return@Scaffold
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardImage(c.imageUrl, Modifier.fillMaxWidth(0.75f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${c.setName} · ${c.number}", style = MaterialTheme.typography.bodyMedium)
                Text(listOfNotNull(c.game.label, c.rarity, c.variantLabel).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(
                    Money.format(Money.value(c, s.currency, s.usdToEur), s.currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (c.price != null) {
                    Text(
                        "${Money.format(Money.unit(c, s.currency, s.usdToEur), s.currency)} per copy" +
                            " (${Money.format(c.price!!, c.priceCurrency)} on ${c.priceSource ?: "market"})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    c.priceUpdatedAt?.let {
                        Text("Updated ${android.text.format.DateUtils.getRelativeTimeSpanString(it)}", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text("No market price available", style = MaterialTheme.typography.bodySmall)
                }
                Text("Raw-card market price; graded copies are worth a different amount.", style = MaterialTheme.typography.labelSmall)
            }
            Text("Quantity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            QuantityStepper(c.quantity, { q -> scope.launch { repo.update(c.copy(quantity = q)) } })
            Text("Condition", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITIONS.forEach { cond ->
                    FilterChip(cond == c.condition, {
                        scope.launch { runCatching { repo.update(c.copy(condition = cond)) } }
                    }, { Text(cond) })
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove card?") },
            text = { Text("This removes all ${card?.quantity ?: 0} copies from your collection.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    card?.let { scope.launch { repo.delete(it) } }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
