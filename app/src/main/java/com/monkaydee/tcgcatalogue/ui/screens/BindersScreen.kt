package com.monkaydee.tcgcatalogue.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.CollectionList
import com.monkaydee.tcgcatalogue.data.CoverDesign
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.LookImages
import com.monkaydee.tcgcatalogue.ui.components.BinderCover
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The binder shelf: the main binder with every card, the user's own binders, all closed, and a
 * tile to start a new one. Opening a binder shows its cover first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindersScreen(repo: CardRepository, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val cards by repo.cards.collectAsState(initial = emptyList())
    val binders by repo.binders.collectAsState(initial = emptyList())
    val links by repo.binderCards.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = settings ?: return
    val newName = stringResource(R.string.binder_new_name)
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.binders_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BinderCover(CoverDesign.of(s.mainCover), s.mainCoverImage, stringResource(R.string.binders_all),
                    stringResource(R.string.binders_card_count, cards.sumOf { it.quantity }),
                    Modifier.aspectRatio(0.72f).clickable { onOpen(0) }, compact = true)
            }
            items(binders, key = { it.id }) { b ->
                val rows = links.filter { it.binderId == b.id }.map { it.cardRowId }.toSet()
                BinderCover(CoverDesign.of(b.cover), b.coverImage, b.name,
                    stringResource(R.string.binders_card_count, cards.filter { it.id in rows }.sumOf { it.quantity }),
                    Modifier.aspectRatio(0.72f).clickable { onOpen(b.id) }, compact = true)
            }
            item {
                Surface(
                    onClick = { creating = true },
                    modifier = Modifier.aspectRatio(0.72f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(40.dp))
                        Text(stringResource(R.string.binders_new), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
    if (creating) {
        BinderEditor(name = newName, design = CoverDesign.MIDNIGHT, image = null, onDismiss = { creating = false }) { name, design, image ->
            creating = false
            scope.launch {
                val id = repo.createBinder(name ?: newName, design.key)
                if (image != null) repo.binder(id)?.let { repo.updateBinder(it.copy(coverImage = image)) }
                onOpen(id)
            }
        }
    }
}

/**
 * Name and cover of a binder: a preset design or the user's own picture. [name] null hides the name
 * (the main binder keeps its name).
 */
@Composable
fun BinderEditor(name: String?, design: CoverDesign, image: String?, onDismiss: () -> Unit, onSave: (String?, CoverDesign, String?) -> Unit) {
    var text by remember { mutableStateOf(name.orEmpty()) }
    var chosen by remember { mutableStateOf(design) }
    var picture by remember { mutableStateOf(image) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            // Every picture gets its own file, so a cancelled edit never changes a saved cover.
            runCatching { LookImages.import(context, uri, "cover-${UUID.randomUUID()}") }
                .onSuccess { picture = it }
                .onFailure { Toast.makeText(context, context.getString(R.string.appearance_image_failed), Toast.LENGTH_LONG).show() }
        }
    }
    // A picture picked and then not saved is not kept.
    val cancel = { picture?.takeIf { it != image }?.let { java.io.File(it).delete() }; onDismiss() }
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(stringResource(R.string.binder_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BinderCover(chosen, picture, text.ifBlank { name ?: stringResource(R.string.binders_all) }, null,
                    Modifier.height(170.dp).aspectRatio(0.72f).align(Alignment.CenterHorizontally), compact = true)
                if (name != null) {
                    OutlinedTextField(text, { text = it.take(60) }, label = { Text(stringResource(R.string.binder_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.binder_cover_design), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CoverDesign.entries) { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                            BinderCover(d, null, "", null,
                                Modifier.size(56.dp, 78.dp)
                                    .then(if (d == chosen && picture == null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable { chosen = d; picture = null },
                                compact = true)
                            Text(stringResource(d.label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.binder_cover_upload))
                    }
                    if (picture != null) TextButton(onClick = { picture = null }) { Text(stringResource(R.string.binder_cover_remove)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (name == null) null else text, chosen, picture) }) { Text(stringResource(R.string.binder_save)) } },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Choose which collection cards are in a binder: search and tick them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderCardPicker(cards: List<OwnedCard>, selected: Set<Long>, s: AppSettings, onDismiss: () -> Unit, onSave: (Set<Long>) -> Unit) {
    var picked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    val sorted = remember(cards) { cards.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    val shown = sorted.filter { c ->
        query.isBlank() || query.trim().split(' ').all { w -> listOf(c.name, c.setName, c.number).any { it.contains(w, ignoreCase = true) } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = appBarColors(),
                    title = { Text(stringResource(R.string.binder_choose_cards)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(android.R.string.cancel)) } },
                    actions = { TextButton(onClick = { onSave(picked) }) { Text(stringResource(R.string.binder_save)) } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.binder_search)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(stringResource(R.string.binder_selected, picked.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val on = c.id in picked
                        Row(
                            Modifier.fillMaxWidth().clickable { picked = if (on) picked - c.id else picked + c.id }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(on, { picked = if (on) picked - c.id else picked + c.id })
                            Box(Modifier.width(40.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(3.dp))) { CardOrSlab(c, Modifier.fillMaxSize(), thumb = true) }
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(c.setName, c.number, CollectionList.slab(c)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(Money.unitText(c, s.currency, s.usdToEur), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
