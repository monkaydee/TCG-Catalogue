package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.SharedLearning
import kotlinx.coroutines.launch

@Composable
fun LearningPanel(repo: CardRepository) {
    val state by SharedLearning.state.collectAsState()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    Text(stringResource(R.string.tools_learning), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.learning_metadata_notice), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.tools_learning), Modifier.weight(1f))
        Switch(state.text, { scope.launch { SharedLearning.consent(repo, it, state.photos); refresh++ } })
    }
    Text(stringResource(R.string.learning_photos_paused), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    val crops = remember(state.pending, state.photos, refresh) { SharedLearning.crops() }
    crops.forEach { file ->
        AsyncImage(file, stringResource(R.string.tools_preview), Modifier.fillMaxWidth().height(240.dp))
        TextButton(onClick = { scope.launch { SharedLearning.approve(file); SharedLearning.flush(repo); refresh++ } }) { Text(stringResource(R.string.tools_preview)) }
        TextButton(onClick = { scope.launch { SharedLearning.decline(file); SharedLearning.flush(repo); refresh++ } }) { Text(stringResource(R.string.card_cancel)) }
    }
    Text(stringResource(R.string.tools_send) + ": ${state.pending}")
    TextButton(onClick = { scope.launch { SharedLearning.flush(repo); refresh++ } }, enabled = state.text && state.pending > 0) { Text(stringResource(R.string.tools_send)) }
    TextButton(onClick = { scope.launch { SharedLearning.deleteRemote(repo); refresh++ } }) { Text(stringResource(R.string.tools_delete)) }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
