package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.clickable
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monkaydee.tcgcatalogue.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.monkaydee.tcgcatalogue.data.db.WishCard
import com.monkaydee.tcgcatalogue.data.remote.toBrief
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.CardBrief
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.scan.CardTextParser
import com.monkaydee.tcgcatalogue.scan.OcrLine
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
import com.monkaydee.tcgcatalogue.ui.components.AddRequest
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val loading: Boolean = false,
    val results: List<CardBrief> = emptyList(),
    val candidates: List<CardCandidate> = emptyList(),
    val message: String? = null,
    /** Results being put on the wishlist (their details are loading). */
    val wishPending: Set<String> = emptySet(),
)

class SearchViewModel(private val repo: CardRepository) : ViewModel() {
    val state = MutableStateFlow(SearchState())

    fun search(game: Game, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        state.value = SearchState(loading = true)
        viewModelScope.launch {
            runCatching {
                val pokemonNumber = if (game == Game.POKEMON) CardTextParser.findPokemon(listOf(OcrLine(q, top = 0.9f))) else null
                if (pokemonNumber != null) {
                    // "025/165" optionally followed by the name: "025/165 pikachu"
                    val name = q.replace(Regex("""[A-Z]{0,3}\d{1,3}\s?/\s?[A-Z]{0,3}\d{2,3}"""), "").trim().ifEmpty { null }
                    val found = repo.resolve(pokemonNumber.copy(nameGuess = name))
                    state.update {
                        it.copy(
                            loading = false,
                            // Also listed behind the sheet, so they can be reopened or put on the wishlist.
                            results = found.map { c -> c.toBrief() }.distinctBy { b -> b.cardId },
                            candidates = found,
                            message = if (found.isEmpty()) AppStrings.get(R.string.search_no_card, q) else null,
                        )
                    }
                } else {
                    val results = repo.search(game, q)
                    val single = results.singleOrNull()?.candidate
                    state.update {
                        it.copy(
                            loading = false,
                            results = results.distinctBy { b -> b.cardId },
                            candidates = listOfNotNull(single),
                            message = if (results.isEmpty()) AppStrings.get(R.string.search_nothing_found, q) else null,
                        )
                    }
                }
            }.onFailure { e -> state.update { it.copy(loading = false, message = AppStrings.get(R.string.search_failed, e.message.toString())) } }
        }
    }

    fun open(brief: CardBrief) {
        state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val c = runCatching { repo.details(brief) }.getOrNull()
            state.update { it.copy(loading = false, candidates = listOfNotNull(c), message = if (c == null) AppStrings.get(R.string.search_could_not_load) else null) }
        }
    }

    fun add(r: AddRequest) {
        val c = r.card
        val qty = r.quantity
        viewModelScope.launch {
            runCatching { repo.add(r) }
                .onSuccess { state.update { it.copy(candidates = emptyList(), message = AppStrings.get(R.string.search_added_qty, c.name, qty)) } }
                .onFailure { e -> state.update { it.copy(candidates = emptyList(), message = AppStrings.get(R.string.search_could_not_save, e.message.toString())) } }
        }
    }

    fun dismiss() = state.update { it.copy(candidates = emptyList()) }

    /** Puts a result on the wishlist (its default printing), or takes it off when it is already there. */
    fun toggleWish(brief: CardBrief, wishlist: List<WishCard>) {
        if (brief.cardId in state.value.wishPending) return
        val existing = wishlist.filter { it.game == brief.game && it.cardId == brief.cardId }
        viewModelScope.launch {
            if (existing.isNotEmpty()) {
                existing.forEach { repo.removeFromWishlist(it) }
                state.update { it.copy(message = AppStrings.get(R.string.wish_removed, brief.name)) }
                return@launch
            }
            state.update { it.copy(wishPending = it.wishPending + brief.cardId) }
            val added = runCatching {
                val c = repo.details(brief)?.takeIf { it.variants.isNotEmpty() } ?: error("no card")
                repo.addToWishlist(c, c.defaultVariant)
            }.isSuccess
            state.update {
                it.copy(
                    wishPending = it.wishPending - brief.cardId,
                    message = if (added) AppStrings.get(R.string.wish_added, brief.name) else AppStrings.get(R.string.wish_add_failed),
                )
            }
        }
    }
}

/** A small heart on a search result: adds the card to the wishlist or removes it. */
@Composable
private fun WishToggle(wished: Boolean, pending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable(enabled = !pending, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (wished) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                stringResource(if (wished) R.string.wish_remove else R.string.wish_add),
                Modifier.size(18.dp),
                tint = if (wished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun searchHint(game: Game) = when (game) {
    Game.POKEMON -> R.string.search_hint_pokemon
    Game.ONE_PIECE -> R.string.search_hint_one_piece
    Game.MAGIC -> R.string.search_hint_magic
    Game.DRAGON_BALL_FW -> R.string.search_hint_dragon_ball_fw
    Game.DRAGON_BALL_SUPER -> R.string.search_hint_dragon_ball_super
    Game.UNION_ARENA -> R.string.search_hint_union_arena
    Game.WEISS_SCHWARZ -> R.string.search_hint_weiss_schwarz
    Game.NARUTO -> R.string.search_hint_naruto
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(repo: CardRepository, onBack: () -> Unit, replaceId: Long? = null) {
    val vm: SearchViewModel = viewModel { SearchViewModel(repo) }
    val state by vm.state.collectAsState()
    val settings by repo.settings.flow.collectAsState(initial = AppSettings())
    var game by rememberSaveable { mutableStateOf(Game.POKEMON) }
    var query by rememberSaveable { mutableStateOf("") }
    val wishlist by repo.wishlist.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(if (replaceId != null) stringResource(R.string.search_title_replace) else stringResource(R.string.search_title_add)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.search_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameChips(game, { g -> if (g != null) game = g }, nullLabel = null)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(searchHint(game))) },
                trailingIcon = { IconButton(onClick = { vm.search(game, query) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search_search)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(game, query) }),
            )
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.results, key = { it.cardId }) { r ->
                    Column(Modifier.clickable { vm.open(r) }) {
                        Box {
                            CardImage(r.imageUrl)
                            WishToggle(
                                wished = wishlist.any { it.game == r.game && it.cardId == r.cardId },
                                pending = r.cardId in state.wishPending,
                                onClick = { vm.toggleWish(r, wishlist) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                        Text(r.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.cardId, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }

    // Replace mode ("Wrong card?" on a card in the collection): the chosen card takes its place.
    var replacing by remember { mutableStateOf<com.monkaydee.tcgcatalogue.data.db.OwnedCard?>(null) }
    LaunchedEffect(replaceId) { replacing = replaceId?.let { repo.card(it) } }
    val scope = rememberCoroutineScope()
    val original = replacing
    if (replaceId != null && original != null) {
        AddCardSheet(
            state.candidates,
            settings,
            repo,
            initial = original,
            confirmLabel = stringResource(R.string.search_replace),
            onAdd = { r ->
                vm.dismiss()
                scope.launch {
                    repo.saveEdit(original, r)
                    onBack()
                }
            },
            onDismiss = vm::dismiss,
        )
    } else {
        AddCardSheet(state.candidates, settings, repo, onAdd = vm::add, onDismiss = vm::dismiss)
    }
}
