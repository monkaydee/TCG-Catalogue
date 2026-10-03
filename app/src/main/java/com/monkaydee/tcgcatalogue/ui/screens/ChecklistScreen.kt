package com.monkaydee.tcgcatalogue.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.ChecklistEntry
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.ListEmptyState
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.components.display
import com.monkaydee.tcgcatalogue.ui.components.thumbUrl
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class ChecklistFilter(@StringRes val label: Int) {
    ALL(R.string.checklist_filter_all),
    OWNED(R.string.checklist_filter_owned),
    MISSING(R.string.checklist_filter_missing),
}

/** Turns the pictures of missing cards grey. */
private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Every card of a set as a grid: owned cards in colour with their number of copies, missing
 * ones greyed out. Filter by owned / missing; tapping an owned card opens it, tapping a missing
 * one offers to add it to the collection or the wishlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(repo: CardRepository, game: Game, setId: String, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val all by repo.cards.collectAsState(initial = emptyList())
    val wishlist by repo.wishlist.collectAsState(initial = emptyList())
    val set by remember(game, setId) { repo.sets.map { list -> list.firstOrNull { it.game == game && it.setId == setId } } }
        .collectAsState(initial = null)
    var entries by remember(game, setId) { mutableStateOf<List<ChecklistEntry>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(game, setId, reload) {
        entries = null
        // One cell per card id (the grid needs unique keys).
        entries = repo.setChecklist(game, setId).distinctBy { it.cardId }
    }
    var filter by rememberSaveable { mutableStateOf(ChecklistFilter.ALL) }
    var picked by remember { mutableStateOf<ChecklistEntry?>(null) }
    var adding by remember { mutableStateOf<List<CardCandidate>>(emptyList()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val owned = remember(all, game) { all.filter { it.game == game }.groupBy { it.cardId } }
    val wished = remember(wishlist, game) { wishlist.filter { it.game == game }.map { it.cardId }.toSet() }
    val name = set?.name ?: owned.values.firstOrNull { it.first().setId == setId }?.first()?.setName ?: setId

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = {
                    Column {
                        Text(stringResource(R.string.checklist_title))
                        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.lists_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = entries
        when {
            list == null -> Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.checklist_loading), style = MaterialTheme.typography.bodyMedium)
            }
            list.isEmpty() -> ListEmptyState(
                Icons.Default.Checklist,
                stringResource(R.string.checklist_empty_title),
                stringResource(R.string.checklist_empty_text),
                Modifier.padding(padding),
            ) { OutlinedButton(onClick = { reload++ }) { Text(stringResource(R.string.lists_retry)) } }
            else -> {
                val ownedEntries = list.filter { it.cardId in owned }
                val shown = when (filter) {
                    ChecklistFilter.ALL -> list
                    ChecklistFilter.OWNED -> ownedEntries
                    ChecklistFilter.MISSING -> list.filter { it.cardId !in owned }
                }
                val counts = mapOf(
                    ChecklistFilter.ALL to list.size,
                    ChecklistFilter.OWNED to ownedEntries.size,
                    ChecklistFilter.MISSING to list.size - ownedEntries.size,
                )
                // Swiping on an opened card follows the checklist order.
                val browseIds = shown.flatMap { e -> owned[e.cardId].orEmpty().map { it.id } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                pluralStringResource(R.plurals.checklist_progress, list.size, ownedEntries.size, list.size),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            LinearProgressIndicator(
                                progress = { ownedEntries.size.toFloat() / list.size },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChecklistFilter.entries.forEach { f ->
                                    FilterChip(f == filter, { filter = f }, { Text(stringResource(f.label, counts[f] ?: 0)) })
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(if (filter == ChecklistFilter.OWNED) R.string.checklist_none_owned else R.string.checklist_none_missing),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(shown, key = { it.cardId }) { e ->
                        val rows = owned[e.cardId].orEmpty()
                        ChecklistCell(e, rows.sumOf { it.quantity }, e.cardId in wished) {
                            if (rows.isNotEmpty()) CardBrowse.open(browseIds, rows.first().id, onOpenCard) else picked = e
                        }
                    }
                }
            }
        }
    }

    picked?.let { entry ->
        MissingCardSheet(
            entry = entry,
            setName = name,
            game = game,
            repo = repo,
            s = s,
            wished = entry.cardId in wished,
            onDismiss = { picked = null },
            onAdd = { c ->
                picked = null
                adding = listOf(c)
            },
            onWish = { c, v ->
                picked = null
                scope.launch {
                    val ok = runCatching { repo.addToWishlist(c, v) }.isSuccess
                    snackbar.showSnackbar(if (ok) AppStrings.get(R.string.wish_added, c.name) else AppStrings.get(R.string.wish_add_failed))
                }
            },
        )
    }

    AddCardSheet(
        candidates = adding,
        settings = s,
        repo = repo,
        onAdd = { r ->
            adding = emptyList()
            scope.launch {
                runCatching { repo.add(r) }
                    .onSuccess { snackbar.showSnackbar(AppStrings.get(R.string.checklist_added, r.card.name)) }
                    .onFailure { e -> snackbar.showSnackbar(AppStrings.get(R.string.search_could_not_save, e.message.toString())) }
            }
        },
        onDismiss = { adding = emptyList() },
    )
}

/** One card of the checklist: in colour with a copies badge when owned, grey and faded when missing. */
@Composable
private fun ChecklistCell(e: ChecklistEntry, copies: Int, wished: Boolean, onClick: () -> Unit) {
    val have = copies > 0
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(63f / 88f)
                    .clip(shape)
                    .background(colors.surfaceVariant)
                    .then(if (have) Modifier else Modifier.border(1.dp, colors.outlineVariant, shape)),
                contentAlignment = Alignment.Center,
            ) {
                if (e.imageUrl == null) {
                    Text(e.number, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                } else {
                    AsyncImage(
                        model = thumbUrl(e.imageUrl),
                        contentDescription = e.name,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (have) null else Greyscale,
                        modifier = Modifier.fillMaxSize().then(if (have) Modifier else Modifier.alpha(0.4f)),
                    )
                }
            }
            if (have) {
                Text(
                    stringResource(R.string.checklist_owned_badge, copies),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(colors.primary, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            } else {
                Text(
                    stringResource(R.string.checklist_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(6.dp)
                        .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
            if (wished) {
                Icon(
                    Icons.Default.Favorite,
                    stringResource(R.string.checklist_on_wishlist),
                    tint = colors.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(3.dp)
                        .size(14.dp),
                )
            }
        }
        Text(e.number, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(
            e.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (have) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A missing card: loads its full data (price, printings) and offers "Add to collection" and
 * "Add to wishlist". Offline it says so and offers to try again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MissingCardSheet(
    entry: ChecklistEntry,
    setName: String,
    game: Game,
    repo: CardRepository,
    s: AppSettings,
    wished: Boolean,
    onDismiss: () -> Unit,
    onAdd: (CardCandidate) -> Unit,
    onWish: (CardCandidate, Variant) -> Unit,
) {
    var candidate by remember(entry) { mutableStateOf<CardCandidate?>(null) }
    var loading by remember(entry) { mutableStateOf(true) }
    var attempt by remember(entry) { mutableIntStateOf(0) }
    LaunchedEffect(entry, attempt) {
        loading = true
        candidate = repo.checklistCard(game, entry)?.takeIf { it.variants.isNotEmpty() }
        loading = false
    }
    val c = candidate
    var variantKey by remember(c) { mutableStateOf(c?.defaultVariant?.key) }
    val variant = c?.variants?.firstOrNull { it.key == variantKey } ?: c?.variants?.firstOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardImage(variant?.imageUrl ?: c?.imageUrl ?: entry.imageUrl, Modifier.width(120.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$setName · ${entry.number}", style = MaterialTheme.typography.bodyMedium)
                    c?.rarity?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.size(4.dp))
                    when {
                        loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.checklist_card_loading), style = MaterialTheme.typography.bodySmall)
                        }
                        c == null || variant == null -> {
                            Text(stringResource(R.string.checklist_card_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.lists_retry)) }
                        }
                        else -> Text(
                            repo.rawPrice(c, variant, s).display(s),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (wished) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text(stringResource(R.string.checklist_on_wishlist), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (c != null && c.variants.size > 1) {
                Text(stringResource(R.string.checklist_printing), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.variants.forEach { v ->
                        FilterChip(
                            selected = v.key == variant?.key,
                            onClick = { variantKey = v.key },
                            label = { Text("${v.label} · ${repo.rawPrice(c, v, s).display(s)}") },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (c != null && variant != null) onWish(c, variant) },
                    enabled = c != null && variant != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (wished) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.checklist_add_wishlist), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { if (c != null) onAdd(c.copy(preferredVariant = variant?.key)) },
                    enabled = c != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.checklist_add_collection), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
