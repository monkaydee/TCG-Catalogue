package com.monkaydee.tcgcatalogue.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
            enabledGames = p[Keys.games]?.mapNotNull { n -> Game.entries.firstOrNull { it.name == n } }?.toSet() ?: d.enabledGames,
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
    suspend fun setEnabledGames(v: Set<Game>) = context.dataStore.edit { it[Keys.games] = v.map { g -> g.name }.toSet() }
}
