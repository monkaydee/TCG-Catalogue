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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.WishCard
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.AmountField
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.ListEmptyState
import com.monkaydee.tcgcatalogue.ui.components.ListSummaryCard
import com.monkaydee.tcgcatalogue.ui.components.amountInput
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.components.parseAmount
import com.monkaydee.tcgcatalogue.ui.components.rememberNotificationPermissionRequest
import com.monkaydee.tcgcatalogue.ui.theme.Gain
import kotlinx.coroutines.launch

/** Current price of a wished card in the display currency, null when unknown. */
private fun WishCard.priceIn(s: AppSettings): Double? = price?.let { Money.convert(it, priceCurrency, s.currency, s.usdToEur) }

/** Target price of a wished card in the display currency, null when none is set. */
private fun WishCard.targetIn(s: AppSettings): Double? = targetPrice?.let { Money.convert(it, targetCurrency ?: s.currency, s.currency, s.usdToEur) }

/**
 * The cards the user wants: picture, set, printing, current price and target price (a
 * notification comes when the price drops to it), with the total cost of the whole list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(repo: CardRepository, onBack: () -> Unit) {
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val list by repo.wishlist.collectAsState(initial = null)
    var targetFor by remember { mutableStateOf<Long?>(null) }
    val askNotifications = rememberNotificationPermissionRequest()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.wish_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.lists_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val wishes = list ?: return@Scaffold
        if (wishes.isEmpty()) {
            ListEmptyState(
                Icons.Default.FavoriteBorder,
                stringResource(R.string.wish_empty_title),
                stringResource(R.string.wish_empty_text),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        val sorted = wishes.sortedByDescending { it.addedAt }
        val total = wishes.sumOf { it.priceIn(s) ?: 0.0 }
        val reached = wishes.count { w -> w.targetIn(s)?.let { t -> w.priceIn(s)?.let { it <= t } } == true }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListSummaryCard(stringResource(R.string.wish_total), Money.format(total, s.currency)) {
                    Text(pluralStringResource(R.plurals.wish_count, wishes.size, wishes.size), style = MaterialTheme.typography.bodySmall)
                    if (reached > 0) {
                        Text(pluralStringResource(R.plurals.wish_targets_reached, reached, reached), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(sorted, key = { it.id }) { w ->
                WishRow(
                    w, s,
                    onTarget = { targetFor = w.id },
                    onRemove = {
                        scope.launch {
                            repo.removeFromWishlist(w)
                            snackbar.showSnackbar(AppStrings.get(R.string.wish_removed, w.name))
                        }
                    },
                )
            }
        }
    }

    val editing = targetFor?.let { id -> list?.firstOrNull { it.id == id } }
    if (editing != null) {
        TargetDialog(editing, s, onDismiss = { targetFor = null }) { target ->
            targetFor = null
            if (target != null) askNotifications()
            scope.launch { repo.setWishTarget(editing, target, s.currency) }
        }
    }
}

@Composable
private fun WishRow(w: WishCard, s: AppSettings, onTarget: () -> Unit, onRemove: () -> Unit) {
    val price = w.priceIn(s)
    val target = w.targetIn(s)
    val atTarget = price != null && target != null && price <= target
    Card(Modifier.fillMaxWidth().clickable(onClick = onTarget)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardImage(w.imageUrl, Modifier.width(52.dp), thumb = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${w.setName} · ${w.number}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(w.variantLabel, w.rarity).joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        atTarget -> stringResource(R.string.wish_target_reached)
                        target != null -> stringResource(R.string.wish_target, Money.format(target, s.currency))
                        else -> stringResource(R.string.wish_no_target)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        atTarget -> Gain
                        target != null -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (atTarget) FontWeight.Bold else null,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    price?.let { Money.format(it, s.currency) } ?: stringResource(R.string.wish_no_price),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (atTarget) Gain else MaterialTheme.colorScheme.onSurface,
                )
                Row {
                    IconButton(onClick = onTarget) {
                        Icon(
                            if (target != null) Icons.Default.NotificationsActive else Icons.Default.NotificationAdd,
                            stringResource(R.string.wish_set_target),
                            tint = if (target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, stringResource(R.string.wish_remove), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Sets or clears the target price of a wished card, in the display currency. */
@Composable
private fun TargetDialog(w: WishCard, s: AppSettings, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember(w.id) { mutableStateOf(amountInput(w.targetIn(s))) }
    val amount = parseAmount(text)?.takeIf { it > 0 }
    val bad = text.isNotBlank() && amount == null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NotificationsActive, null) },
        title = { Text(stringResource(R.string.wish_target_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(w.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    w.priceIn(s)?.let { stringResource(R.string.wish_target_text, Money.format(it, s.currency)) }
                        ?: stringResource(R.string.wish_target_text_no_price),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AmountField(
                    text, { text = it }, stringResource(R.string.lists_amount_in, s.currency),
                    isError = bad, supportingText = if (bad) stringResource(R.string.lists_amount_invalid) else null,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(amount) }, enabled = !bad) { Text(stringResource(R.string.lists_save)) } },
        dismissButton = {
            Row {
                if (w.targetPrice != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.lists_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) }
            }
        },
    )
}
