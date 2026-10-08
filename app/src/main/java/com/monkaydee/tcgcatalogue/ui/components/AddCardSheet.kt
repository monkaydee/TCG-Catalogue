package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.monkaydee.tcgcatalogue.ui.components.StandardButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.monkaydee.tcgcatalogue.ui.components.StandardOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.QrCodeScanner
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.data.remote.attempt
import com.monkaydee.tcgcatalogue.data.remote.Price
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.ui.AppStrings

val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")
val GRADERS = listOf("PSA", "BGS", "CGC", "SGC", "TAG", "ACE", "AOG", "GSG", "PI", "Other")
val GRADES = listOf("10", "9.5", "9", "8.5", "8", "7.5", "7", "6", "5", "4", "3", "2", "1")

fun Price?.display(s: AppSettings): String =
    this?.let { Money.format(Money.convert(it.amount, it.currency, s.currency, s.usdToEur), s.currency) } ?: AppStrings.get(R.string.add_no_price)

/** The special 10s that are priced separately. */
fun qualifiersFor(grader: String?): List<String> = when (grader) {
    "BGS" -> listOf("Black Label")
    "CGC" -> listOf("Pristine", "Perfect")
    else -> emptyList()
}

/** Everything chosen in the add / edit sheet. */
data class AddRequest(
    val card: CardCandidate,
    val variant: Variant,
    val quantity: Int,
    val condition: String,
    val grade: GradeInfo?,
    val listing: CardmarketApi.Listing?,
    /** Price paid per copy, in the display currency, if entered. */
    val purchasePrice: Double?,
    /** The user's own value per copy, in the display currency, overriding the market price. */
    val manualValue: Double? = null,
    /** Language of the copy ("EN", "DE", "JA" …). */
    val language: String = "EN",
)

/** Card languages, named in the app's language ("Japanese" in English, "Japanisch" in German). */
val CARD_LANGUAGES: List<Pair<String, String>>
    get() = listOf("EN", "DE", "FR", "IT", "ES", "PT", "NL", "PL", "JA", "KO", "ZH").map { code ->
        code to (java.util.Locale(code.lowercase()).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) } + if (code == "JA") " (JP)" else "")
    }

