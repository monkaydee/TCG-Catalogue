package com.monkaydee.tcgcatalogue.ui.screens

import android.content.Context
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.R
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
import com.monkaydee.tcgcatalogue.scan.PictureSearch
import com.monkaydee.tcgcatalogue.scan.ScanHit
import com.monkaydee.tcgcatalogue.scan.SharedPhotos
import com.monkaydee.tcgcatalogue.scan.VisualMatcher
import com.monkaydee.tcgcatalogue.ui.AppStrings
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
    /** The card cut out of the photo, for telling alt arts apart. */
    val picture: android.graphics.Bitmap? = null,
    val status: ImportStatus,
    val candidates: List<CardCandidate> = emptyList(),
    val note: String? = null,
    /** Everything read on the card (name, numbers, rules), to check and to search by. */
    val texts: List<String> = emptyList(),
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
            replace(placeholder.key, listOf(placeholder.copy(status = ImportStatus.ERROR, note = AppStrings.get(R.string.import_could_not_open_photo))))
            return
        }
        val found = result.getOrThrow()
        if (found.hits.isEmpty()) {
            // No number could be read: look the card up by its picture straight away.
            replace(placeholder.key, listOf(placeholder.copy(status = ImportStatus.LOOKING_UP)))
            replace(placeholder.key, listOf(byPicture(placeholder, found.texts)))
            return
        }
        val items = found.hits.mapIndexed { i, hit ->
            ImportItem(if (i == 0) placeholder.key else nextKey++, photo, hit, found.grade, found.picture, ImportStatus.LOOKING_UP, texts = found.texts)
        }
        replace(placeholder.key, items)
        items.forEach { lookUp(it) }
    }

    private suspend fun lookUp(item: ImportItem) {
        val hit = item.hit ?: return
        val result = runCatching { VisualMatcher.rank(context, item.picture, repo.checkedByName(repo.resolve(hit), item.texts), VisualMatcher.Source.PHOTO) }
        val candidates = result.getOrDefault(emptyList())
        val updated = when {
            result.isFailure -> item.copy(status = ImportStatus.ERROR, note = AppStrings.get(R.string.import_lookup_failed))
            // The number led nowhere (misread, or a print the databases don't have): try the picture.
            candidates.isEmpty() -> byPicture(item, item.texts).let { if (it.candidates.isEmpty()) item.copy(status = ImportStatus.NOT_FOUND) else it }
            repo.settings.current().quickAdd && repo.isConfident(candidates) -> {
                val top = candidates.first()
                val grade = item.grade?.takeIf { it.grader != null && it.grade != null }
                runCatching { repo.add(top, top.defaultVariant, 1, repo.settings.current().defaultCondition, grade) }
                    .fold(
                        onSuccess = {
                            item.copy(status = ImportStatus.ADDED, candidates = candidates, note = "${top.name} · ${top.setName}" + (if (top.variants.size > 1) " · ${top.defaultVariant.label}" else "") + (grade?.let { g -> " · ${g.label}" } ?: ""))
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

    /** A photo without a readable number: look the card up by its picture alone. */
    fun findByPicture(item: ImportItem) {
        replace(item.key, listOf(item.copy(status = ImportStatus.LOOKING_UP, note = null)))
        viewModelScope.launch { replace(item.key, listOf(byPicture(item, emptyList()))) }
    }

    /**
     * [item] looked up by its picture: matches are always shown for review (never added on their
     * own), as a picture can't tell reprints with the same art apart. [texts] is what could be read.
     */
    private suspend fun byPicture(item: ImportItem, texts: List<String>): ImportItem {
        val games = state.value.filter?.let { setOf(it) } ?: repo.settings.current().enabledGames
        val result = com.monkaydee.tcgcatalogue.data.remote.attempt {
            val photo = PhotoRecognizer.loadSmall(context, item.photo)
            val crops = PictureSearch.crops(photo, fromCamera = false)
            repo.candidatesFromPicture(PictureSearch.find(context, crops, CardTextParser.gameFromPrint(texts)?.takeIf { it in games }?.let { setOf(it) } ?: games), texts)
        }
        val found = result.getOrDefault(emptyList())
        return when {
            result.isFailure -> item.copy(status = ImportStatus.NO_NUMBER, note = AppStrings.get(R.string.picture_unavailable))
            found.isEmpty() -> item.copy(status = ImportStatus.NO_NUMBER, note = AppStrings.get(R.string.picture_none))
            else -> item.copy(status = ImportStatus.REVIEW, candidates = found, note = null)
        }
    }

    /**
     * Asks the price server's image recognition (only when the user taps the button: the photo
     * leaves the phone, and the server has a small daily limit).
     */
    fun identifyOnline(item: ImportItem) {
        replace(item.key, listOf(item.copy(status = ImportStatus.LOOKING_UP, note = null)))
        viewModelScope.launch {
            val games = state.value.filter?.let { setOf(it) } ?: repo.settings.current().enabledGames
            val result = com.monkaydee.tcgcatalogue.data.remote.attempt {
                val jpeg = withContext(Dispatchers.Default) {
                    val photo = PhotoRecognizer.loadSmall(context, item.photo, maxSide = 1400)
                    java.io.ByteArrayOutputStream().also { photo.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                }
                repo.identifyOnline(jpeg, games)
            }
            val found = result.getOrDefault(emptyList())
            replace(
                item.key,
                listOf(
                    when {
                        result.isFailure -> item.copy(status = ImportStatus.NO_NUMBER, note = AppStrings.get(R.string.identify_failed))
                        found.isEmpty() -> item.copy(status = ImportStatus.NO_NUMBER, note = AppStrings.get(R.string.identify_none))
                        else -> item.copy(status = ImportStatus.REVIEW, candidates = found, note = null)
                    },
                ),
            )
        }
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

    fun add(key: Long, c: CardCandidate, v: Variant, qty: Int, condition: String, grade: GradeInfo?, listing: CardmarketApi.Listing? = null, paid: Double? = null, own: Double? = null) {
        state.update { it.copy(reviewing = null) }
        viewModelScope.launch {
            runCatching { repo.add(c, v, qty, condition, grade, listing, paid, own) }.onSuccess {
                state.value.items.firstOrNull { it.key == key }?.let { item ->
                    val extra = listOfNotNull(grade?.label, AppStrings.get(R.string.import_quantity, qty).takeIf { qty > 1 })
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
                add(item.key, top, top.defaultVariant, 1, condition, item.grade?.takeIf { it.grader != null && it.grade != null })
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
                colors = appBarColors(),
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.import_back)) } },
                actions = { IconButton(onClick = pick) { Icon(Icons.Default.AddPhotoAlternate, stringResource(R.string.import_add_photos)) } },
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
                            stringResource(R.string.import_intro),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.size(16.dp))
                        Button(onClick = pick) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.import_choose_photos))
                        }
                    }
                }
            } else {
                item {
                    Text(
                        listOfNotNull(
                            counts[ImportStatus.ADDED]?.let { pluralStringResource(R.plurals.import_count_added, it, it) },
                            toReview.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_count_to_review, it, it) },
                            ((counts[ImportStatus.READING] ?: 0) + (counts[ImportStatus.LOOKING_UP] ?: 0)).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_count_in_progress, it, it) },
                            ((counts[ImportStatus.NO_NUMBER] ?: 0) + (counts[ImportStatus.NOT_FOUND] ?: 0) + (counts[ImportStatus.ERROR] ?: 0))
                                .takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_count_not_recognised, it, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (toReview > 1) {
                    item {
                        FilledTonalButton(onClick = vm::addAllBest, modifier = Modifier.fillMaxWidth()) {
                            Text(pluralStringResource(R.plurals.import_add_all_best, toReview, toReview))
                        }
                    }
                }
                items(state.items, key = { it.key }) { item ->
                    ImportRow(item, settings, repo, onReview = { vm.review(item) }, onRetry = { vm.retry(item) }, onRemove = { vm.remove(item) }, onManual = onManual, onPicture = { vm.findByPicture(item) }, onIdentify = { vm.identifyOnline(item) })
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
            onAdd = { r -> vm.add(reviewing.key, r.card, r.variant, r.quantity, r.condition, r.grade, r.listing, r.purchasePrice, r.manualValue) },
            onDismiss = vm::closeReview,
        )
    }
}

