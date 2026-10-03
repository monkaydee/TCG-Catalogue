package com.monkaydee.tcgcatalogue.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.scan.CardTextParser
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.ui.components.display
import com.monkaydee.tcgcatalogue.scan.PhotoRecognizer
import com.monkaydee.tcgcatalogue.scan.ScanHit
import com.monkaydee.tcgcatalogue.scan.SharedPhotos
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.GradedSlab
import com.monkaydee.tcgcatalogue.ui.theme.Gain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ImportStatus { READING, LOOKING_UP, REVIEW, ADDED, NOT_FOUND, NO_NUMBER, ERROR }

/** One card found in an imported photo (or the photo itself while it is being read). */
data class ImportItem(
    val key: Long,
    val photo: Uri,
    val hit: ScanHit? = null,
    val grade: GradeInfo? = null,
    val status: ImportStatus,
    val candidates: List<CardCandidate> = emptyList(),
    val note: String? = null,
)

data class ImportState(
    val items: List<ImportItem> = emptyList(),
    /** null = recognise any enabled game */
    val filter: Game? = null,
    val reviewing: Long? = null,
)

class ImportViewModel(private val repo: CardRepository, private val context: Context) : ViewModel() {
    val state = MutableStateFlow(ImportState())
    private val queue = Channel<Uri>(Channel.UNLIMITED)
    private var nextKey = 0L
    private val dir = File(context.cacheDir, "imports").apply { mkdirs() }

    init {
        // Photos are read one at a time to keep memory use low.
        viewModelScope.launch {
            repo.prepareIndexes()
            for (uri in queue) process(uri)
        }
    }

