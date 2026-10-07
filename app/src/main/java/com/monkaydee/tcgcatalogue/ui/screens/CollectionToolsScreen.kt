package com.monkaydee.tcgcatalogue.ui.screens

import com.monkaydee.tcgcatalogue.data.CardLanguage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.*
import com.monkaydee.tcgcatalogue.data.db.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CollectionToolsScreen(repo: CardRepository, onCard: (Long) -> Unit, onReview: () -> Unit) {
    val cards by repo.cards.collectAsState(emptyList())
    val lots by repo.costLots.collectAsState(emptyList())
    val submissions by repo.submissions.collectAsState(emptyList())
    val s by repo.settings.flow.collectAsState(AppSettings())
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { message = runCatching { CollectionExport.csv(context, uri, cards, lots, s); context.getString(R.string.tools_complete) }.getOrElse { it.message } }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { message = runCatching { CollectionExport.pdf(context, uri, cards, lots, s); context.getString(R.string.tools_complete) }.getOrElse { it.message } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.headlineMedium)
        val known = cards.mapNotNull { card -> CostLedger.pnl(card, lots.filter { it.cardRowId == card.id }, s.currency, s.usdToEur) }
        Text(stringResource(R.string.tools_pnl) + ": " + Money.format(known.sum(), s.currency), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.tools_unknown) + ": " + (cards.size - known.size))
        Text(stringResource(R.string.tools_export), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csv.launch("cardnavo-${java.time.LocalDate.now()}.csv") }) { Text("CSV") }
            OutlinedButton(onClick = { pdf.launch("cardnavo-${java.time.LocalDate.now()}.pdf") }) { Text("PDF") }
        }
        message?.let { Text(it) }
        OutlinedButton(onClick = onReview) { Text(stringResource(R.string.tools_review)) }
        LearningPanel(repo)
        Text(stringResource(R.string.tools_grading), style = MaterialTheme.typography.titleMedium)
        submissions.forEach { sub ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text((cards.firstOrNull { it.id == sub.cardRowId }?.name ?: sub.reference) + " · " + sub.company)
                Text(sub.reference)
                Text(stringResource(submissionStatus(sub.status)))
                Text(sub.notes)
                OutlinedButton(onClick = { onCard(sub.cardRowId) }, enabled = cards.any { it.id == sub.cardRowId }) { Text(stringResource(R.string.tools_costs)) }
            } }
        }
        Text(stringResource(R.string.tools_costs), style = MaterialTheme.typography.titleMedium)
        cards.forEach { card -> TextButton(onClick = { onCard(card.id) }) { Text(card.name + " · " + card.condition + " · ×" + card.quantity) } }
    }
}

fun submissionStatus(status: String): Int = when (status) {
    "SHIPPED" -> R.string.tools_shipped; "RECEIVED" -> R.string.tools_received
    "GRADING" -> R.string.tools_grading_status; "RETURNED" -> R.string.tools_returned
    "CANCELLED" -> R.string.tools_cancelled; else -> R.string.tools_preparing
}

@Composable
fun CardLedgerPanel(repo: CardRepository, card: OwnedCard, s: AppSettings) {
    val all by repo.costLots.collectAsState(emptyList())
    val subs by repo.submissions.collectAsState(emptyList())
    val lots = all.filter { it.cardRowId == card.id }
    val scope = rememberCoroutineScope()
    var edit by remember(card.id) { mutableStateOf<CostLot?>(null) }
    var submission by remember(card.id) { mutableStateOf<GradingSubmission?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.tools_costs), style = MaterialTheme.typography.titleMedium)
    val pnl = CostLedger.pnl(card, lots, s.currency, s.usdToEur)
    Text(stringResource(R.string.tools_pnl) + ": " + (pnl?.let { Money.format(it, s.currency) } ?: stringResource(R.string.tools_unknown)))
    lots.forEach { lot ->
        TextButton(onClick = { edit = lot }) {
            Text("×${lot.quantity} · " + (CostLedger.basis(lot, lot.currency, s.usdToEur)?.let { Money.format(it, lot.currency) } ?: stringResource(R.string.tools_unknown)))
        }
    }
    TextButton(onClick = { submission = GradingSubmission(cardRowId = card.id, company = card.grader ?: "PSA") }) { Text(stringResource(R.string.tools_grading)) }
    subs.filter { it.cardRowId == card.id }.forEach { sub -> TextButton(onClick = { submission = sub }) { Text(sub.company + " · " + sub.reference + " · " + stringResource(submissionStatus(sub.status))) } }
    Text(stringResource(R.string.tools_evidence), style = MaterialTheme.typography.titleSmall)
    Text(listOfNotNull(CardLanguage.displayCode(card.language), card.variantLabel, card.condition, card.priceSource,
        card.priceUpdatedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }).joinToString(" · "))
    if (card.price != null) Text(stringResource(if (System.currentTimeMillis() - (card.priceUpdatedAt ?: 0) > 7 * 86400000L) R.string.tools_stale else R.string.tools_current))
    card.priceNote?.let { Text(it) }
    TextButton(onClick = { scope.launch { runCatching { SharedLearning.reportPrice(repo, card) }.onSuccess { error = null }.onFailure { error = it.message } } }) { Text(stringResource(R.string.tools_price_challenge)) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    edit?.let { original -> CostDialog(original, onDismiss = { edit = null }) { changed -> scope.launch {
        runCatching { repo.editCostLot(original, changed) }.onSuccess { edit = null }.onFailure { error = it.message }
    } } }
    submission?.let { sub -> SubmissionDialog(sub, onDismiss = { submission = null }) { updated -> scope.launch {
        runCatching { repo.saveSubmission(updated) }.onSuccess { submission = null }.onFailure { error = it.message }
    } } }
}

