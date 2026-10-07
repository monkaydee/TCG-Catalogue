package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SealedPricingTest {
    @Test fun yesterdayCatalogueDoesNotInventGermanOnePieceOrGermanJapanesePokemon() {
        assertEquals(listOf("EN"), CardIndexApi.regionalCandidateLanguages(Game.ONE_PIECE, "Two Legends Booster Box", listOf("OP08"), emptyList()))
        assertEquals(listOf("JA"), CardIndexApi.regionalCandidateLanguages(Game.ONE_PIECE, "Two Legends Booster Box (Non-English)", listOf("OP08"), emptyList()))
        assertEquals(listOf("JA"), CardIndexApi.regionalCandidateLanguages(Game.POKEMON, "Terastal Festival ex Booster Box", listOf("SV8a"), emptyList()))
        assertEquals(listOf("ZH"), CardIndexApi.regionalCandidateLanguages(Game.POKEMON, "Terastal Festival ex Chinese Gift Box", listOf("SV8a"), emptyList()))
        assertEquals(listOf("EN", "DE"), CardIndexApi.regionalCandidateLanguages(Game.POKEMON, "Surging Sparks Booster Box", listOf("Stürmische Funken"), emptyList()))
        assertEquals(listOf("EN"), CardIndexApi.regionalCandidateLanguages(Game.POKEMON, "151 English Booster Bundle", emptyList(), emptyList()))
    }

    @Test fun japaneseQuoteRetainsLanguageOriginalCurrencyAndAskingSource() = runBlocking {
        MockWebServer().use { web ->
            web.start()
            val api = PriceServerApi { web.url("/").toString() to "test-key" }
            web.enqueue(MockResponse().setBody("""{"price":{"amount":60,"currency":"USD","source":"eBay sealed listings (asking, shipping excluded) · EBAY_US international reference","listings":8,"fetchedAt":"2026-10-07T00:00:00Z"}}"""))
            val p = SealedProduct(Game.ONE_PIECE, -766868, "Two Legends Booster Box (Non-English)", "Booster Boxes", null, language="JA", aliases=listOf("OP08"), market="DE")
            val result = api.sealed(p)
            assertEquals(60.0, result.quote!!.amount, 0.0)
            assertEquals("USD", result.quote!!.currency)
            assertTrue(result.quote!!.source.contains("asking"))
            assertEquals(8, result.quote!!.listings)
            val body = web.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"language\":\"JA\""))
            assertTrue(body.contains("\"market\":\"DE\""))
            assertTrue(body.contains("OP08"))
        }
    }

    @Test fun noMatchAndProviderFailureHaveDifferentSearchStatuses() = runBlocking {
        MockWebServer().use { web ->
            web.start()
            val api = PriceServerApi { web.url("/").toString() to "test-key" }
            val p = SealedProduct(Game.POKEMON, -784949, "Surging Sparks Booster Box", "Display", null, language="DE")
            for (reason in listOf("not_found", "unavailable", "budget_exhausted")) {
                web.enqueue(MockResponse().setBody("""{"price":null,"reason":"$reason"}"""))
                val result = api.sealed(p)
                assertNull(result.quote)
                assertEquals(reason, result.reason)
            }
        }
    }
}