    fun setFilter(f: Game?) = state.update { it.copy(filter = f) }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                // Keep a private copy: access to photos shared from other apps can be revoked later.
                val local = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = File(dir, "photo-${System.nanoTime()}")
                        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                        Uri.fromFile(file)
                    }.getOrDefault(uri)
                }
                val key = nextKey++
                state.update { it.copy(items = it.items + ImportItem(key, local, status = ImportStatus.READING)) }
                queue.send(local)
            }
        }
    }

    private suspend fun process(photo: Uri) {
        val placeholder = state.value.items.firstOrNull { it.photo == photo && it.status == ImportStatus.READING } ?: return
        val filter = state.value.filter
        val enabled = repo.settings.current().enabledGames
        val result = runCatching {
            PhotoRecognizer.recognize(context, photo) { lines ->
                CardTextParser.parseAll(lines, filter, repo.indexMatchers(enabled)).filter { filter != null || it.game in enabled }
            }
        }
        if (result.isFailure) {
            replace(placeholder.key, listOf(placeholder.copy(status = ImportStatus.ERROR, note = "Could not open the photo")))
            return
        }
        val found = result.getOrThrow()
        if (found.hits.isEmpty()) {
            replace(placeholder.key, listOf(placeholder.copy(status = ImportStatus.NO_NUMBER)))
            return
        }
        val items = found.hits.mapIndexed { i, hit ->
            ImportItem(if (i == 0) placeholder.key else nextKey++, photo, hit, found.grade, ImportStatus.LOOKING_UP)
        }
        replace(placeholder.key, items)
        items.forEach { lookUp(it) }
    }

    private suspend fun lookUp(item: ImportItem) {
        val hit = item.hit ?: return
        val result = runCatching { repo.resolve(hit) }
        val candidates = result.getOrDefault(emptyList())
        val updated = when {
            result.isFailure -> item.copy(status = ImportStatus.ERROR, note = "Lookup failed — check your connection")
            candidates.isEmpty() -> item.copy(status = ImportStatus.NOT_FOUND)
            repo.settings.current().quickAdd && repo.isConfident(candidates) -> {
                val top = candidates.first()
                val grade = item.grade?.takeIf { it.grader != null && it.grade != null }
                runCatching { repo.add(top, top.variants.first(), 1, repo.settings.current().defaultCondition, grade) }
                    .fold(
                        onSuccess = {
                            item.copy(status = ImportStatus.ADDED, candidates = candidates, note = "${top.name} · ${top.setName}" + (grade?.let { g -> " · ${g.label}" } ?: ""))
                        },
                        onFailure = { item.copy(status = ImportStatus.REVIEW, candidates = candidates) },
                    )
            }
            else -> item.copy(status = ImportStatus.REVIEW, candidates = candidates)
        }
        replace(item.key, listOf(updated))
    }

    private fun replace(key: Long, with: List<ImportItem>) = state.update { s ->
        val i = s.items.indexOfFirst { it.key == key }
        if (i < 0) s else s.copy(items = s.items.take(i) + with + s.items.drop(i + 1))
    }

    fun retry(item: ImportItem) {
        if (item.hit == null) {
            replace(item.key, listOf(item.copy(status = ImportStatus.READING, note = null)))
            viewModelScope.launch { queue.send(item.photo) }
        } else {
            replace(item.key, listOf(item.copy(status = ImportStatus.LOOKING_UP, note = null)))
            viewModelScope.launch { lookUp(item) }
        }
    }

    fun review(item: ImportItem) = state.update { it.copy(reviewing = item.key) }
    fun closeReview() = state.update { it.copy(reviewing = null) }
    fun remove(item: ImportItem) = state.update { s -> s.copy(items = s.items.filterNot { it.key == item.key }) }

    fun add(key: Long, c: CardCandidate, v: Variant, qty: Int, condition: String, grade: GradeInfo?, listing: CardmarketApi.Listing? = null) {
        state.update { it.copy(reviewing = null) }
        viewModelScope.launch {
            runCatching { repo.add(c, v, qty, condition, grade, listing) }.onSuccess {
                state.value.items.firstOrNull { it.key == key }?.let { item ->
                    val extra = listOfNotNull(grade?.label, "×$qty".takeIf { qty > 1 })
                    replace(key, listOf(item.copy(status = ImportStatus.ADDED, note = (listOf("${c.name} · ${c.setName}") + extra).joinToString(" · "))))
                }
            }
        }
    }

    /** Adds the best match of every card still waiting for review. */
    fun addAllBest() {
        val s = state.value
        viewModelScope.launch {
            val condition = repo.settings.current().defaultCondition
            s.items.filter { it.status == ImportStatus.REVIEW }.forEach { item ->
                val top = item.candidates.first()
                add(item.key, top, top.variants.first(), 1, condition, item.grade?.takeIf { it.grader != null && it.grade != null })
            }
        }
    }

    override fun onCleared() {
        queue.close()
        dir.deleteRecursively()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(repo: CardRepository, openPicker: Boolean, onBack: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    val vm: ImportViewModel = viewModel { ImportViewModel(repo, context.applicationContext) }
    val state by vm.state.collectAsState()
    val settings by repo.settings.flow.collectAsState(initial = AppSettings())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)) { uris ->
        vm.addPhotos(uris)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var autoOpened by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (openPicker && !autoOpened) {
            autoOpened = true
            pick()
        }
    }
    // Photos shared from other apps (gallery, WhatsApp, ...).
    LaunchedEffect(Unit) {
        SharedPhotos.pending.collect { if (it.isNotEmpty()) vm.addPhotos(SharedPhotos.take()) }
    }

    val counts = state.items.groupingBy { it.status }.eachCount()
    val toReview = counts[ImportStatus.REVIEW] ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import photos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = pick) { Icon(Icons.Default.AddPhotoAlternate, "Add photos") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GameChips(state.filter, vm::setFilter, Game.entries.filter { it in settings.enabledGames })
            }
            if (state.items.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Took photos of cards while you were out? Import them here and they're recognised just like a live scan.\n\n" +
                                "• One card per photo works best; binder pages work if the numbers are sharp\n" +
                                "• Make sure the number at the bottom of the card is readable\n" +
                                "• You can also share photos to TCG Catalogue from your gallery or a chat app",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.size(16.dp))
                        Button(onClick = pick) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose photos")
                        }
                    }
                }
            } else {
                item {
                    Text(
                        listOfNotNull(
                            counts[ImportStatus.ADDED]?.let { "$it added" },
                            toReview.takeIf { it > 0 }?.let { "$it to review" },
                            ((counts[ImportStatus.READING] ?: 0) + (counts[ImportStatus.LOOKING_UP] ?: 0)).takeIf { it > 0 }?.let { "$it in progress" },
                            ((counts[ImportStatus.NO_NUMBER] ?: 0) + (counts[ImportStatus.NOT_FOUND] ?: 0) + (counts[ImportStatus.ERROR] ?: 0))
                                .takeIf { it > 0 }?.let { "$it not recognised" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (toReview > 1) {
                    item {
                        FilledTonalButton(onClick = vm::addAllBest, modifier = Modifier.fillMaxWidth()) {
                            Text("Add best match for all $toReview cards")
                        }
                    }
                }
                items(state.items, key = { it.key }) { item ->
                    ImportRow(item, settings, repo, onReview = { vm.review(item) }, onRetry = { vm.retry(item) }, onRemove = { vm.remove(item) }, onManual = onManual)
                }
            }
        }
    }

    val reviewing = state.items.firstOrNull { it.key == state.reviewing }
    if (reviewing != null) {
        AddCardSheet(
            reviewing.candidates,
            settings,
            repo,
            initialGrade = reviewing.grade,
            onAdd = { c, v, q, cond, g, l -> vm.add(reviewing.key, c, v, q, cond, g, l) },
            onDismiss = vm::closeReview,
        )
    }
}

private fun ScanHit.label() = describeHit(this)

@Composable
private fun ImportRow(item: ImportItem, settings: AppSettings, repo: CardRepository, onReview: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit, onManual: () -> Unit) {
    val top = item.candidates.firstOrNull()
    Card(Modifier.fillMaxWidth().clickable(enabled = item.status == ImportStatus.REVIEW, onClick = onReview)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(52.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(4.dp)),
            )
            if (top != null) {
                Spacer(Modifier.width(6.dp))
                val g = item.grade?.takeIf { it.grader != null && it.grade != null }
                if (g != null) {
                    GradedSlab(top.imageUrl, g.grader, g.grade, g.qualifier, top.name, top.number, g.cert, Modifier.width(60.dp), thumb = true)
                } else {
                    CardImage(top.imageUrl, Modifier.width(52.dp), thumb = true)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (item.status) {
                    ImportStatus.READING -> Text("Reading photo…", style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.LOOKING_UP -> Text("Looking up ${item.hit?.label()}…", style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.REVIEW -> {
                        Text(top!!.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.grade?.let { Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                        Text("${top.setName} · ${top.number}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.candidates.size > 1) "${item.candidates.size} possible matches · tap to choose" else "${repo.rawPrice(top, top.variants.first(), settings).display(settings)} · tap to add",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ImportStatus.ADDED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Gain, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Added", style = MaterialTheme.typography.titleSmall, color = Gain, fontWeight = FontWeight.Bold)
                        }
                        item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                    ImportStatus.NOT_FOUND -> Text("No card found for ${item.hit?.label()}", style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.NO_NUMBER -> Text("No card number found in this photo", style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.ERROR -> Text(item.note ?: "Something went wrong", style = MaterialTheme.typography.bodyMedium)
                }
                when (item.status) {
                    ImportStatus.NO_NUMBER, ImportStatus.NOT_FOUND -> OutlinedButton(onClick = onManual) { Text("Type it in") }
                    ImportStatus.ERROR -> OutlinedButton(onClick = onRetry) { Text("Retry") }
                    else -> Unit
                }
            }
            when (item.status) {
                ImportStatus.READING, ImportStatus.LOOKING_UP -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                ImportStatus.ADDED -> Unit
                else -> IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Dismiss") }
            }
        }
    }
}