@Composable
private fun CostDialog(lot: CostLot, onDismiss: () -> Unit, onSave: (CostLot) -> Unit) {
    var qty by remember(lot) { mutableStateOf(lot.quantity.toString()) }
    var purchase by remember(lot) { mutableStateOf(lot.purchase?.toString().orEmpty()) }
    var grade by remember(lot) { mutableStateOf(lot.grading?.toString().orEmpty()) }
    var shipping by remember(lot) { mutableStateOf(lot.shipping?.toString().orEmpty()) }
    var tax by remember(lot) { mutableStateOf(lot.tax?.toString().orEmpty()) }
    var currency by remember(lot) { mutableStateOf(lot.currency) }
    fun amount(value: String) = value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1e9 }
    val valid = qty.toIntOrNull() in 1..lot.quantity && listOf(purchase, grade, shipping, tax).all { it.isBlank() || amount(it) != null }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.tools_costs)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tools_blank))
            OutlinedTextField(qty, { qty = it }, label = { Text(stringResource(R.string.card_quantity)) }, singleLine = true)
            Row { listOf("USD", "EUR").forEach { c -> FilterChip(currency == c, { currency = c }, label = { Text(c) }) } }
            OutlinedTextField(purchase, { purchase = it }, label = { Text(stringResource(R.string.add_purchase_price, currency)) }, singleLine = true)
            OutlinedTextField(grade, { grade = it }, label = { Text(stringResource(R.string.tools_grading_cost)) }, singleLine = true)
            OutlinedTextField(shipping, { shipping = it }, label = { Text(stringResource(R.string.tools_shipping)) }, singleLine = true)
            OutlinedTextField(tax, { tax = it }, label = { Text(stringResource(R.string.tools_tax)) }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(lot.copy(quantity = qty.toInt(), purchase = amount(purchase), grading = amount(grade), shipping = amount(shipping), tax = amount(tax), currency = currency)) }) { Text(stringResource(R.string.card_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.card_cancel)) } })
}

@Composable
private fun SubmissionDialog(sub: GradingSubmission, onDismiss: () -> Unit, onSave: (GradingSubmission) -> Unit) {
    var company by remember(sub) { mutableStateOf(sub.company) }; var ref by remember(sub) { mutableStateOf(sub.reference) }
    var status by remember(sub) { mutableStateOf(sub.status) }; var notes by remember(sub) { mutableStateOf(sub.notes) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.tools_grading)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { listOf("PSA", "CGC", "BGS", "SGC").forEach { c -> FilterChip(company == c, { company = c }, label = { Text(c) }) } }
            OutlinedTextField(ref, { ref = it.take(200) }, label = { Text(stringResource(R.string.tools_reference)) })
            listOf("PREPARING", "SHIPPED", "RECEIVED", "GRADING", "RETURNED", "CANCELLED").forEach { v -> FilterChip(status == v, { status = v }, label = { Text(stringResource(submissionStatus(v))) }) }
            OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text(stringResource(R.string.tools_notes)) })
        }
    }, confirmButton = { TextButton(onClick = { onSave(sub.copy(company = company, reference = ref, status = status, notes = notes)) }) { Text(stringResource(R.string.card_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.card_cancel)) } })
}
