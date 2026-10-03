package com.monkaydee.tcgcatalogue.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.BuildConfig
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Area
import com.monkaydee.tcgcatalogue.data.Backup
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.ui.AppStrings
import com.monkaydee.tcgcatalogue.data.Palette
import com.monkaydee.tcgcatalogue.data.ThemeMode
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.ui.AppLanguages
import com.monkaydee.tcgcatalogue.ui.LookImages
import com.monkaydee.tcgcatalogue.ui.components.CONDITIONS
import com.monkaydee.tcgcatalogue.ui.components.ColorPickerDialog
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.theme.LocalLook
import com.monkaydee.tcgcatalogue.ui.theme.schemeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: CardRepository, onRefresh: () -> Unit) {
    val s by repo.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = backupJson.encodeToString(Backup.serializer(), repo.exportBackup())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            }
            val message = if (result.isSuccess) {
                context.getString(R.string.settings_backup_saved)
            } else {
                context.getString(R.string.settings_backup_failed, result.exceptionOrNull()?.message.orEmpty())
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                repo.importBackup(backupJson.decodeFromString(Backup.serializer(), text))
            }
            val message = if (result.isSuccess) {
                context.getString(R.string.settings_restored)
            } else {
                context.getString(R.string.settings_import_failed, result.exceptionOrNull()?.message.orEmpty())
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    var picking by remember { mutableStateOf<Area?>(null) }
    var choosingLanguage by remember { mutableStateOf(false) }
    var imageSlot by remember { mutableStateOf("home") }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val slot = imageSlot
        scope.launch {
            runCatching { LookImages.import(context, uri, slot) }
                .onSuccess { path -> if (slot == "home") repo.settings.setHomeImage(path) else repo.settings.setBinderImage(path) }
                .onFailure { Toast.makeText(context, context.getString(R.string.appearance_image_failed), Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, colors = appBarColors()) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CloudPendingCard()

            Section(stringResource(R.string.appearance_title)) {
                Label(stringResource(R.string.appearance_mode))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.appearance_mode_system,
                        ThemeMode.LIGHT to R.string.appearance_mode_light,
                        ThemeMode.DARK to R.string.appearance_mode_dark,
                    )
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.look.mode == mode,
                            onClick = { scope.launch { repo.settings.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }

                Label(stringResource(R.string.appearance_palette))
                val dark = LocalLook.current.dark
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.entries.filter { it != Palette.CUSTOM }.forEach { p ->
                        if (p == Palette.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@forEach
                        val selected = s.look.palette == p && Area.ACCENT !in s.look.colors
                        PaletteDot(
                            colors = if (p == Palette.DYNAMIC) null else schemeFrom(Color(p.seed), dark).let { listOf(it.primary, it.primaryContainer) },
                            selected = selected,
                            label = stringResource(paletteName(p)),
                        ) {
                            scope.launch {
                                repo.settings.setPalette(p)
                                repo.settings.setColor(Area.ACCENT, null)
                            }
                        }
                    }
                }
                Text(
                    stringResource(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.appearance_palette_hint_dynamic else R.string.appearance_palette_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Label(stringResource(R.string.appearance_areas))
                val scheme = MaterialTheme.colorScheme
                val look = LocalLook.current
                Area.entries.forEach { area ->
                    val custom = s.look.colors[area]?.let { Color(it) }
                    val current = custom ?: when (area) {
                        Area.ACCENT -> scheme.primary
                        Area.BACKGROUND -> scheme.background
                        Area.CARDS -> scheme.surfaceContainerHighest
                        Area.TOP_BAR -> look.topBar
                        Area.BOTTOM_BAR -> look.bottomBar
                        Area.BINDER_PAGE -> look.binderPage
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { picking = area }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(current).border(1.dp, scheme.outlineVariant, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(areaName(area)), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (custom == null) R.string.appearance_area_theme else R.string.appearance_area_custom),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (s.look.colors.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { repo.settings.resetColors() } }) { Text(stringResource(R.string.appearance_reset_colors)) }
                }

                Label(stringResource(R.string.appearance_images))
                ImageRow(stringResource(R.string.appearance_image_home), s.look.homeImage, onPick = {
                    imageSlot = "home"
                    imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, onRemove = {
                    LookImages.remove(context, "home")
                    scope.launch { repo.settings.setHomeImage(null) }
                })
                ImageRow(stringResource(R.string.appearance_image_binder), s.look.binderImage, onPick = {
                    imageSlot = "binder"
                    imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, onRemove = {
                    LookImages.remove(context, "binder")
                    scope.launch { repo.settings.setBinderImage(null) }
                })
                if (s.look.homeImage != null || s.look.binderImage != null) {
                    var dim by remember(s.look.imageDim) { mutableFloatStateOf(s.look.imageDim) }
                    Text(stringResource(R.string.appearance_image_dim), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = dim,
                        onValueChange = { dim = it },
                        onValueChangeFinished = { scope.launch { repo.settings.setImageDim(dim) } },
                        valueRange = 0f..0.85f,
                    )
                }
            }

            Section(stringResource(R.string.language_title)) {
                val current = AppLanguages.current()
                val name = current?.let { tag -> AppLanguages.all.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.second ?: tag }
                    ?: stringResource(R.string.language_system)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { choosingLanguage = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.language_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { choosingLanguage = true }) { Text(stringResource(R.string.language_change)) }
                }
            }

            Section(stringResource(R.string.settings_prices_title)) {
                Label(stringResource(R.string.settings_currency))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EUR", "USD").forEach { c -> FilterChip(s.currency == c, { scope.launch { repo.settings.setCurrency(c) } }, { Text(c) }) }
                }
                Hint(stringResource(R.string.settings_fx_rate, s.usdToEur))
                Label(stringResource(R.string.settings_price_source))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PriceSource.CARDMARKET, PriceSource.TCGPLAYER).forEach { p ->
                        FilterChip(s.pokemonSource == p, {
                            scope.launch { repo.settings.setPokemonSource(p); onRefresh() }
                        }, { Text("${p.label} (${p.currency})") })
                    }
                }
                Hint(stringResource(R.string.settings_price_source_hint))
                Hint(stringResource(R.string.settings_refresh_hint))
                Button(onClick = onRefresh) { Text(stringResource(R.string.settings_refresh_now)) }
            }

            Section(stringResource(R.string.settings_scanning_title)) {
                Label(stringResource(R.string.settings_games))
                Hint(stringResource(R.string.settings_games_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Game.entries.forEach { g ->
                        FilterChip(g in s.enabledGames, {
                            val next = if (g in s.enabledGames) s.enabledGames - g else s.enabledGames + g
                            if (next.isNotEmpty()) scope.launch { repo.settings.setEnabledGames(next) }
                        }, { Text(g.short) })
                    }
                }
                SwitchRow(stringResource(R.string.settings_quick_add), stringResource(R.string.settings_quick_add_hint), s.quickAdd) { v ->
                    scope.launch { repo.settings.setQuickAdd(v) }
                }
                Label(stringResource(R.string.settings_default_condition))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CONDITIONS.forEach { c -> FilterChip(s.defaultCondition == c, { scope.launch { repo.settings.setDefaultCondition(c) } }, { Text(c) }) }
                }
            }

            Section(stringResource(R.string.settings_display_title)) {
                SwitchRow(stringResource(R.string.settings_full_screen), stringResource(R.string.settings_full_screen_hint), s.fullScreen) { v ->
                    scope.launch { repo.settings.setFullScreen(v) }
                }
            }

            Section(stringResource(R.string.settings_backup_title)) {
                Hint(stringResource(R.string.settings_backup_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("tcg-catalogue-${LocalDate.now()}.json") }) { Text(stringResource(R.string.settings_export)) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text(stringResource(R.string.settings_import)) }
                }
                CloudBackupSection()
            }

            Section(stringResource(R.string.settings_server_title)) {
                ServerSettings(repo, s)
            }

            Section(stringResource(R.string.settings_about_title)) {
                Hint(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME))
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    picking?.let { area ->
        val scheme = MaterialTheme.colorScheme
        val look = LocalLook.current
        val themeColor = when (area) {
            Area.ACCENT -> scheme.primary
            Area.BACKGROUND -> scheme.background
            Area.CARDS -> scheme.surfaceContainerHighest
            Area.TOP_BAR -> look.topBar
            Area.BOTTOM_BAR -> look.bottomBar
            Area.BINDER_PAGE -> look.binderPage
        }
        ColorPickerDialog(
            title = stringResource(areaName(area)),
            initial = s.look.colors[area]?.let { Color(it) },
            themeColor = themeColor,
            onPick = { c ->
                picking = null
                scope.launch {
                    repo.settings.setColor(area, c?.toArgbLong())
                    if (area == Area.ACCENT && c != null) repo.settings.setPalette(Palette.CUSTOM)
                }
            },
            onDismiss = { picking = null },
        )
    }

    if (choosingLanguage) {
        val current = AppLanguages.current()
        AlertDialog(
            onDismissRequest = { choosingLanguage = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                LazyColumn {
                    item {
                        LanguageRow(stringResource(R.string.language_system), current == null) {
                            choosingLanguage = false
                            AppLanguages.set(null)
                        }
                    }
                    items(AppLanguages.all) { (tag, name) ->
                        LanguageRow(name, current.equals(tag, ignoreCase = true)) {
                            choosingLanguage = false
                            AppLanguages.set(tag)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingLanguage = false }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun paletteName(p: Palette) = when (p) {
    Palette.DYNAMIC -> R.string.palette_dynamic
    Palette.INDIGO -> R.string.palette_indigo
    Palette.OCEAN -> R.string.palette_ocean
    Palette.TEAL -> R.string.palette_teal
    Palette.FOREST -> R.string.palette_forest
    Palette.GOLD -> R.string.palette_gold
    Palette.SUNSET -> R.string.palette_sunset
    Palette.ROSE -> R.string.palette_rose
    Palette.GRAPE -> R.string.palette_grape
    Palette.GRAPHITE -> R.string.palette_graphite
    Palette.CUSTOM -> R.string.palette_custom
}

private fun areaName(a: Area) = when (a) {
    Area.ACCENT -> R.string.area_accent
    Area.BACKGROUND -> R.string.area_background
    Area.CARDS -> R.string.area_cards
    Area.TOP_BAR -> R.string.area_top_bar
    Area.BOTTOM_BAR -> R.string.area_bottom_bar
    Area.BINDER_PAGE -> R.string.area_binder_page
}

/** A titled group of settings on its own card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** The price server's address and app key, with a test of the connection. */
@Composable
private fun ServerSettings(repo: CardRepository, s: AppSettings) {
    val scope = rememberCoroutineScope()
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var key by remember(s.serverKey) { mutableStateOf(s.serverKey) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Hint(stringResource(R.string.settings_server_summary))
    androidx.compose.material3.OutlinedTextField(
        value = url, onValueChange = { url = it; result = null }, singleLine = true,
        label = { Text(stringResource(R.string.settings_server_url)) }, modifier = Modifier.fillMaxWidth(),
    )
    androidx.compose.material3.OutlinedTextField(
        value = key, onValueChange = { key = it; result = null }, singleLine = true,
        label = { Text(stringResource(R.string.settings_server_key)) }, modifier = Modifier.fillMaxWidth(),
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { scope.launch { repo.settings.setServer(url, key) } }, enabled = url != s.serverUrl || key != s.serverKey) {
            Text(stringResource(R.string.settings_server_save))
        }
        if (BuildConfig.PRICE_SERVER_URL.isNotEmpty()) {
            OutlinedButton(onClick = { scope.launch { repo.settings.setServer("", "") } }) { Text(stringResource(R.string.settings_server_reset)) }
        }
        OutlinedButton(onClick = {
            testing = true
            scope.launch {
                result = AppStrings.get(if (repo.server.reachable()) R.string.settings_server_ok else R.string.settings_server_bad)
                testing = false
            }
        }, enabled = !testing && s.hasServer) { Text(stringResource(R.string.settings_server_test)) }
    }
    Hint(result ?: if (!s.hasServer) stringResource(R.string.settings_server_off) else "")
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(title)
            Hint(hint)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

/** A colour theme to pick: its two main colours, or the wallpaper icon for the phone's colours. */
@Composable
private fun PaletteDot(colors: List<Color>?, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (colors == null) {
                        Brush.sweepGradient(listOf(Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF66BB6A), Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFFEF5350)))
                    } else {
                        Brush.linearGradient(listOf(colors[0], colors[0], colors[1], colors[1]))
                    },
                )
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected -> Icon(Icons.Default.Check, null, tint = Color.White)
                colors == null -> Icon(Icons.Default.Wallpaper, null, tint = Color.White)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ImageRow(title: String, path: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (path != null) AsyncImage(model = java.io.File(path), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onPick) { Text(stringResource(R.string.appearance_image_choose)) }
        if (path != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.appearance_image_remove)) }
    }
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
