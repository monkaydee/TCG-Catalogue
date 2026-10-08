package com.monkaydee.tcgcatalogue.data.remote

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class TcgDexEditionPricingTest {
    @Test fun modernCatalogueKeysRetainExactEditionProductAndPrinting() = runBlocking {
        AppStrings.init(ApplicationProvider.getApplicationContext<Context>())
        for ((id, product) in listOf("base5-4" to 84572L, "base2-3" to 45129L)) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val body = """{"id":"$id","name":"Fixture","localId":"4","set":{"id":"base5","name":"Team Rocket","cardCount":{"official":82}},"variants":{"firstEdition":true,"holo":true},"pricing":{"tcgplayer":{"1st-edition-holofoil":{"productId":$product,"marketPrice":480},"unlimited-holofoil":{"productId":$product,"marketPrice":100}}}}"""
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body.toResponseBody()).build()
            }.build()
            val card = TcgDexApi(Http(client)).card(id)!!
            assertTrue(card.printingUnique)
            val first = card.variants.first { it.key == "firstEdition" }
            val unlimited = card.variants.first { it.key == "holo" }
            assertEquals(product, first.tcgplayerId)
            assertEquals("1st Edition Holofoil", first.tcgplayerPrinting)
            assertEquals(480.0, first.prices[PriceSource.TCGPLAYER]!!, 0.0)
            assertEquals(product, unlimited.tcgplayerId)
            assertEquals("Holofoil", unlimited.tcgplayerPrinting)
            assertEquals(100.0, unlimited.prices[PriceSource.TCGPLAYER]!!, 0.0)
        }
    }
}
