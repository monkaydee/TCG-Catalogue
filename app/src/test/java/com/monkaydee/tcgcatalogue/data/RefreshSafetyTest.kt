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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RefreshSafetyTest {
    private class Fixture(server: PriceServerApi = PriceServerApi { null }) : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val http = Http(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/cards/base1-4"))
                """{"id":"base1-4","name":"Charizard","localId":"4","set":{"id":"base1","name":"Base Set","cardCount":{"official":102}},"variants":{"holo":true},"pricing":null}""" else ""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (body.isEmpty()) 404 else 200)
                .message("fixture").body(body.toResponseBody()).build()
        }.build())
        val dir = File(context.cacheDir,"refresh-safety-${java.util.UUID.randomUUID()}")
        val index = CardIndexApi(http,dir)
        val dex = TcgDexApi(http)
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsStore(context)
        val repo = CardRepository(db,dex,OnePieceApi(http),ScryfallApi(http),index,TcgPlayerApi(http),
            CardmarketApi(index,http),CardmarketPokemon(index,http,dex),FxApi(http),settings,server=server)
        init { AppStrings.init(context) }
        override fun close() { db.close(); dir.deleteRecursively() }
    }
    @Test fun sealedQuotePreservesEditsAndCannotResurrectDeletedOrChangedRows() = runBlocking {
        Fixture().use { f ->
            val id = f.db.sealed().upsert(SealedItem(game=Game.POKEMON,productId=-1,name="Box",groupName="Set",
                price=100.0,quantity=1,purchasePrice=30.0,language="DE",priceCurrency="EUR"))
            val original = f.db.sealed().get(id)!!
            val edited = original.copy(quantity=7,purchasePrice=50.0,purchaseCurrency="EUR")
            f.db.sealed().update(edited)
            val quote = SealedProduct(Game.POKEMON,-1,"Box","Set",120.0,language="DE",currency="USD",source="asking")
            f.repo.applySealedQuote(original,quote)
            val current=f.db.sealed().get(id)!!
            assertEquals(7,current.quantity); assertEquals(50.0,current.purchasePrice!!,0.0)
            assertEquals("EUR",current.purchaseCurrency); assertEquals(120.0,current.price!!,0.0)
            f.repo.applySealedQuote(original,null)
            assertEquals(7,f.db.sealed().get(id)!!.quantity)
            f.db.sealed().update(current.copy(language="JA",price=77.0))
            f.repo.applySealedQuote(original,quote)
            assertEquals(77.0,f.db.sealed().get(id)!!.price!!,0.0)
            f.db.sealed().delete(f.db.sealed().get(id)!!)
            f.repo.applySealedQuote(original,quote)
            assertNull(f.db.sealed().get(id))
        }
    }
    @Test fun rawProviderOutageRetainsQuoteWithUnavailableNotice() = runBlocking {
        MockWebServer().use { web ->
            web.start()
            Fixture(PriceServerApi { web.url("/").toString() to "test" }).use { f ->
            val id=f.db.cards().insert(OwnedCard(game=Game.POKEMON,cardId="base1-4",variant="holo",variantLabel="Holo",
                name="Charizard",number="4/102",setId="base1",setName="Base Set",price=90.0,priceCurrency="EUR",priceUpdatedAt=123L))
            web.enqueue(MockResponse().setBody("""{"results":[{"reason":"unavailable","conditions":null}]}"""))
            assertFalse(f.repo.refreshPrice(id))
            val retained=f.db.cards().get(id)!!
            assertEquals(90.0,retained.price!!,0.0); assertEquals(123L,retained.priceUpdatedAt)
            assertTrue(retained.priceNote!!,retained.priceNote!!.contains("Latest refresh unavailable"))
            web.enqueue(MockResponse().setBody("""{"results":[{"reason":"not_found","conditions":null}]}"""))
            assertFalse(f.repo.refreshPrice(id))
            assertFalse(f.db.cards().get(id)!!.priceNote!!.contains("Latest refresh unavailable"))
            Unit
        } }
    }
}
