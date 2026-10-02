package com.monkaydee.tcgcatalogue.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** "EUR" or "USD" */
    val currency: String = "EUR",
    /** Which market to price Pokémon cards from; One Piece always uses TCGplayer. */
    val pokemonSource: PriceSource = PriceSource.CARDMARKET,
    val usdToEur: Double = 0.9,
    val lastPriceRefresh: Long = 0,
    /** Add a confidently recognised card straight away and keep scanning. */
    val quickAdd: Boolean = false,
    val defaultCondition: String = "NM",
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
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setCurrency(v: String) = context.dataStore.edit { it[Keys.currency] = v }
    suspend fun setPokemonSource(v: PriceSource) = context.dataStore.edit { it[Keys.pokemonSource] = v.name }
    suspend fun setUsdToEur(v: Double) = context.dataStore.edit { it[Keys.usdToEur] = v }
    suspend fun setLastRefresh(v: Long) = context.dataStore.edit { it[Keys.lastRefresh] = v }
    suspend fun setQuickAdd(v: Boolean) = context.dataStore.edit { it[Keys.quickAdd] = v }
    suspend fun setDefaultCondition(v: String) = context.dataStore.edit { it[Keys.condition] = v }
}
