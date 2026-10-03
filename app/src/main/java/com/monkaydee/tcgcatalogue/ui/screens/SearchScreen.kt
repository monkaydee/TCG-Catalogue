package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.monkaydee.tcgcatalogue.ui.components.AddCardSheet
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
                    state.update { it.copy(loading = false, candidates = found, message = if (found.isEmpty()) "No card $q" else null) }
                } else {
                    val results = repo.search(game, q)
                    val single = results.singleOrNull()?.candidate
                    state.update {
                        it.copy(
                            loading = false,
                            results = if (single != null) emptyList() else results,
                            candidates = listOfNotNull(single),
                            message = if (results.isEmpty()) "Nothing found for \"$q\"" else null,
                        )
                    }
                }
            }.onFailure { e -> state.update { it.copy(loading = false, message = "Search failed: ${e.message}") } }
        }
    }

    fun open(brief: CardBrief) {
        state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val c = runCatching { repo.details(brief) }.getOrNull()
            state.update { it.copy(loading = false, candidates = listOfNotNull(c), message = if (c == null) "Could not load card" else null) }
        }
    }

    fun add(c: CardCandidate, v: Variant, qty: Int, condition: String, grade: GradeInfo?, listing: CardmarketApi.Listing?) {
        viewModelScope.launch {
            runCatching { repo.add(c, v, qty, condition, grade, listing) }
                .onSuccess { state.update { it.copy(candidates = emptyList(), message = "Added ${c.name} ×$qty") } }
                .onFailure { e -> state.update { it.copy(candidates = emptyList(), message = "Could not save: ${e.message}") } }
        }
    }

    fun dismiss() = state.update { it.copy(candidates = emptyList()) }
}

private fun searchHint(game: Game) = when (game) {
    Game.POKEMON -> "Name or number (e.g. Pikachu, 025/165)"
    Game.ONE_PIECE -> "Card code (e.g. OP05-060)"
    Game.MAGIC -> "Name or set + number (e.g. Sheoldred, DMU 107)"
    Game.DRAGON_BALL_FW -> "Code or name (e.g. FB01-139)"
    Game.DRAGON_BALL_SUPER -> "Code or name (e.g. BT1-031)"
    Game.UNION_ARENA -> "Code or name (e.g. UE01BT/BLC-1-001)"
    Game.WEISS_SCHWARZ -> "Code or name (e.g. HOL/W91-001)"
    Game.NARUTO -> "Code or name"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(repo: CardRepository, onBack: () -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel(repo) }
    val state by vm.state.collectAsState()
    val settings by repo.settings.flow.collectAsState(initial = AppSettings())
    var game by rememberSaveable { mutableStateOf(Game.POKEMON) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a card") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                label = { Text(searchHint(game)) },
                trailingIcon = { IconButton(onClick = { vm.search(game, query) }) { Icon(Icons.Default.Search, "Search") } },
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
                        CardImage(r.imageUrl)
                        Text(r.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.cardId, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }

    AddCardSheet(state.candidates, settings, repo, onAdd = vm::add, onDismiss = vm::dismiss)
}
