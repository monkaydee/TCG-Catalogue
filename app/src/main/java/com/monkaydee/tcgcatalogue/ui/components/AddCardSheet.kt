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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.Price
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.scan.GradeInfo

val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")
val GRADERS = listOf("PSA", "BGS", "CGC", "SGC", "TAG", "ACE", "AOG", "GSG", "PI", "Other")
val GRADES = listOf("10", "9.5", "9", "8.5", "8", "7.5", "7", "6", "5", "4", "3", "2", "1")

fun Price?.display(s: AppSettings): String =
    this?.let { Money.format(Money.convert(it.amount, it.currency, s.currency, s.usdToEur), s.currency) } ?: "no price"

/** The special 10s that are priced separately. */
fun qualifiersFor(grader: String?): List<String> = when (grader) {
    "BGS" -> listOf("Black Label")
    "CGC" -> listOf("Pristine")
    else -> emptyList()
}

/**
 * Lets the user confirm the recognised card and pick the printing, condition (or grading
 * company and grade for a slab) and quantity. [initialGrade] pre-fills what was read from a slab label.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(
    candidates: List<CardCandidate>,
    settings: AppSettings,
    repo: CardRepository,
    initialGrade: GradeInfo? = null,
    onAdd: (CardCandidate, Variant, Int, String, GradeInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (candidates.isEmpty()) return
    var selected by remember(candidates) { mutableIntStateOf(0) }
    val card = candidates[selected.coerceIn(candidates.indices)]
    var variantKey by remember(card) { mutableStateOf(card.variants.first().key) }
    val variant = card.variants.firstOrNull { it.key == variantKey } ?: card.variants.first()
    var quantity by remember(card) { mutableIntStateOf(1) }
    var condition by remember { mutableStateOf(settings.defaultCondition) }
    var graded by remember(initialGrade) { mutableStateOf(initialGrade != null) }
    var grader by remember(initialGrade) { mutableStateOf(initialGrade?.grader ?: "PSA") }
    var grade by remember(initialGrade) { mutableStateOf(initialGrade?.grade ?: "10") }
    var qualifier by remember(initialGrade) { mutableStateOf(initialGrade?.qualifier) }
    var cert by remember(initialGrade) { mutableStateOf(initialGrade?.cert.orEmpty()) }
    val gradeInfo = GradeInfo(grader, grade, qualifier.takeIf { grade == "10" && it in qualifiersFor(grader) }, cert.ifBlank { null })

    val raw = repo.rawPrice(card, variant, settings)
    // Price for the chosen condition (NM is the market price itself; the others are looked up).
    var conditionQuote by remember { mutableStateOf<Price?>(null) }
    var conditionLoading by remember { mutableStateOf(false) }
    LaunchedEffect(card, variant, condition, graded) {
        if (graded || condition == "NM") {
            conditionQuote = raw
            return@LaunchedEffect
        }
        conditionLoading = true
        conditionQuote = runCatching { repo.conditionPrice(card, variant, condition, settings) }.getOrNull() ?: raw
        conditionLoading = false
    }
    var gradedQuote by remember { mutableStateOf<Price?>(null) }
    var gradedLoading by remember { mutableStateOf(false) }
    LaunchedEffect(card, variant, graded, gradeInfo.grader, gradeInfo.grade, gradeInfo.qualifier) {
        if (!graded) return@LaunchedEffect
        gradedLoading = true
        gradedQuote = runCatching { repo.gradedPrice(card, variant, gradeInfo) }.getOrNull()
        gradedLoading = false
    }
    val shown = if (graded) gradedQuote ?: raw.takeIf { !gradedLoading } else conditionQuote ?: raw
    val loading = if (graded) gradedLoading else conditionLoading
    val gradedMissing = graded && !gradedLoading && gradedQuote == null
    val note = if (gradedMissing) "No ${gradeInfo.label} sales on PriceCharting or eBay; the raw price will be used" else shown?.note

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (candidates.size > 1) {
                Text("${candidates.size} possible matches — tap the right one", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(candidates) { i, c ->
                        Card(
                            Modifier.width(96.dp).clickable { selected = i },
                            border = if (i == selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(),
                        ) {
                            CardImage(c.variants.firstOrNull()?.imageUrl ?: c.imageUrl, thumb = true)
                            Text(c.name.takeIf { candidates.any { o -> o.name != c.name } } ?: c.setName,
                                style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardImage(variant.imageUrl ?: card.imageUrl, Modifier.width(140.dp))
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
                        if (graded) "${gradeInfo.label} · ${if (gradedMissing) "raw price" else shown?.source?.label ?: "looking up sales…"}" else "per copy · ${variant.label} · $condition · ${shown?.source?.label.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                }
            }
            if (card.variants.size > 1) {
                Text("Printing", style = MaterialTheme.typography.labelLarge)
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!graded, { graded = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Raw") }
                SegmentedButton(graded, { graded = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Graded") }
            }
            if (graded) {
                Text("Grading company", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GRADERS.forEach { g -> FilterChip(g == grader, { grader = g }, { Text(g) }) }
                }
                Text("Grade", style = MaterialTheme.typography.labelLarge)
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
                OutlinedTextField(
                    value = cert,
                    onValueChange = { cert = it.filter(Char::isLetterOrDigit).take(14) },
                    label = { Text("Cert number (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Condition", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CONDITIONS.forEach { c -> FilterChip(selected = c == condition, onClick = { condition = c }, label = { Text(c) }) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                QuantityStepper(quantity, { quantity = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onAdd(card, variant, quantity, condition, gradeInfo.takeIf { graded }) }) { Text("Add") }
                }
            }
        }
    }
}
