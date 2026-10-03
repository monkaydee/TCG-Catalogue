package com.monkaydee.tcgcatalogue.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cardmarket's One Piece listings and EUR prices by card code, from the daily
 * CARDMARKET_ONE_PIECE.json on the data branch (built from Cardmarket's public price guide).
 * Cardmarket lists every print of a card separately (original, reprints, alt arts, promos),
 * so the user picks the listing that matches their copy.
 */
class CardmarketApi(private val files: CardIndexApi, private val http: Http) {
    data class Listing(
        val productId: Long,
        val code: String,
        val name: String,
        val expansion: String,
        /** Cardmarket's V.1, V.2 ... within a set, 0 when the set has only one print */
        val version: Int,
        val trend: Double?,
        val low: Double?,
        val avg7: Double?,
        val avg30: Double?,
    ) {
        val label: String get() = expansion + if (version > 0) " · V.$version" else ""
        val nonEnglish: Boolean get() = expansion.contains("Non-English")

        /** The trend price; the 7-day average when the trend is clearly off (below half the cheapest offer). */
        val price: Double?
            get() = trend?.takeIf { it > 0 && (low == null || it >= low * 0.5) } ?: avg7 ?: avg30 ?: trend ?: low
    }

    private val mutex = Mutex()
    private var byCode: Map<String, List<Listing>> = emptyMap()
    private var byId: Map<Long, Listing> = emptyMap()
    private var loadedFrom = 0L

    suspend fun listings(code: String): List<Listing> = load()[code].orEmpty()
        .sortedWith(compareBy<Listing> { it.nonEnglish }.thenBy { it.expansion }.thenBy { it.version })

    suspend fun byProduct(id: Long): Listing? {
        load()
        return byId[id]
    }

    private suspend fun load(): Map<String, List<Listing>> = mutex.withLock {
        val file = files.dailyFile(FILE) ?: return byCode
        if (file.lastModified() == loadedFrom && byCode.isNotEmpty()) return byCode
        val parsed = withContext(Dispatchers.Default) {
            val root = http.json.parseToJsonElement(file.readText())
            val expansions = root["expansions"].obj().orEmpty().mapValues { it.value.str().orEmpty() }
            root["cards"].arr().orEmpty().mapNotNull { c ->
                val a = c.arr() ?: return@mapNotNull null
                Listing(
                    code = a.getOrNull(0).str() ?: return@mapNotNull null,
                    name = a.getOrNull(1).str().orEmpty(),
                    productId = a.getOrNull(2).str()?.toLongOrNull() ?: return@mapNotNull null,
                    expansion = expansions[a.getOrNull(3).str()] ?: "Cardmarket",
                    version = a.getOrNull(4).int() ?: 0,
                    trend = a.getOrNull(5).dbl(),
                    low = a.getOrNull(6).dbl(),
                    avg7 = a.getOrNull(7).dbl(),
                    avg30 = a.getOrNull(8).dbl(),
                )
            }
        }
        byCode = parsed.groupBy { it.code }
        byId = parsed.associateBy { it.productId }
        loadedFrom = file.lastModified()
        byCode
    }

    companion object {
        const val FILE = "CARDMARKET_ONE_PIECE.json"
    }
}
