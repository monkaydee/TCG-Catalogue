package com.monkaydee.tcgcatalogue

import android.app.Application
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

class TcgApp : Application() {
    lateinit var repository: CardRepository
        private set

    override fun onCreate() {
        super.onCreate()
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
    }
}
