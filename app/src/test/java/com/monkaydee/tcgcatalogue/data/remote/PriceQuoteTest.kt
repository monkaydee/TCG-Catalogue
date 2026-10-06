package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PriceQuoteTest {
    @Test fun exactConditionAndOriginalFreshnessSurviveTheApi() = runBlocking {
        MockWebServer().use { web ->
            web.start()
            val api=PriceServerApi { web.url("/").toString() to "test-key" }
            val response="""{"results":[{"conditions":{"NM":80,"LP":40},"market":999,"currency":"EUR","source":"eBay condition listings (asking)","fetchedAt":"2026-10-01T00:00:00Z","stale":true,"conditionEvidence":{"LP":{"listings":3,"low":30,"high":50}}}]}"""
            web.enqueue(MockResponse().setBody(response))
            val quote=api.raw(Game.POKEMON,"base1-4","Charizard","Base Set","4/102",null,null,"DE",condition="LP",market="DE")!!
            assertEquals(40.0,quote.amount,0.0);assertEquals(3,quote.listings);assertTrue(quote.stale)
            assertEquals(java.time.Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(),quote.fetchedAt)
            assertTrue(web.takeRequest().body.readUtf8().contains("\"market\":\"DE\""))
            web.enqueue(MockResponse().setBody("""{"results":[{"conditions":{"LP":40},"market":999}]}"""))
            assertNull(api.raw(Game.POKEMON,"x","X","Set","1",null,null))
        }
    }
    @Test fun gradedCacheFreshnessIsIndependentOfRawCache() = runBlocking {
        MockWebServer().use { web ->
            web.start();val api=PriceServerApi { web.url("/").toString() to "test-key" }
            web.enqueue(MockResponse().setBody("""{"results":[{"stale":false,"gradedStale":true,"gradedFetchedAt":"2026-09-01T00:00:00Z","graded":[{"grader":"PSA","grade":"9","price":99,"currency":"EUR","source":"eBay","evidence":"limited"}]}]}"""))
            val quote=api.graded(Game.POKEMON,"x","X","Set","1",null).single()
            assertTrue(quote.stale);assertEquals("limited",quote.evidence)
            assertEquals(java.time.Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(),quote.fetchedAt)
        }
    }
    @Test fun ambiguousOrNonEnglishCardmarketProductsAreNeverAutomaticallySelected() {
        fun listing(id:Long,set:String)=CardmarketApi.Listing(id,"OP01-001","Card",set,1,100.0,null,null,null)
        val japanese=listing(1,"Romance Dawn (Non-English)")
        val english=listing(2,"Romance Dawn")
        assertNull(CardmarketApi.automaticListing(listOf(japanese)))
        assertEquals(english,CardmarketApi.automaticListing(listOf(japanese,english)))
        assertNull(CardmarketApi.automaticListing(listOf(english,listing(3,"Reprint"))))
    }
}
