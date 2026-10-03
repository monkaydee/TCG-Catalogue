package com.monkaydee.tcgcatalogue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import com.monkaydee.tcgcatalogue.ui.components.slabStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PageColor = Color(0xFF2B2D33)
private val PageEdge = Color(0xFF3A3D45)
private val PocketColor = Color(0x1FFFFFFF)

/**
 * The collection as a virtual binder: pages of 3×3, 6×6 or 9×9 pockets, sorted by value, name or
 * set (each set starting on a new page). Pages turn like real binder pages, or slide when the
 * animation is off. Tapping a card opens it; swiping there follows the binder's order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderScreen(repo: CardRepository, onBack: () -> Unit, onOpenCard: (List<Long>, Long) -> Unit) {
    val settings by repo.settings.flow.collectAsState(initial = null)
    val all by repo.cards.collectAsState(initial = null)
    val setList by repo.sets.collectAsState(initial = emptyList())
    var game by rememberSaveable { mutableStateOf<Game?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Binder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val s = settings
        val cards = all
        if (s == null || cards == null) return@Scaffold
        val sets = remember(setList) { setList.associateBy { it.game to it.setId } }
        val shown = cards.filter { game == null || it.game == game }
        val perPage = s.binderGrid * s.binderGrid
        val pages = remember(shown, s.binderSort, s.binderSetOrder, perPage, sets, s.currency, s.usdToEur) {
            Binder.pages(shown, s.binderSort, s.binderSetOrder, perPage, sets) { Money.unit(it, s.currency, s.usdToEur) }
        }
        val order = remember(pages) { pages.flatMap { p -> p.cards.map { it.id } } }
        val turner = rememberPageTurnState()
        // After a change of grid the binder stays at the same card; after a new sort or filter it starts over.
        var keepCard by remember { mutableStateOf<Long?>(null) }
        var restart by remember { mutableStateOf(false) }
        LaunchedEffect(pages) {
            keepCard?.let { id -> turner.jump(pages.indexOfFirst { p -> p.cards.any { it.id == id } }.coerceAtLeast(0)) }
            if (restart) turner.jump(0)
            keepCard = null
            restart = false
        }
        val pager = rememberPagerState(initialPage = turner.page) { pages.size }
        LaunchedEffect(turner.page, s.binderAnimation) { if (!s.binderAnimation && pager.currentPage != turner.page) pager.scrollToPage(turner.page) }
        LaunchedEffect(pager.currentPage) { if (!s.binderAnimation) turner.jump(pager.currentPage) }

        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val games = Game.entries.filter { g -> cards.any { it.game == g } }
                if (games.size > 1) {
                    GameChips(selected = game, onSelect = { game = it; restart = true }, games = games, nullLabel = "All")
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Binder.GRIDS.forEach { g ->
                        FilterChip(s.binderGrid == g, {
                            keepCard = pages.getOrNull(turner.page)?.cards?.firstOrNull()?.id
                            scope.launch { repo.settings.setBinderGrid(g) }
                        }, { Text("$g×$g") })
                    }
                    Menu(Icons.AutoMirrored.Filled.Sort, s.binderSort.label, BinderSort.entries, { it.label }) {
                        restart = true
                        scope.launch { repo.settings.setBinderSort(it) }
                    }
                    if (s.binderSort == BinderSort.SET) {
                        Menu(null, "In set: ${s.binderSetOrder.label}", SetOrder.entries, { it.label }) {
                            restart = true
                            scope.launch { repo.settings.setBinderSetOrder(it) }
                        }
                    }
                    FilterChip(s.binderAnimation, { scope.launch { repo.settings.setBinderAnimation(!s.binderAnimation) } }, { Text("Page-turn animation") })
                }
            }

            if (pages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No cards yet — scan some to fill your binder.", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }
            val open = { c: OwnedCard -> onOpenCard(order, c.id) }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (s.binderAnimation) {
                    PageTurner(
                        state = turner,
                        pageCount = pages.size,
                        modifier = Modifier.fillMaxSize(),
                        pageBack = { BinderSheet(null, s.binderGrid, s, onOpen = {}) },
                    ) { i -> BinderSheet(pages[i], s.binderGrid, s, open) }
                } else {
                    HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 12.dp) { i -> BinderSheet(pages[i], s.binderGrid, s, open) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val page = turner.page.coerceIn(pages.indices)
                IconButton(onClick = {
                    if (s.binderAnimation) turner.previous(scope) else scope.launch { pager.animateScrollToPage(page - 1) }
                }, enabled = page > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Page ${page + 1} of ${pages.size} · ${Money.format(pages[page].cards.sumOf { Money.value(it, s.currency, s.usdToEur) }, s.currency)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (pages.size > 2) {
                        Slider(
                            value = page.toFloat(),
                            onValueChange = { turner.jump(it.roundToInt()) },
                            valueRange = 0f..(pages.size - 1).toFloat(),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                IconButton(onClick = {
                    if (s.binderAnimation) turner.next(scope) else scope.launch { pager.animateScrollToPage(page + 1) }
                }, enabled = page < pages.size - 1) { Icon(Icons.Default.ChevronRight, "Next page") }
            }
        }
    }
}

@Composable
private fun <T> Menu(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, options: List<T>, name: (T) -> String, onPick: (T) -> Unit) {
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
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1F2025), PageColor, PageColor, Color(0xFF30333A))))
            .border(1.dp, PageEdge, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)),
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
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val gap = when (grid) { 3 -> 8.dp; 6 -> 4.dp; else -> 2.dp }
                val byWidth = (maxWidth - gap * (grid - 1)) / grid
                val byHeight = ((maxHeight - gap * (grid - 1)) / grid) * (63f / 88f)
                val slot = min(byWidth, byHeight)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(grid) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(grid) { c ->
                                Pocket(page?.cards?.getOrNull(r * grid + c), slot, grid, s, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pocket(card: OwnedCard?, width: Dp, grid: Int, s: AppSettings, onOpen: (OwnedCard) -> Unit) {
    val shape = RoundedCornerShape(if (grid == 3) 6.dp else 2.dp)
    Box(
        Modifier
            .size(width, width * (88f / 63f))
            .clip(shape)
            .background(PocketColor)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
            .then(if (card != null) Modifier.clickable { onOpen(card) } else Modifier),
    ) {
        if (card == null) return@Box
        CardImage(card.imageUrl, Modifier.fillMaxSize().padding(if (grid == 9) 1.dp else 2.dp), thumb = grid > 3)
        // The sleeve's shine.
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.05f)))))
        val fs = (width.value * 0.12f).coerceIn(6f, 12f).sp
        if (card.graded) {
            val style = slabStyle(card.grader, card.grade, card.gradeQualifier)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(style.label)
                    .padding(vertical = if (grid == 9) 1.dp else 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (grid < 9) {
                    Text(
                        listOfNotNull(card.grader?.takeUnless { it == "Other" }, card.grade).joinToString(" "),
                        color = style.text,
                        style = TextStyle(fontSize = fs, fontWeight = FontWeight.Black, lineHeight = fs),
                        maxLines = 1,
                    )
                }
            }
        }
        if (card.quantity > 1 && grid == 6) {
            Text(
                "×${card.quantity}",
                color = Color.White,
                style = TextStyle(fontSize = fs, fontWeight = FontWeight.Bold, lineHeight = fs),
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 3.dp),
            )
        }
        if (grid == 3) {
            Text(
                (if (card.quantity > 1) "×${card.quantity} · " else "") + Money.format(Money.unit(card, s.currency, s.usdToEur), s.currency),
                color = Color.White,
                style = TextStyle(fontSize = fs, fontWeight = FontWeight.Bold, lineHeight = fs),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
