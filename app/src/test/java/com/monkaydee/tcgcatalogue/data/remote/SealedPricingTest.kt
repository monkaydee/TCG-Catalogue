package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SealedPricingTest {
    @Test fun germanPokemonSearchAcceptsUmlautsTransliterationsAndPackagingTerms() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("sealed-german-search").toFile()
        try {
            java.io.File(dir,"SEALED_POKEMON.json").writeText("""{"groups":{},"items":[]}""")
            java.io.File(dir,"SEALED_REGIONAL_V5_POKEMON.json").writeText("""{"schemaVersion":5,"items":[
              {"productId":-784949,"name":"Surging Sparks Booster Box","groupName":"Pokémon Display","candidateLanguages":["EN","DE"],"aliases":["Stürmische Funken"],"imageUrls":{"EN":"https://tcgplayer-cdn.tcgplayer.com/product/1_in_400x400.jpg"}},
              {"productId":-784948,"name":"Surging Sparks Booster","groupName":"Pokémon Booster","candidateLanguages":["EN","DE"],"aliases":["Stürmische Funken"]},
              {"productId":-100,"name":"Terastal Festival ex Booster Box","candidateLanguages":["JA"],"aliases":["SV8a"]},
              {"productId":-200,"name":"Prismatic Evolutions Elite Trainer Box","groupName":"Pokémon Top Trainer Box","candidateLanguages":["EN","DE"],"aliases":["Prismatische Entwicklungen"]}
            ]}""")
            val api=CardIndexApi(Http(),dir)
            for (query in listOf("Stürmische Funken","Sturmische Funken","Stuermische Funken","Surging Sparks","Surging-Sparks")) {
                assertEquals(query,2,api.searchSealed(Game.POKEMON,query,"DE").size)
            }
            for (query in listOf("Pokemon Sturmische Funken Boosterbox","Pokémon Stuermische Funken Display")) {
                val product=api.searchSealed(Game.POKEMON,query,"DE").single()
                assertEquals(-784949L,product.productId)
                assertEquals("DE",product.language)
                assertNull(product.imageUrl)
                assertNull(product.price)
            }
            assertTrue(api.searchSealed(Game.POKEMON,"Terastal Festival","DE").isEmpty())
            assertEquals(-200L,api.searchSealed(Game.POKEMON,"Prismatische Entwicklungen Top-Trainer-Box","DE").single().productId)
            assertEquals(-200L,api.searchSealed(Game.POKEMON,"Prismatic Evolutions ETB","DE").single().productId)
        } finally { dir.deleteRecursively() }
    }

    @Test fun thinSealedReferenceRetainsCountAndLimitedEvidence() = runBlocking {
        MockWebServer().use { web ->
            web.start()
            val api=PriceServerApi { web.url("/").toString() to "test-key" }
            web.enqueue(MockResponse().setBody("""{"price":{"amount":65,"currency":"EUR","source":"eBay sealed listings (asking, shipping excluded)","listings":1,"evidence":"limited"}}"""))
            val result=api.sealed(SealedProduct(Game.ONE_PIECE,-9000000010142,"The Azure Sea's Seven Japanese Booster Box","Booster Boxes",null,language="JA"))
            assertEquals(65.0,result.quote!!.amount,0.0)
            assertEquals(1,result.quote!!.listings)
            assertEquals("limited",result.quote!!.evidence)
        }
    }

    @Test fun publisherJapaneseAzureEntriesAreFoundByEnglishNameAndNativeCode() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("sealed-publisher-regression").toFile()
        try {
            java.io.File(dir,"SEALED_ONE_PIECE.json").writeText("""{"groups":{"24537":"The Azure Sea's Seven"},"items":[[665598,"The Azure Sea's Seven Booster Box",24537,269.83,"EN","TCGplayer market"]]}""")
            java.io.File(dir,"SEALED_REGIONAL_V4_ONE_PIECE.json").writeText("""{"schemaVersion":4,"items":[
              {"productId":-864452,"name":"The Azure Sea's Seven Booster Box","candidateLanguages":["EN"],"aliases":["OP14"],"imageUrls":{"EN":"https://tcgplayer-cdn.tcgplayer.com/product/665598_in_400x400.jpg"},"catalogueProductIds":{"EN":665598}},
              {"productId":-9000000010142,"name":"The Azure Sea's Seven Japanese Booster Box","candidateLanguages":["JA"],"languages":["JA"],"aliases":["OP14","OP-14","蒼海の七傑"],"availabilityEvidence":"Japanese set confirmed by Bandai · OP14"},
              {"productId":-9000000010141,"name":"The Azure Sea's Seven Japanese Booster Pack","candidateLanguages":["JA"],"languages":["JA"],"aliases":["OP14","OP-14","蒼海の七傑"],"imageUrl":"https://www.onepiece-cardgame.com/op14-pack.webp"}
            ]}""")
            val api = CardIndexApi(Http(),dir)
            val japanese = api.searchSealed(Game.ONE_PIECE,"the azure","JA")
            assertEquals(2,japanese.size)
            assertTrue(japanese.all { it.language == "JA" && it.price == null })
            assertEquals(2,api.searchSealed(Game.ONE_PIECE,"OP-14","JA").size)
            assertEquals(2,api.searchSealed(Game.ONE_PIECE,"蒼海の七傑","JA").size)
            assertNotNull(japanese.first { it.name.endsWith("Pack") }.imageUrl)
            assertTrue(api.searchSealed(Game.ONE_PIECE,"the azure","DE").isEmpty())
            assertTrue(api.searchSealed(Game.ONE_PIECE,"the azure","EN").single().imageUrl!!.contains("665598"))
            assertEquals(665598L,api.searchSealed(Game.ONE_PIECE,"OP14","EN").single().productId)
        } finally { dir.deleteRecursively() }
    }

    @Test fun matchingPhotoSurvivesAResponseWithoutEnoughPriceEvidence() = runBlocking {
        MockWebServer().use { web ->
            web.start()
            val api = PriceServerApi { web.url("/").toString() to "test-key" }
            val p = SealedProduct(Game.POKEMON,-784949,"Surging Sparks Booster Box","Display",null,language="DE")
            web.enqueue(MockResponse().setBody("""{"price":null,"reason":"not_found","imageUrl":"https://i.ebayimg.com/images/german-box.jpg"}"""))
            val result = api.sealed(p)
            assertNull(result.quote)
            assertEquals("not_found",result.reason)
            assertEquals("https://i.ebayimg.com/images/german-box.jpg",result.imageUrl)
        }
    }

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
