package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.components.Backdrop
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.theme.LocalLook
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Check
import com.monkaydee.tcgcatalogue.ui.components.AppSelector
import com.monkaydee.tcgcatalogue.ui.components.SelectorOption
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.Binder
import com.monkaydee.tcgcatalogue.data.BinderPage
import com.monkaydee.tcgcatalogue.data.BinderSort
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.Money
import com.monkaydee.tcgcatalogue.data.SetOrder
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.components.CardImage
import com.monkaydee.tcgcatalogue.ui.components.GameChips
import com.monkaydee.tcgcatalogue.ui.components.PageTurner
import com.monkaydee.tcgcatalogue.ui.components.rememberPageTurnState
import com.monkaydee.tcgcatalogue.ui.components.CardOrSlab
import com.monkaydee.tcgcatalogue.ui.components.BinderCover
import com.monkaydee.tcgcatalogue.data.CoverDesign
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


/**
 * The collection as a virtual binder: pages of 3×3, 6×6 or 9×9 pockets, sorted by value, name or
 * set (each set starting on a new page). Pages turn like real binder pages, or slide when the
 * animation is off. Tapping a card opens it; swiping there follows the binder's order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderScreen(repo: CardRepository, binderId: Long = 0, onBack: () -> Unit, onOpenCard: (List<Long>, Long) -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val all by repo.cards.collectAsState(initial = null)
    val setList by repo.sets.collectAsState(initial = emptyList())
    val binders by repo.binders.collectAsState(initial = null)
    val links by repo.binderCards.collectAsState(initial = emptyList())
    // 0 is the main binder with every card; any other id is one of the user's own binders.
    val custom = binders?.firstOrNull { it.id == binderId }
    LaunchedEffect(binders, binderId) { if (binderId != 0L && binders != null && custom == null) onBack() }
    var game by rememberSaveable { mutableStateOf<Game?>(null) }
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The shown page is recorded into [layer] while it is drawn, so "Share page" can turn it into a picture.
    val layer = rememberGraphicsLayer()
    var shareInfo by remember { mutableStateOf<PageShareInfo?>(null) }
    var sharing by remember { mutableStateOf(false) }
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val title = custom?.name ?: stringResource(R.string.binder_title)

    val picture = LocalLook.current.binderImage
    Backdrop(picture) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = appBarColors(overPicture = picture != null),
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.binder_back)) } },
                actions = {
                    if (custom != null) {
                        IconButton(onClick = { choosing = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.binder_add_cards)) }
                    }
                    IconButton(
                        enabled = shareInfo != null && !sharing,
                        onClick = {
                            val info = shareInfo ?: return@IconButton
                            sharing = true
                            scope.launch {
                                runCatching {
                                    val shot = layer.toImageBitmap().asAndroidBitmap()
                                    val uri = PageShare.save(context, shot, info.footer, context.getString(R.string.app_name), backgroundColor, textColor)
                                    PageShare.open(context, uri)
                                }.onFailure {
                                    Toast.makeText(context, context.getString(R.string.share_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
                                }
                                sharing = false
                            }
                        },
                    ) { Icon(Icons.Outlined.Share, stringResource(R.string.share_page)) }
                },
            )
        },
    ) { padding ->
        val s = settings
        val owned = all
        if (s == null || owned == null || (binderId != 0L && custom == null)) return@Scaffold
        val members = remember(links, binderId) { links.filter { it.binderId == binderId }.map { it.cardRowId }.toSet() }
        val cards = if (custom == null) owned else owned.filter { it.id in members }
        val sets = remember(setList) { setList.associateBy { it.game to it.setId } }
        val shown = cards.filter { game == null || it.game == game }
        val perPage = Binder.perPage(s.binderGrid)
        val pages = remember(shown, s.binderSort, s.binderSetOrder, perPage, sets, s.currency, s.usdToEur) {
            Binder.pages(shown, s.binderSort, s.binderSetOrder, perPage, sets) { Money.unit(it, s.currency, s.usdToEur) }
                .ifEmpty { listOf(BinderPage(emptyList())) }
        }
        // Turner page 0 is the closed cover; binder page i is turner page i + 1.
        val total = pages.size + 1
        val order = remember(pages) { pages.flatMap { p -> p.cards.map { it.id } } }
        val turner = rememberPageTurnState()
        // After a change of grid the binder stays at the same card; after a new sort or filter it starts over.
        var keepCard by remember { mutableStateOf<Long?>(null) }
        var restart by remember { mutableStateOf(false) }
        LaunchedEffect(pages) {
            keepCard?.let { id -> turner.jump(pages.indexOfFirst { p -> p.cards.any { it.id == id } }.coerceAtLeast(0) + 1) }
            if (restart && turner.page > 0) turner.jump(1)
            keepCard = null
            restart = false
        }
        val pager = rememberPagerState(initialPage = turner.page) { total }
        LaunchedEffect(turner.page, s.binderAnimation) { if (!s.binderAnimation && pager.currentPage != turner.page) pager.scrollToPage(turner.page) }
        LaunchedEffect(pager.currentPage) { if (!s.binderAnimation) turner.jump(pager.currentPage) }
        val openCover = { if (s.binderAnimation) turner.next(scope) else scope.launch { pager.animateScrollToPage(1) }; Unit }
        val design = CoverDesign.of(custom?.cover ?: s.mainCover)
        val coverImage = custom?.coverImage ?: s.mainCoverImage
        val coverTitle = custom?.name ?: stringResource(R.string.binders_all)
        val coverCount = stringResource(R.string.binders_card_count, cards.sumOf { it.quantity })

        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val games = Game.entries.filter { g -> cards.any { it.game == g } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSelector(stringResource(R.string.design_game), game,
                        listOf(SelectorOption<Game?>(null, stringResource(R.string.home_all_games))) + games.map { SelectorOption<Game?>(it, it.short) },
                        { game = it; restart = true }, Modifier.weight(1f), Icons.Outlined.Collections)
                    AppSelector(stringResource(R.string.design_layout), s.binderGrid,
                        Binder.GRIDS.map { SelectorOption(it, Binder.label(it)) }, { grid ->
                            keepCard = pages.getOrNull(turner.page - 1)?.cards?.firstOrNull()?.id
                            scope.launch { repo.settings.setBinderGrid(grid) }
                        }, Modifier.weight(1f), Icons.Outlined.GridView)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppSelector(stringResource(R.string.design_sort), s.binderSort,
                        BinderSort.entries.map { SelectorOption(it, stringResource(it.label)) }, {
                            restart = true; scope.launch { repo.settings.setBinderSort(it) }
                        }, Modifier.weight(1f), Icons.AutoMirrored.Outlined.Sort)
                    if (s.binderSort == BinderSort.SET) {
                        AppSelector(stringResource(R.string.design_sort), s.binderSetOrder,
                            SetOrder.entries.map { SelectorOption(it, stringResource(it.label)) }, {
                                restart = true; scope.launch { repo.settings.setBinderSetOrder(it) }
                            }, Modifier.weight(1f))
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.design_more))
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.binder_animation)) },
                                onClick = { menu = false; scope.launch { repo.settings.setBinderAnimation(!s.binderAnimation) } },
                                trailingIcon = if (s.binderAnimation) ({ Icon(Icons.Outlined.Check, null) }) else null)
                            DropdownMenuItem(text = { Text(stringResource(R.string.binder_edit)) }, onClick = { menu = false; editing = true })
                            if (custom != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.binder_add_cards)) }, onClick = { menu = false; choosing = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.binder_delete)) }, onClick = { menu = false; deleting = true })
                            }
                        }
                    }
                }
            }

            val open = { c: OwnedCard -> if (turner.progress == 0f) onOpenCard(order, c.id) }
            val current = turner.page.coerceIn(0, total - 1)
            val shownPage = pages.getOrNull(current - 1)
            if (shownPage == null || shownPage.cards.isEmpty()) SideEffect { shareInfo = null } else {
                val footer = listOfNotNull(
                    shownPage.title?.takeIf { it.isNotBlank() },
                    Money.coverage(shownPage.cards, s.currency, s.usdToEur).text(s.currency),
                ).joinToString(" · ")
                SideEffect { shareInfo = PageShareInfo(footer) }
            }
            val sheet: @Composable (Int) -> Unit = { i ->
                if (i == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BinderCover(design, coverImage, coverTitle, coverCount,
                            Modifier.fillMaxHeight().aspectRatio(0.72f, matchHeightConstraintsFirst = true).clickable { openCover() })
                    }
                } else BinderSheet(pages[i - 1], s.binderGrid, s, open)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                if (s.binderAnimation) {
                    PageTurner(
                        state = turner,
                        pageCount = total,
                        modifier = Modifier.fillMaxSize(),
                        pageBack = { BinderSheet(null, s.binderGrid, s, onOpen = {}) },
                    ) { i -> sheet(i) }
                } else {
                    HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 12.dp) { i -> sheet(i) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (s.binderAnimation) turner.previous(scope) else scope.launch { pager.animateScrollToPage(current - 1) }
                }, enabled = current > 0) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.binder_previous)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (current == 0) {
                        Text(stringResource(R.string.binder_open_hint), style = MaterialTheme.typography.labelLarge)
                    } else {
                        val coverage = Money.coverage(pages[current - 1].cards, s.currency, s.usdToEur)
                        Text(stringResource(R.string.binder_page, current, pages.size, coverage.text(s.currency)), style = MaterialTheme.typography.labelLarge)
                        if (coverage.missingCopies > 0) Text(stringResource(R.string.price_coverage_missing, coverage.missingCopies), style = MaterialTheme.typography.labelSmall)
                        if (cards.isEmpty()) {
                            Text(stringResource(if (custom != null) R.string.binder_empty_custom else R.string.binder_empty), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (total > 2) {
                        Slider(
                            value = current.toFloat(),
                            onValueChange = { turner.jump(it.roundToInt()) },
                            valueRange = 0f..(total - 1).toFloat(),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                IconButton(onClick = {
                    if (s.binderAnimation) turner.next(scope) else scope.launch { pager.animateScrollToPage(current + 1) }
                }, enabled = current < total - 1) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.binder_next)) }
            }
        }

        if (editing) {
            BinderEditor(
                name = custom?.name, design = design, image = coverImage,
                onDismiss = { editing = false },
                onSave = { name, newDesign, image ->
                    editing = false
                    if (image != coverImage) coverImage?.let { java.io.File(it).delete() }
                    scope.launch {
                        if (custom != null) repo.updateBinder(custom.copy(name = name ?: custom.name, cover = newDesign.key, coverImage = image))
                        else repo.settings.setMainCover(newDesign.key, image)
                    }
                },
            )
        }
        if (choosing && custom != null) {
            BinderCardPicker(owned, members, s, onDismiss = { choosing = false }) { picked ->
                choosing = false
                scope.launch { repo.setBinderCards(custom.id, picked) }
            }
        }
        if (deleting && custom != null) {
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text(stringResource(R.string.binder_delete)) },
                text = { Text(stringResource(R.string.binder_delete_confirm, custom.name)) },
                // The screen leaves by itself once the binder is gone.
                confirmButton = { TextButton(onClick = { deleting = false; scope.launch { repo.deleteBinder(custom.id) } }) { Text(stringResource(R.string.binder_delete)) } },
                dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
    }
    }
}

@Composable
private fun <T> Menu(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, options: List<T>, name: @Composable (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            icon?.let { Icon(it, null, Modifier.size(18.dp).padding(end = 4.dp)) }
            Text(label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(name(o)) }, onClick = { open = false; onPick(o) }) }
        }
    }
}

/**
 * One binder page: the rings on the left and a grid of pockets. [page] null draws the back of a
 * sheet (empty pockets), seen while a page is turned over.
 */
