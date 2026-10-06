package com.monkaydee.tcgcatalogue.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.*
import com.monkaydee.tcgcatalogue.data.remote.*
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[34])
class PriceRefreshRegressionTest {
    @Test fun nativeHoloPriceReachesSavedCardAndMissingQuotesNeverEraseIt() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        AppStrings.init(context)
        var priceFields=""""pricing":{"cardmarket":{"trend":40.9},"tcgplayer":{"holofoil":{"productId":497629,"marketPrice":89.55}}}"""
        var holo=true
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            val request=chain.request()
            val isCard=request.url.encodedPath.endsWith("/cards/sv02-226")
            val body=if (isCard) """{"id":"sv02-226","name":"Maushold","localId":"226","set":{"id":"sv02","name":"Paldea Evolved","cardCount":{"official":193}},"variants":{"holo":$holo,"reverse":${!holo}},$priceFields}""" else ""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if(isCard)200 else 404).message("fixture").body(body.toResponseBody()).build()
        }.build()
        val http=Http(client);val tcgdex=TcgDexApi(http);val index=CardIndexApi(http,java.io.File(context.cacheDir,"price-regression-index"))
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings=SettingsStore(context)
            val repo=CardRepository(db,tcgdex,OnePieceApi(http),ScryfallApi(http),index,TcgPlayerApi(http),CardmarketApi(index,http),CardmarketPokemon(index,http,tcgdex),FxApi(http),settings)
            val row=OwnedCard(game=Game.POKEMON,cardId="sv02-226",variant="holo",variantLabel="Holo",name="Maushold",number="226/193",setId="sv02",setName="Paldea Evolved",language="EN",condition="NM",price=null,purchasePrice=12.0,priceCurrency="EUR")
            db.cards().insert(row)
            assertEquals(1,repo.refreshPrices())
            val priced=db.cards().getAll().single()
            assertNotNull(priced.price);assertTrue(priced.price!!>0);assertNotNull(priced.priceUpdatedAt)
            priceFields="\"pricing\":null"
            repo.refreshPrices()
            val retained=db.cards().getAll().single()
            assertEquals(priced.price,retained.price);assertEquals(priced.priceUpdatedAt,retained.priceUpdatedAt)
            assertEquals(priced.purchasePrice,retained.purchasePrice);assertTrue(retained.priceNote!!.contains("retained",ignoreCase=true))
            holo=false
            repo.refreshPrices()
            assertEquals(priced.price,db.cards().getAll().single().price)
            assertTrue(db.cards().getAll().single().priceNote!!.contains("printing",ignoreCase=true))
        } finally {db.close()}
    }
}