/**
 * Lets the user confirm the recognised card and pick the printing, condition (or grading
 * company and grade for a slab) and quantity. [initialGrade] pre-fills what was read from a slab label.
 * With [initial] it edits a card already in the collection ([confirmLabel] "Save"), and
 * [onChangeCard] offers to replace it with a different card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(
    candidates: List<CardCandidate>,
    settings: AppSettings,
    repo: CardRepository,
    initialGrade: GradeInfo? = null,
    initial: OwnedCard? = null,
    confirmLabel: String = stringResource(R.string.add_confirm),
    onChangeCard: (() -> Unit)? = null,
    onAdd: (AddRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val startGrade = initialGrade ?: initial?.takeIf { it.graded }?.let { GradeInfo(it.grader, it.grade, it.gradeQualifier, it.certNumber) }
    if (candidates.isEmpty()) return
    var selected by remember(candidates) { mutableIntStateOf(0) }
    val sourceCard = candidates[selected.coerceIn(candidates.indices)]
    var language by remember(sourceCard) { mutableStateOf(initial?.language ?: sourceCard.language ?: if (sourceCard.cardId.startsWith("ja:")) "JA" else "EN") }
    var japaneseSelection by remember(sourceCard) { mutableStateOf<CardCandidate?>(null) }
    var japaneseOptions by remember(sourceCard) { mutableStateOf<List<CardCandidate>>(emptyList()) }
    var japaneseLoading by remember(sourceCard) { mutableStateOf(false) }
    var japaneseFailed by remember(sourceCard) { mutableStateOf(false) }
    var japaneseRetry by remember(sourceCard) { mutableIntStateOf(0) }
    val card = if (language == "JA") japaneseSelection ?: sourceCard else sourceCard
    val needsJapanesePrinting = repo.needsJapanesePrinting(card, language)
    LaunchedEffect(sourceCard, language, japaneseRetry) {
        if (!repo.needsJapanesePrinting(sourceCard, language)) return@LaunchedEffect
        japaneseLoading = true
        japaneseFailed = false
        try {
            val result = attempt { repo.japanesePrintings(sourceCard) }
            japaneseOptions = result.getOrDefault(emptyList())
            japaneseFailed = result.isFailure
        } finally { japaneseLoading = false }
    }
    var variantKey by remember(card) {
        mutableStateOf(initial?.variant?.takeIf { k -> card.variants.any { it.key == k } } ?: card.defaultVariant.key)
    }
    val variant = card.variants.firstOrNull { it.key == variantKey } ?: card.variants.first()
    var quantity by remember(card) { mutableIntStateOf(initial?.quantity ?: 1) }
    var condition by remember { mutableStateOf(initial?.condition?.takeIf { it in CONDITIONS } ?: settings.defaultCondition) }
    var graded by remember(startGrade) { mutableStateOf(startGrade != null) }
    // A slab whose company couldn't be read starts on "Other" so the user picks the company.
    var grader by remember(startGrade) { mutableStateOf(startGrade?.let { it.grader ?: "Other" } ?: "PSA") }
    var grade by remember(startGrade) { mutableStateOf(startGrade?.grade ?: "10") }
    var qualifier by remember(startGrade) { mutableStateOf(startGrade?.qualifier) }
    var cert by remember(startGrade) { mutableStateOf(startGrade?.cert.orEmpty()) }
    var myValue by remember {
        mutableStateOf(
            initial?.manualPrice?.let { "%.2f".format(Money.convert(it, initial.manualCurrency ?: settings.currency, settings.currency, settings.usdToEur)) }.orEmpty(),
        )
    }
    // Stored in the card's price currency, edited in the display currency.
    var paid by remember {
        mutableStateOf(
            initial?.purchasePrice?.let { "%.2f".format(Money.convert(it, initial.priceCurrency, settings.currency, settings.usdToEur)) }.orEmpty(),
        )
    }
    val gradeInfo = GradeInfo(grader, grade, qualifier.takeIf { grade == "10" && it in qualifiersFor(grader) }, cert.ifBlank { null })

    // One Piece: Cardmarket's listing of the exact print (original, reprint, alt art, ...).
    var listings by remember(card) { mutableStateOf<List<CardmarketApi.Listing>>(emptyList()) }
    var listing by remember(card) { mutableStateOf<CardmarketApi.Listing?>(null) }
    var listingPicked by remember(card) { mutableStateOf(false) }
    LaunchedEffect(card) {
        listings = repo.cardmarketListings(card)
        // Editing: keep the listing the card already has.
        initial?.marketProductId?.let { id -> listings.firstOrNull { it.productId == id } }?.let { listing = it; listingPicked = true }
    }
    LaunchedEffect(listings, variant) {
        if (!listingPicked) listing = repo.defaultListing(listings, variant, settings)
    }
    val raw = repo.rawPrice(card, variant, settings, listing)
    // Price for the chosen condition (NM is the market price itself; the others are looked up).
    var conditionQuote by remember { mutableStateOf<Price?>(null) }
    var conditionLoading by remember { mutableStateOf(false) }
    LaunchedEffect(card, variant, condition, graded, listing, language) {
        if (needsJapanesePrinting) {
            conditionLoading = false
            conditionQuote = null
            return@LaunchedEffect
        }
        // NM English is the market price itself, unless the card databases have none (then the price server is asked)
        if (graded || (condition == "NM" && raw != null && language == "EN")) {
            conditionQuote = raw
            conditionLoading = false
            return@LaunchedEffect
        }
        conditionLoading = true
        conditionQuote = null
        conditionQuote = attempt { repo.conditionPrice(card, variant, condition, settings, listing, language) }.getOrNull()
        conditionLoading = false
    }
    var gradedQuote by remember { mutableStateOf<Price?>(null) }
    var gradedProblem by remember { mutableStateOf<String?>(null) }
    var gradedLoading by remember { mutableStateOf(false) }
    LaunchedEffect(card, variant, graded, gradeInfo.grader, gradeInfo.grade, gradeInfo.qualifier, language) {
        if (!graded) return@LaunchedEffect
        if (needsJapanesePrinting) {
            gradedLoading = false
            gradedQuote = null
            gradedProblem = AppStrings.get(R.string.jp_printing_required)
            return@LaunchedEffect
        }
        gradedLoading = true
        gradedQuote = null
        gradedProblem = null
        // attempt() lets cancellation through, so a lookup for the previous grade can't overwrite this one.
        val result = attempt { repo.gradedLookup(card, variant, gradeInfo, language) }
        gradedQuote = result.getOrNull()?.price
        gradedProblem = result.getOrNull()?.problem ?: result.exceptionOrNull()?.message
        gradedLoading = false
    }
    val shown = if (graded) gradedQuote else conditionQuote
    val loading = if (graded) gradedLoading else conditionLoading
    val gradedMissing = graded && !gradedLoading && gradedQuote == null
    val note = if (gradedMissing) stringResource(R.string.add_graded_missing, gradedProblem ?: stringResource(R.string.graded_no_quote_reason)) else shown?.note

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (candidates.size > 1) {
                Text(pluralStringResource(R.plurals.add_possible_matches, candidates.size, candidates.size), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(candidates) { i, c ->
                        Card(
                            Modifier.width(96.dp).clickable { selected = i },
                            border = if (i == selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(),
                        ) {
                            CardImage(c.defaultVariant.imageUrl ?: c.imageUrl, thumb = true)
                            Text(c.name.takeIf { candidates.any { o -> o.name != c.name } } ?: c.setName,
                                style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (graded) {
                    GradedSlab(
                        variant.imageUrl ?: card.imageUrl, gradeInfo.grader, gradeInfo.grade, gradeInfo.qualifier,
                        card.name, "${card.setName} #${card.number.substringBefore('/')}", gradeInfo.cert, Modifier.width(150.dp),
                    )
                } else {
                    CardImage(variant.imageUrl ?: card.imageUrl, Modifier.width(140.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(card.setName, style = MaterialTheme.typography.bodyMedium)
                    Text("${card.number}${card.rarity?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (loading) "…" else shown.display(settings),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (graded) "${gradeInfo.label} · ${if (gradedMissing) stringResource(R.string.add_no_price) else shown?.source?.label ?: stringResource(R.string.add_looking_up_sales)}" else stringResource(R.string.add_price_details, variant.label, condition, shown?.source?.label.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                }
            }
            if (repo.needsJapanesePrinting(sourceCard, language)) {
                if (needsJapanesePrinting) Text(stringResource(R.string.jp_printing_required),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (japaneseLoading) Text(stringResource(R.string.jp_printing_loading), style = MaterialTheme.typography.bodySmall)
                if (japaneseOptions.isNotEmpty()) JapanesePrintingChoices(japaneseOptions, japaneseSelection?.cardId,
                    settings, repo) { japaneseSelection = it }
                if (!japaneseLoading && japaneseOptions.isEmpty()) {
                    Text(stringResource(if (japaneseFailed) R.string.jp_printing_failed else R.string.jp_printing_empty), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { japaneseRetry++ }) { Text(stringResource(R.string.card_refresh)) }
                }
            }
            card.warning?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            if (card.variants.size > 1) {
                Text(
                    when {
                        initial == null && card.printingCheck -> stringResource(R.string.add_printing_check)
                        initial == null && card.preferredVariant != null -> stringResource(R.string.add_printing_recognised)
                        else -> stringResource(R.string.add_printing)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (initial == null && card.printingCheck) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.variants.forEach { v ->
                        FilterChip(
                            selected = v.key == variant.key,
                            onClick = { variantKey = v.key },
                            label = { Text("${v.label} · ${repo.rawPrice(card, v, settings).display(settings)} NM") },
                        )
                    }
                }
            }
            if (listings.isNotEmpty() && settings.pokemonSource == PriceSource.CARDMARKET) {
                Text(stringResource(R.string.add_cardmarket_listing), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listings.forEach { l ->
                        FilterChip(
                            selected = l.productId == listing?.productId,
                            onClick = { listing = l; listingPicked = true },
                            label = { Text("${l.label} · ${l.price?.let { Money.format(Money.convert(it, "EUR", settings.currency, settings.usdToEur), settings.currency) } ?: "–"}") },
                        )
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!graded, { graded = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.add_raw)) }
                SegmentedButton(graded, { graded = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.add_graded)) }
            }
            if (graded) {
                Text(stringResource(R.string.add_grading_company), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GRADERS.forEach { g -> FilterChip(g == grader, { grader = g }, { Text(if (g == "Other") stringResource(R.string.add_grader_other) else g) }) }
                }
                Text(stringResource(R.string.add_grade), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GRADES.forEach { g -> FilterChip(g == grade, { grade = g }, { Text(g) }) }
                }
                if (grade == "10" && qualifiersFor(grader).isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualifiersFor(grader).forEach { q ->
                            FilterChip(qualifier == q, { qualifier = if (qualifier == q) null else q }, { Text(q) })
                        }
                    }
                }
                val scanContext = androidx.compose.ui.platform.LocalContext.current
                val scanScope = androidx.compose.runtime.rememberCoroutineScope()
                OutlinedTextField(
                    value = cert,
                    onValueChange = { cert = it.filter(Char::isLetterOrDigit).take(14) },
                    label = { Text(stringResource(R.string.add_cert_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        // The label's barcode or QR code fills in the certificate (and the company and, for PSA, the grade).
                        androidx.compose.material3.IconButton(onClick = {
                            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(scanContext).startScan()
                                .addOnSuccessListener { code ->
                                    val found = code.rawValue?.let(com.monkaydee.tcgcatalogue.scan.SlabCode::parse)
                                    if (found == null) {
                                        android.widget.Toast.makeText(scanContext, scanContext.getString(R.string.slab_scan_unreadable), android.widget.Toast.LENGTH_LONG).show()
                                        return@addOnSuccessListener
                                    }
                                    cert = found.cert
                                    found.grader?.let { grader = it }
                                    if ((found.grader ?: grader) == "PSA") scanScope.launch {
                                        runCatching { repo.verifyCert(found.cert) }.getOrNull()?.let { c ->
                                            com.monkaydee.tcgcatalogue.scan.SlabCode.psaGrade(c.grade)?.let { g -> grade = g }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    android.widget.Toast.makeText(scanContext, scanContext.getString(R.string.slab_scan_unavailable), android.widget.Toast.LENGTH_LONG).show()
                                }
                        }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.QrCodeScanner, stringResource(R.string.slab_scan)) }
                    },
                )
            } else {
                Text(stringResource(R.string.add_condition), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CONDITIONS.forEach { c -> FilterChip(selected = c == condition, onClick = { condition = c }, label = { Text(c) }) }
                }
            }
            Text(stringResource(R.string.add_language), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CARD_LANGUAGES.forEach { (code, name) -> FilterChip(selected = code == language, onClick = { language = code }, label = { Text(name) }) }
            }
            if (initial == null) OutlinedTextField(
                value = paid,
                onValueChange = { paid = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10) },
                label = { Text(stringResource(R.string.add_purchase_price, settings.currency)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = myValue,
                onValueChange = { myValue = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10) },
                label = { Text(stringResource(R.string.add_my_value, settings.currency)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (onChangeCard != null) {
                TextButton(onClick = onChangeCard) { Text(stringResource(R.string.add_wrong_card)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                QuantityStepper(quantity, { quantity = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.add_cancel)) }
                    Button(enabled = !needsJapanesePrinting, onClick = {
                        val price = paid.replace(',', '.').toDoubleOrNull()
                        val own = myValue.replace(',', '.').toDoubleOrNull()
                        onAdd(AddRequest(card, variant, quantity, condition, gradeInfo.takeIf { graded }, listing, price, own, language))
                    }) { Text(confirmLabel) }
                }
            }
        }
    }
}