private fun ScanHit.label() = describeHit(this)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ImportRow(item: ImportItem, settings: AppSettings, repo: CardRepository, onReview: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit, onManual: () -> Unit, onPicture: () -> Unit = {}, onIdentify: () -> Unit = {}) {
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
                    GradedSlab(top.defaultVariant.imageUrl ?: top.imageUrl, g.grader, g.grade, g.qualifier, top.name, top.number, g.cert, Modifier.width(60.dp), thumb = true)
                } else {
                    CardImage(top.defaultVariant.imageUrl ?: top.imageUrl, Modifier.width(52.dp), thumb = true)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (item.status) {
                    ImportStatus.READING -> Text(stringResource(R.string.import_reading_photo), style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.LOOKING_UP -> Text(stringResource(R.string.import_looking_up, item.hit?.label().toString()), style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.REVIEW -> {
                        Text(top!!.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.grade?.let { Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                        Text("${top.setName} · ${top.number}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (top.variants.size > 1) Text(top.defaultVariant.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (top.printingCheck) {
                            Text(stringResource(R.string.import_check_printing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            if (item.candidates.size > 1) pluralStringResource(R.plurals.import_possible_matches, item.candidates.size, item.candidates.size) else stringResource(R.string.import_tap_to_add, repo.rawPrice(top, top.defaultVariant, settings).display(settings)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ImportStatus.ADDED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Gain, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.import_added), style = MaterialTheme.typography.titleSmall, color = Gain, fontWeight = FontWeight.Bold)
                        }
                        item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                    ImportStatus.NOT_FOUND -> Text(stringResource(R.string.import_no_card_found, item.hit?.label().toString()), style = MaterialTheme.typography.bodyMedium)
                    ImportStatus.NO_NUMBER -> {
                        Text(stringResource(R.string.import_no_number), style = MaterialTheme.typography.bodyMedium)
                        item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    ImportStatus.ERROR -> Text(item.note ?: stringResource(R.string.import_something_wrong), style = MaterialTheme.typography.bodyMedium)
                }
                when (item.status) {
                    ImportStatus.NO_NUMBER, ImportStatus.NOT_FOUND -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.status == ImportStatus.NO_NUMBER) OutlinedButton(onClick = onPicture) { Text(stringResource(R.string.picture_find)) }
                            if (settings.hasServer) OutlinedButton(onClick = onIdentify) { Text(stringResource(R.string.identify_online)) }
                            OutlinedButton(onClick = onManual) { Text(stringResource(R.string.import_type_it_in)) }
                        }
                        if (settings.hasServer) Text(stringResource(R.string.identify_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ImportStatus.ERROR -> OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.import_retry)) }
                    else -> Unit
                }
            }
            when (item.status) {
                ImportStatus.READING, ImportStatus.LOOKING_UP -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                ImportStatus.ADDED -> Unit
                else -> IconButton(onClick = onRemove) { Icon(Icons.Default.Close, stringResource(R.string.import_dismiss)) }
            }
        }
    }
}
