package com.monkaydee.tcgcatalogue.ui.screens

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.CloudBackup
import com.monkaydee.tcgcatalogue.data.CreateBackupDocument
import com.monkaydee.tcgcatalogue.data.OpenBackupDocument
import com.monkaydee.tcgcatalogue.data.SaveOutcome
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun stamp(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

/** "Cloud backup" inside the Backup section: pick the file, see when it was saved, back up now, stop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.CloudBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by CloudBackup.state.collectAsState()

    val createLauncher = rememberLauncherForActivityResult(CreateBackupDocument()) { uri ->
        uri?.let { scope.launch { CloudBackup.choose(it) } }
    }
    val openLauncher = rememberLauncherForActivityResult(OpenBackupDocument()) { uri ->
        uri?.let { scope.launch { CloudBackup.choose(it) } }
    }

    Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.cloud_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (state.uri != null) {
        Text(stringResource(R.string.cloud_file, state.name ?: "…"), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (state.lastSaved > 0) stringResource(R.string.cloud_last_saved, stamp(context, state.lastSaved)) else stringResource(R.string.cloud_never_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.uri != null) {
            Button(enabled = !state.busy, onClick = {
                scope.launch {
                    val message = when (CloudBackup.backUpNow()) {
                        SaveOutcome.SAVED -> R.string.cloud_saved
                        SaveOutcome.UNCHANGED -> R.string.cloud_unchanged
                        else -> null
                    }
                    message?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
                }
            }) { Text(stringResource(R.string.cloud_backup_now)) }
        }
        OutlinedButton(onClick = { createLauncher.launch("tcg-catalogue-${LocalDate.now()}.json") }) { Text(stringResource(R.string.cloud_choose_new)) }
        OutlinedButton(onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) }) { Text(stringResource(R.string.cloud_choose_existing)) }
        if (state.uri != null) {
            TextButton(onClick = {
                CloudBackup.stop()
                Toast.makeText(context, context.getString(R.string.cloud_stopped), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.cloud_stop)) }
        }
    }
}

/**
 * Shown when the backup file holds a newer collection from another phone: load it (replace this
 * phone's collection), merge it in, or ignore it. Draws nothing otherwise.
 */
@Composable
fun CloudPendingCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by CloudBackup.pending.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val backup = pending ?: return

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cloud_pending_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.cloud_pending_text, backup.cards.sumOf { it.quantity }, stamp(context, backup.savedAt)),
                style = MaterialTheme.typography.bodyMedium,
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirming = true }) { Text(stringResource(R.string.cloud_load)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        if (CloudBackup.mergePending()) Toast.makeText(context, context.getString(R.string.cloud_merged), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.cloud_merge)) }
                TextButton(onClick = { CloudBackup.ignorePending() }) { Text(stringResource(R.string.cloud_ignore)) }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.cloud_load_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_load_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch {
                        if (CloudBackup.loadPending()) Toast.makeText(context, context.getString(R.string.cloud_loaded), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.cloud_load_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
}
