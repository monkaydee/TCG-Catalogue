package com.monkaydee.tcgcatalogue.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.scan.SharedPhotos
import com.monkaydee.tcgcatalogue.ui.screens.BinderScreen
import com.monkaydee.tcgcatalogue.ui.screens.CardBrowse
import com.monkaydee.tcgcatalogue.ui.screens.CardScreen
import com.monkaydee.tcgcatalogue.ui.screens.ImportScreen
import com.monkaydee.tcgcatalogue.ui.screens.HomeScreen
import com.monkaydee.tcgcatalogue.ui.screens.ScanScreen
import com.monkaydee.tcgcatalogue.ui.screens.SearchScreen
import com.monkaydee.tcgcatalogue.ui.screens.SetScreen
import com.monkaydee.tcgcatalogue.ui.screens.SettingsScreen
import com.monkaydee.tcgcatalogue.work.PriceRefreshWorker
import kotlinx.coroutines.flow.map

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Collection", Icons.Default.Collections),
    Tab("scan", "Scan", Icons.Default.CameraAlt),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun AppNav(repo: CardRepository) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val refreshState by remember { PriceRefreshWorker.observeNow(context).map { it.firstOrNull()?.state } }.collectAsState(initial = null)
    val refresh = { PriceRefreshWorker.runNow(context) }

    // Photos shared from another app open the import screen, which picks them up.
    val shared by SharedPhotos.pending.collectAsState()
    LaunchedEffect(shared) {
        if (shared.isNotEmpty() && nav.currentDestination?.route?.startsWith("import") != true) nav.navigate("import")
    }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding)
                // In full screen the bars are hidden; still keep text away from the camera hole.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
        ) {
            composable("home") {
                HomeScreen(
                    repo = repo,
                    refreshState = refreshState,
                    onRefresh = refresh,
                    onOpenSet = { g, id -> nav.navigate("set/${g.name}/${android.net.Uri.encode(id)}") },
                    onOpenCard = { nav.navigate("card/$it") },
                    onSearch = { nav.navigate("search") },
                    onScan = { goTab("scan") },
                    onPhotos = { nav.navigate("import?pick=true") },
                    onBinder = { nav.navigate("binder") },
                )
            }
            composable("binder") {
                BinderScreen(repo, onBack = { nav.popBackStack() }, onOpenCard = { ids, id -> CardBrowse.open(ids, id) { nav.navigate("card/$it") } })
            }
            composable("scan") {
                ScanScreen(repo, onManual = { nav.navigate("search") }, onPhotos = { nav.navigate("import?pick=true") })
            }
            composable("import?pick={pick}", arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })) { e ->
                ImportScreen(
                    repo = repo,
                    openPicker = e.arguments?.getBoolean("pick") == true,
                    onBack = { nav.popBackStack() },
                    onManual = { nav.navigate("search") },
                )
            }
            composable("settings") { SettingsScreen(repo, onRefresh = refresh) }
            composable(
                "search?replace={replace}",
                arguments = listOf(navArgument("replace") { type = NavType.LongType; defaultValue = -1L }),
            ) { e ->
                SearchScreen(repo, replaceId = e.arguments?.getLong("replace")?.takeIf { it >= 0 }, onBack = { nav.popBackStack() })
            }
            composable(
                "set/{game}/{setId}",
                arguments = listOf(navArgument("game") { type = NavType.StringType }, navArgument("setId") { type = NavType.StringType }),
            ) { e ->
                SetScreen(
                    repo = repo,
                    game = Game.valueOf(e.arguments!!.getString("game")!!),
                    setId = e.arguments!!.getString("setId")!!,
                    onBack = { nav.popBackStack() },
                    onOpenCard = { nav.navigate("card/$it") },
                )
            }
            composable("card/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                CardScreen(repo, e.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onReplace = { nav.navigate("search?replace=$it") })
            }
        }
    }
}
