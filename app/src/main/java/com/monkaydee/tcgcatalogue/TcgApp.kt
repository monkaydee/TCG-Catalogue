package com.monkaydee.tcgcatalogue

import android.app.Application
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.SettingsStore
import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.remote.CardIndexApi
import com.monkaydee.tcgcatalogue.data.remote.FxApi
import com.monkaydee.tcgcatalogue.data.remote.PriceChartingApi
import com.monkaydee.tcgcatalogue.data.remote.ScryfallApi
import com.monkaydee.tcgcatalogue.data.remote.Http
import com.monkaydee.tcgcatalogue.data.remote.OnePieceApi
import com.monkaydee.tcgcatalogue.data.remote.TcgDexApi
import com.monkaydee.tcgcatalogue.work.PriceRefreshWorker

class TcgApp : Application() {
    lateinit var repository: CardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val http = Http()
        repository = CardRepository(
            db = AppDatabase.create(this),
            tcgdex = TcgDexApi(http),
            onePiece = OnePieceApi(http),
            scryfall = ScryfallApi(http),
            cardIndex = CardIndexApi(http, java.io.File(filesDir, "card-index")),
            priceCharting = PriceChartingApi(http),
            fx = FxApi(http),
            settings = SettingsStore(this),
        )
        PriceRefreshWorker.scheduleDaily(this)
    }
}
