package com.monkaydee.tcgcatalogue

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.monkaydee.tcgcatalogue.data.CloudBackup
import com.monkaydee.tcgcatalogue.widget.PortfolioWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.SettingsStore
import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.remote.CardIndexApi
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.data.remote.CardmarketPokemon
import com.monkaydee.tcgcatalogue.data.remote.FxApi
import com.monkaydee.tcgcatalogue.data.remote.ScryfallApi
import com.monkaydee.tcgcatalogue.data.remote.TcgPlayerApi
import com.monkaydee.tcgcatalogue.data.remote.Http
import com.monkaydee.tcgcatalogue.data.remote.OnePieceApi
import com.monkaydee.tcgcatalogue.data.remote.TcgDexApi
import com.monkaydee.tcgcatalogue.work.PriceRefreshWorker
import com.monkaydee.tcgcatalogue.ui.AppStrings

class TcgApp : Application() {
    lateinit var repository: CardRepository
        private set

    /** For work that outlives a screen: widget refresh and cloud backup. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)
        val http = Http()
        val cardIndex = CardIndexApi(http, java.io.File(filesDir, "card-index"))
        val tcgdex = TcgDexApi(http)
        repository = CardRepository(
            db = AppDatabase.create(this),
            tcgdex = tcgdex,
            onePiece = OnePieceApi(http),
            scryfall = ScryfallApi(http),
            cardIndex = cardIndex,
            tcgplayer = TcgPlayerApi(http),
            cardmarket = CardmarketApi(cardIndex, http),
            cardmarketPokemon = CardmarketPokemon(cardIndex, http, tcgdex),
            fx = FxApi(http),
            settings = SettingsStore(this),
        )
        PriceRefreshWorker.scheduleDaily(this)

        CloudBackup.init(this, repository)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                // Opening the app (also a cold start): is there a newer collection from another phone?
                Lifecycle.Event.ON_START -> appScope.launch { CloudBackup.checkRemote() }
                // Leaving the app: save if something changed and bring the widget up to date.
                Lifecycle.Event.ON_STOP -> appScope.launch {
                    PortfolioWidget.refresh(this@TcgApp)
                    CloudBackup.autoSave()
                }
                else -> Unit
            }
        })
    }
}
