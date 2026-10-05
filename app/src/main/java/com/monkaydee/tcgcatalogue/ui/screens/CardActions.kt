package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.components.AmountField
import com.monkaydee.tcgcatalogue.ui.components.BoundedStepper
import com.monkaydee.tcgcatalogue.ui.components.amountInput
import com.monkaydee.tcgcatalogue.ui.components.parseAmount
import com.monkaydee.tcgcatalogue.ui.components.signedMoney
import com.monkaydee.tcgcatalogue.ui.theme.Gain
import com.monkaydee.tcgcatalogue.ui.theme.Loss

/** An alert price of [card] converted to the display currency. */
private fun OwnedCard.alertShown(amount: Double?, s: AppSettings): Double? =
    amount?.let { Money.convert(it, alertCurrency ?: s.currency, s.currency, s.usdToEur) }

/**
 * Everything you can do with a card, in one bar: edit, sell, offer for trade (a toggle), price
 * alert (highlighted while one is set) and remove.
 */
@Composable
fun CardActionBar(
    card: OwnedCard,
    editLoading: Boolean,
    onEdit: () -> Unit,
    onSell: () -> Unit,
    onToggleTrade: (Boolean) -> Unit,
    onAlert: () -> Unit,
    onDelete: () -> Unit,
) {
    val hasAlert = card.alertAbove != null || card.alertBelow != null
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            ActionItem(Icons.Default.Edit, stringResource(R.string.lists_card_edit), onEdit, busy = editLoading)
            ActionItem(Icons.Default.Sell, stringResource(R.string.sold_sell), onSell)
            ActionItem(
                Icons.Default.SwapHoriz, stringResource(R.string.trade_toggle), { onToggleTrade(!card.forTrade) },
                selected = card.forTrade, toggle = true,
            )
            ActionItem(
                if (hasAlert) Icons.Default.NotificationsActive else Icons.Default.NotificationAdd,
                stringResource(R.string.alert_ui_short), onAlert, selected = hasAlert,
            )
            ActionItem(Icons.Default.Delete, stringResource(R.string.lists_card_remove), onDelete, danger = true)
        }
    }
}

@Composable
private fun RowScope.ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    busy: Boolean = false,
    danger: Boolean = false,
    toggle: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        selected -> colors.onSecondaryContainer
        danger -> colors.error
        else -> colors.onSurfaceVariant
    }
    val interaction = if (toggle) {
        Modifier.toggleable(value = selected, role = Role.Switch, enabled = !busy, onValueChange = { onClick() })
    } else {
        Modifier.clickable(role = Role.Button, enabled = !busy, onClick = onClick)
    }
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).then(interaction).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A pill behind the icon marks the switched-on actions, like a navigation bar indicator.
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) colors.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(icon, null, tint = tint)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (danger) colors.error else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The card's active price alerts as chips; tapping one opens the alert dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveAlerts(card: OwnedCard, s: AppSettings, onClick: () -> Unit) {
    val above = card.alertShown(card.alertAbove, s)
    val below = card.alertShown(card.alertBelow, s)
    if (above == null && below == null) return
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        above?.let {
            AssistChip(
                onClick = onClick,
                label = { Text(stringResource(R.string.alert_ui_active_above, Money.format(it, s.currency))) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(AssistChipDefaults.IconSize), tint = Gain) },
            )
        }
        below?.let {
            AssistChip(
                onClick = onClick,
                label = { Text(stringResource(R.string.alert_ui_active_below, Money.format(it, s.currency))) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingDown, null, Modifier.size(AssistChipDefaults.IconSize), tint = Loss) },
            )
        }
    }
}

/**
 * Sell copies of [card]: how many (1..owned) and the price per copy in the display currency,
 * prefilled with the current value. Shows the total and, when the purchase price is known, the profit.
 */
@Composable
fun SellDialog(card: OwnedCard, s: AppSettings, onDismiss: () -> Unit, onConfirm: (quantity: Int, pricePerCopy: Double, fees: Double) -> Unit) {
    var quantity by remember(card.id) { mutableIntStateOf(1) }
    var price by remember(card.id) { mutableStateOf(amountInput(Money.unit(card, s.currency, s.usdToEur))) }
    var fees by remember(card.id) { mutableStateOf("0") }
    val fee = parseAmount(fees)
    val each = parseAmount(price)
    val count = quantity.coerceIn(1, card.quantity.coerceAtLeast(1))
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Sell, null) },
        title = { Text(stringResource(R.string.sold_sell_title, card.name), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (card.quantity > 1) {
                    Text(stringResource(R.string.sold_quantity), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoundedStepper(count, { quantity = it }, min = 1, max = card.quantity)
                        Text(stringResource(R.string.sold_quantity_of, card.quantity), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                AmountField(
                    price, { price = it }, stringResource(R.string.sold_price_each, s.currency),
                    isError = price.isNotBlank() && each == null,
                )
                AmountField(fees, { fees = it }, stringResource(R.string.tools_fees), isError = fee == null)
                if (each != null) {
                    Text(stringResource(R.string.sold_total_line, Money.format(each * count, s.currency)), style = MaterialTheme.typography.titleMedium)

                }
            }
        },
        confirmButton = {
            TextButton(onClick = { each?.let { onConfirm(count, it, fee ?: 0.0) } }, enabled = each != null && fee != null) { Text(stringResource(R.string.sold_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) } },
    )
}

/**
 * Price alerts of [card]: notify above / below a value per copy, entered in the display
 * currency. Empty fields mean no alert; "Clear" removes both.
 */
@Composable
fun PriceAlertDialog(card: OwnedCard, s: AppSettings, onDismiss: () -> Unit, onSave: (above: Double?, below: Double?) -> Unit) {
    var above by remember(card.id) { mutableStateOf(amountInput(card.alertShown(card.alertAbove, s))) }
    var below by remember(card.id) { mutableStateOf(amountInput(card.alertShown(card.alertBelow, s))) }
    val a = parseAmount(above)?.takeIf { it > 0 }
    val b = parseAmount(below)?.takeIf { it > 0 }
    val aBad = above.isNotBlank() && a == null
    val bBad = below.isNotBlank() && b == null
    val orderBad = a != null && b != null && a <= b
    val active = card.alertAbove != null || card.alertBelow != null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NotificationsActive, null) },
        title = { Text(stringResource(R.string.alert_ui_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.alert_ui_text, Money.format(Money.unit(card, s.currency, s.usdToEur), s.currency)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AmountField(
                    above, { above = it }, stringResource(R.string.alert_ui_above, s.currency),
                    isError = aBad || orderBad, supportingText = if (aBad) stringResource(R.string.lists_amount_invalid) else null,
                )
                AmountField(
                    below, { below = it }, stringResource(R.string.alert_ui_below, s.currency),
                    isError = bBad || orderBad, supportingText = if (bBad) stringResource(R.string.lists_amount_invalid) else null,
                )
                if (orderBad) Text(stringResource(R.string.alert_ui_order), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(a, b) }, enabled = !aBad && !bBad && !orderBad) { Text(stringResource(R.string.lists_save)) }
        },
        dismissButton = {
            Row {
                if (active) TextButton(onClick = { onSave(null, null) }) { Text(stringResource(R.string.lists_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) }
            }
        },
    )
}
