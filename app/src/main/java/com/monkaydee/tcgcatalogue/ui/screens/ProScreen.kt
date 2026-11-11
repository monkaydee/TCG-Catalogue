package com.monkaydee.tcgcatalogue.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.billing.ProBilling
import com.monkaydee.tcgcatalogue.data.Pro
import com.monkaydee.tcgcatalogue.ui.components.appBarColors

/** Opens the Pro page from wherever a locked tool sits. */
val LocalOpenPro = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

private val features = listOf(
    R.string.pro_feature_export,
    R.string.pro_feature_import,
    R.string.pro_feature_decks,
    R.string.pro_feature_binders,
    R.string.pro_feature_grading,
    R.string.pro_feature_set_cost,
)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** What CardNavo Pro adds, its price from Google Play, and the buttons to buy or restore it. */
@Composable
fun ProScreen(onBack: () -> Unit) {
    val state by Pro.state.collectAsState()
    val context = LocalContext.current
    // A notice belongs to the visit that caused it.
    DisposableEffect(Unit) { onDispose { Pro.update { it.copy(notice = null) } } }
    ProContent(
        state = state,
        onBack = onBack,
        onBuy = { context.activity()?.let { ProBilling.instance?.buy(it) } },
        onRestore = { ProBilling.instance?.restore() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProContent(state: Pro.State, onBack: () -> Unit, onBuy: () -> Unit, onRestore: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(colors = appBarColors(), title = { Text(stringResource(R.string.pro_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } })
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WorkspacePremium, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.pro_tagline), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    features.forEach { feature ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(feature), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Text(stringResource(R.string.pro_free_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val notice = when (state.notice) {
                Pro.Notice.PURCHASED -> null // shown as the owned state below
                Pro.Notice.PENDING -> R.string.pro_pending
                Pro.Notice.FAILED -> R.string.pro_failed
                Pro.Notice.NOTHING_TO_RESTORE -> R.string.pro_restore_none
                Pro.Notice.STORE_UNAVAILABLE -> R.string.pro_store_unavailable
                null -> when {
                    state.pending -> R.string.pro_pending
                    state.enforced && !state.owned && state.store == Pro.Store.UNAVAILABLE -> R.string.pro_store_unavailable
                    else -> null
                }
            }
            if (state.unlocked) {
                Text(stringResource(R.string.pro_owned), Modifier.testTag("pro_owned"), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            } else {
                notice?.let { Text(stringResource(it), Modifier.testTag("pro_notice"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Button(onClick = onBuy, enabled = !state.pending, modifier = Modifier.fillMaxWidth().testTag("pro_buy")) {
                    Text(state.price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_buy_no_price))
                }
                Text(stringResource(R.string.pro_one_time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRestore, modifier = Modifier.testTag("pro_restore")) { Text(stringResource(R.string.pro_restore)) }
            }
        }
    }
}

/** Stands where a Pro tool would be for a free user; opens the Pro page. */
@Composable
fun ProLock(title: String, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = LocalOpenPro.current, modifier = modifier) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
        Text(stringResource(R.string.pro_locked, title), Modifier.padding(start = 8.dp))
    }
}
