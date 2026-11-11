package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.flow.combine

/** A set next to the open one: its id and name. */
data class SetLink(val setId: String, val name: String)

/** The sets before and after [setId]: the game's sets in the collection, oldest first. */
@Composable
fun rememberSetNeighbours(repo: CardRepository, game: Game, setId: String): Pair<SetLink?, SetLink?> {
    val order by remember(game) {
        combine(repo.cards, repo.sets) { cards, sets ->
            val info = sets.filter { it.game == game }.associateBy { it.setId }
            cards.filter { it.game == game }.distinctBy { it.setId }
                .map { SetLink(it.setId, info[it.setId]?.name ?: it.setName) to (info[it.setId]?.releaseDate ?: "") }
                .sortedWith(compareBy({ it.second }, { it.first.name }))
                .map { it.first }
        }
    }.collectAsState(initial = emptyList())
    val i = order.indexOfFirst { it.setId == setId }
    if (i < 0) return null to null
    return order.getOrNull(i - 1) to order.getOrNull(i + 1)
}

/** Swiping left opens the next set, right the previous one. */
fun Modifier.swipeSets(previous: SetLink?, next: SetLink?, onSwitch: (String) -> Unit): Modifier = pointerInput(previous, next) {
    val threshold = 96.dp.toPx()
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragEnd = {
            if (dx < -threshold) next?.let { onSwitch(it.setId) }
            if (dx > threshold) previous?.let { onSwitch(it.setId) }
        },
    ) { _, amount -> dx += amount }
}

/** "‹ previous set · next set ›" bar; hidden when the set has no neighbours. */
@Composable
fun SetSwitchBar(previous: SetLink?, next: SetLink?, onSwitch: (String) -> Unit) {
    if (previous == null && next == null) return
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp)) {
            TextButton(onClick = { previous?.let { onSwitch(it.setId) } }, enabled = previous != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.set_previous), Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(previous?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { next?.let { onSwitch(it.setId) } }, enabled = next != null, modifier = Modifier.weight(1f)) {
                Text(next?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.set_next), Modifier.size(20.dp))
            }
        }
    }
}
