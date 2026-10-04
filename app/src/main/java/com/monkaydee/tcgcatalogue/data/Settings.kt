package com.monkaydee.tcgcatalogue.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monkaydee.tcgcatalogue.BuildConfig
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** "EUR" or "USD" */
    val currency: String = "EUR",
    /** Which market to price Pokémon and Magic cards from; the other games only have TCGplayer prices. */
    val pokemonSource: PriceSource = PriceSource.CARDMARKET,
    val usdToEur: Double = 0.9,
    val lastPriceRefresh: Long = 0,
    /** Add a confidently recognised card straight away and keep scanning. */
    val quickAdd: Boolean = false,
    val defaultCondition: String = "NM",
    /** Games the scanner looks for; indexed games download their card list when enabled. */
    val enabledGames: Set<Game> = Game.entries.toSet(),
    /** Hide the status and navigation bars (swipe from the edge to show them). */
    val fullScreen: Boolean = true,
    /** Virtual binder: pockets per row and column (3, 6 or 9). */
    val binderGrid: Int = 3,
    val binderSort: BinderSort = BinderSort.SET,
    val binderSetOrder: SetOrder = SetOrder.NUMBER,
    /** Turn the binder's pages like real pages instead of sliding them. */
    val binderAnimation: Boolean = true,
    val look: Look = Look(),
    /**
     * The app's price server (docs/CLOUDFLARE.md) and its app key. Builds made by GitHub Actions
     * have them built in; they can be changed in Settings. Empty = no server.
     */
    val serverUrl: String = BuildConfig.PRICE_SERVER_URL,
    val serverKey: String = BuildConfig.PRICE_SERVER_KEY,
) {
    val hasServer: Boolean get() = serverUrl.startsWith("https://") && serverKey.isNotBlank()
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colour themes: the phone's wallpaper colours (Android 12+), presets, or the user's own accent. */
enum class Palette(val seed: Long) {
    CARDNAVO(0xFF29DBC6),
    DYNAMIC(0xFF3D5AFE),
    INDIGO(0xFF3D5AFE),
    OCEAN(0xFF0277BD),
    TEAL(0xFF00897B),
    FOREST(0xFF2E7D32),
    GOLD(0xFFB8860B),
    SUNSET(0xFFF4511E),
    ROSE(0xFFD81B60),
    GRAPE(0xFF7B1FA2),
    GRAPHITE(0xFF546E7A),
    CUSTOM(0xFF3D5AFE),
}

/** Parts of the app whose colour can be set on its own. */
enum class Area { ACCENT, BACKGROUND, CARDS, TOP_BAR, BOTTOM_BAR, BINDER_PAGE }

/**
 * How the app looks: light/dark, the colour theme, colours set per [Area] (ARGB, missing = the
 * theme's), and the user's own background pictures (files in the app's storage) with how much
 * they are dimmed so text stays readable.
 */
data class Look(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.CARDNAVO,
    val colors: Map<Area, Long> = emptyMap(),
    val homeImage: String? = null,
    val binderImage: String? = null,
    val imageDim: Float = 0.45f,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val currency = stringPreferencesKey("currency")
        val pokemonSource = stringPreferencesKey("pokemon_source")
        val usdToEur = doublePreferencesKey("usd_to_eur")
        val lastRefresh = longPreferencesKey("last_refresh")
        val quickAdd = booleanPreferencesKey("quick_add")
        val condition = stringPreferencesKey("default_condition")
        val games = stringSetPreferencesKey("enabled_games")
        val fullScreen = booleanPreferencesKey("full_screen")
        val binderGrid = intPreferencesKey("binder_grid")
        val binderSort = stringPreferencesKey("binder_sort")
        val binderSetOrder = stringPreferencesKey("binder_set_order")
        val binderAnimation = booleanPreferencesKey("binder_animation")
        val themeMode = stringPreferencesKey("theme_mode")
        val palette = stringPreferencesKey("palette")
        val homeImage = stringPreferencesKey("home_image")
        val binderImage = stringPreferencesKey("binder_image")
        val imageDim = floatPreferencesKey("image_dim")
        val serverUrl = stringPreferencesKey("server_url")
        val serverKey = stringPreferencesKey("server_key")
        fun color(a: Area) = longPreferencesKey("color_${a.name.lowercase()}")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            currency = p[Keys.currency] ?: d.currency,
            pokemonSource = p[Keys.pokemonSource]?.let { runCatching { PriceSource.valueOf(it) }.getOrNull() } ?: d.pokemonSource,
            usdToEur = p[Keys.usdToEur] ?: d.usdToEur,
            lastPriceRefresh = p[Keys.lastRefresh] ?: d.lastPriceRefresh,
            quickAdd = p[Keys.quickAdd] ?: d.quickAdd,
            defaultCondition = p[Keys.condition] ?: d.defaultCondition,
            fullScreen = p[Keys.fullScreen] ?: d.fullScreen,
            binderGrid = p[Keys.binderGrid]?.takeIf { it in Binder.GRIDS } ?: d.binderGrid,
            binderSort = p[Keys.binderSort]?.let { runCatching { BinderSort.valueOf(it) }.getOrNull() } ?: d.binderSort,
            binderSetOrder = p[Keys.binderSetOrder]?.let { runCatching { SetOrder.valueOf(it) }.getOrNull() } ?: d.binderSetOrder,
            binderAnimation = p[Keys.binderAnimation] ?: d.binderAnimation,
            look = Look(
                mode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                palette = p[Keys.palette]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: Palette.DYNAMIC,
                colors = Area.entries.mapNotNull { a -> p[Keys.color(a)]?.let { a to it } }.toMap(),
                homeImage = p[Keys.homeImage],
                binderImage = p[Keys.binderImage],
                imageDim = p[Keys.imageDim] ?: Look().imageDim,
            ),
            enabledGames = p[Keys.games]?.mapNotNull { n -> Game.entries.firstOrNull { it.name == n } }?.toSet() ?: d.enabledGames,
            serverUrl = p[Keys.serverUrl] ?: d.serverUrl,
            serverKey = p[Keys.serverKey] ?: d.serverKey,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setCurrency(v: String) = context.dataStore.edit { it[Keys.currency] = v }
    suspend fun setPokemonSource(v: PriceSource) = context.dataStore.edit { it[Keys.pokemonSource] = v.name }
    suspend fun setUsdToEur(v: Double) = context.dataStore.edit { it[Keys.usdToEur] = v }
    suspend fun setLastRefresh(v: Long) = context.dataStore.edit { it[Keys.lastRefresh] = v }
    suspend fun setQuickAdd(v: Boolean) = context.dataStore.edit { it[Keys.quickAdd] = v }
    suspend fun setDefaultCondition(v: String) = context.dataStore.edit { it[Keys.condition] = v }
    suspend fun setFullScreen(v: Boolean) = context.dataStore.edit { it[Keys.fullScreen] = v }
    suspend fun setBinderGrid(v: Int) = context.dataStore.edit { it[Keys.binderGrid] = v }
    suspend fun setBinderSort(v: BinderSort) = context.dataStore.edit { it[Keys.binderSort] = v.name }
    suspend fun setBinderSetOrder(v: SetOrder) = context.dataStore.edit { it[Keys.binderSetOrder] = v.name }
    suspend fun setBinderAnimation(v: Boolean) = context.dataStore.edit { it[Keys.binderAnimation] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = v.name }
    suspend fun setPalette(v: Palette) = context.dataStore.edit { it[Keys.palette] = v.name }

    /** Sets the colour of [area]; null goes back to the theme's colour. */
    suspend fun setColor(area: Area, argb: Long?) = context.dataStore.edit { if (argb == null) it.remove(Keys.color(area)) else it[Keys.color(area)] = argb }
    suspend fun resetColors() = context.dataStore.edit { p -> Area.entries.forEach { p.remove(Keys.color(it)) } }
    suspend fun setHomeImage(path: String?) = context.dataStore.edit { if (path == null) it.remove(Keys.homeImage) else it[Keys.homeImage] = path }
    suspend fun setBinderImage(path: String?) = context.dataStore.edit { if (path == null) it.remove(Keys.binderImage) else it[Keys.binderImage] = path }
    suspend fun setImageDim(v: Float) = context.dataStore.edit { it[Keys.imageDim] = v }
    /** Sets the price server; blank values go back to the built-in ones. */
    suspend fun setServer(url: String, key: String) = context.dataStore.edit {
        if (url.isBlank()) it.remove(Keys.serverUrl) else it[Keys.serverUrl] = url.trim()
        if (key.isBlank()) it.remove(Keys.serverKey) else it[Keys.serverKey] = key.trim()
    }
    suspend fun setEnabledGames(v: Set<Game>) = context.dataStore.edit { it[Keys.games] = v.map { g -> g.name }.toSet() }
}