@Composable
internal fun BinderSheet(page: BinderPage?, grid: Int, s: AppSettings, onOpen: (OwnedCard) -> Unit) {
    val look = LocalLook.current
    val pageColor = look.binderPage
    val ink = look.onBinderPage
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp))
            .background(Brush.horizontalGradient(listOf(lerp(pageColor, Color.Black, 0.25f), pageColor, pageColor, lerp(pageColor, Color.White, 0.06f))))
            .border(1.dp, lerp(pageColor, ink, 0.12f), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)),
    ) {
        // The punched holes where the binder rings go through.
        Column(
            Modifier.width(22.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF111114)).border(1.5.dp, Color(0xFF8C9099), CircleShape))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp, end = 8.dp, bottom = 8.dp)) {
            Text(
                page?.title.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val columns = Binder.columns(grid)
                val rows = Binder.rows(grid)
                val gap = when (columns) { 3 -> if (rows > 3) 6.dp else 8.dp; 6 -> 4.dp; else -> 2.dp }
                val byWidth = (maxWidth - gap * (columns - 1)) / columns
                val byHeight = ((maxHeight - gap * (rows - 1)) / rows) * (63f / 88f)
                val slot = min(byWidth, byHeight)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(rows) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(columns) { c ->
                                Pocket(page?.cards?.getOrNull(r * columns + c), slot, columns, s, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pocket(card: OwnedCard?, width: Dp, grid: Int /* pockets per row */, s: AppSettings, onOpen: (OwnedCard) -> Unit) {
    val shape = RoundedCornerShape(if (grid == 3) 6.dp else 2.dp)
    Box(
        Modifier
            .size(width, width * (88f / 63f))
            .clip(shape)
            .background(LocalLook.current.onBinderPage.copy(alpha = 0.045f))
            .border(0.5.dp, LocalLook.current.onBinderPage.copy(alpha = 0.12f), shape)
            .then(if (card != null) Modifier.clickable { onOpen(card) } else Modifier),
    ) {
        if (card == null) return@Box
        CardOrSlab(card, Modifier.fillMaxSize().padding(if (grid == 9) 1.dp else 2.dp), thumb = grid > 3)
        // The sleeve's shine stays subtle so the card and label remain readable.
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Transparent, Color.White.copy(alpha = 0.025f)))))
        val fs = (width.value * 0.12f).coerceIn(6f, 12f).sp
        if (card.quantity > 1 && grid == 6) {
            Text(
                "×${card.quantity}",
                color = Color.White,
                style = TextStyle(fontFamily = com.monkaydee.tcgcatalogue.ui.theme.AppFontFamily, fontSize = fs, fontWeight = FontWeight.Bold, lineHeight = fs),
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 3.dp),
            )
        }
        if (grid == 3) {
            Text(
                (if (card.quantity > 1) "×${card.quantity} · " else "") + Money.unitText(card, s.currency, s.usdToEur),
                color = Color.White,
                style = TextStyle(fontFamily = com.monkaydee.tcgcatalogue.ui.theme.AppFontFamily, fontSize = fs, fontWeight = FontWeight.Bold, lineHeight = fs),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
    }
}
