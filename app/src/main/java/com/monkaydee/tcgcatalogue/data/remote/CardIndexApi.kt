package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Card data for the games without a dedicated API (Union Arena, Weiss Schwarz, Dragon Ball,
 * Naruto). A GitHub Action (scripts/build_card_index.py) builds one JSON file per game every day
 * from TCGplayer's catalogue and prices and publishes it on the repository's `data` branch.
 * The app downloads the files of the games it needs and looks scanned codes up locally.
 */
class CardIndexApi(private val http: Http, private val dir: File) {
    data class Group(val id: Int, val name: String, val abbreviation: String, val size: Int)

    data class Entry(
        val number: String,
        val name: String,
        val groupId: Int,
        val rarity: String?,
        val productId: Long,
        val prices: Map<String, Double>,
    )

    class Index(val game: Game, val updated: String, val groups: Map<Int, Group>, val entries: List<Entry>) {
        val byNumber: Map<String, List<Entry>> = entries.groupBy { it.number }
        val byProduct: Map<Long, Entry> = entries.associateBy { it.productId }
    }

    private val loaded = ConcurrentHashMap<Game, Index>()
    private val mutex = Mutex()

    /** Index already in memory (used by the scanner, which can't wait for a download). */
    fun cached(game: Game): Index? = loaded[game]

    /** Loads [game]'s index, downloading a fresh copy when the local one is older than [maxAgeMs]. */
    suspend fun index(game: Game, maxAgeMs: Long = DAY): Index? = mutex.withLock {
        val file = File(dir, "${game.name}.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs
        loaded[game]?.takeIf { fresh }?.let { return it }
        if (!fresh) {
            runCatching {
                val text = http.getText("$BASE/${game.name}.json", accept = "application/json")
                if (!text.isNullOrBlank()) withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    val tmp = File(dir, "${game.name}.json.tmp")
                    tmp.writeText(text)
                    tmp.renameTo(file)
                }
            }
        }
        if (!file.exists()) return null
        val index = withContext(Dispatchers.Default) { parse(game, file.readText()) }
        loaded[game] = index
        index
    }

    suspend fun lookup(game: Game, code: String): List<CardCandidate> {
        val index = index(game) ?: return emptyList()
        return index.byNumber[code].orEmpty().map { candidate(index, it) }
    }

    suspend fun byProduct(game: Game, productId: Long): CardCandidate? {
        val index = index(game) ?: return null
        return index.byProduct[productId]?.let { candidate(index, it) }
    }

    /** Cards whose code starts with or whose name contains [query]. */
    suspend fun search(game: Game, query: String, limit: Int = 60): List<CardCandidate> {
        val index = index(game) ?: return emptyList()
        val q = query.trim().uppercase()
        val byCode = index.entries.filter { it.number.startsWith(q.replace(" ", "")) }
        val byName = index.entries.filter { it.name.uppercase().contains(q) }
        return (byCode + byName).distinctBy { it.productId }.take(limit).map { candidate(index, it) }
    }

    private fun candidate(index: Index, e: Entry): CardCandidate {
        val group = index.groups[e.groupId]
        val variants = e.prices.entries.map { (subType, price) ->
            Variant(subType, subType, mapOf(PriceSource.TCGPLAYER to price))
        }.ifEmpty { listOf(Variant("Normal", "Normal", emptyMap())) }
        return CardCandidate(
            game = index.game,
            cardId = e.productId.toString(),
            name = e.name,
            number = e.number,
            setId = e.groupId.toString(),
            setName = group?.name.orEmpty(),
            setTotal = group?.size ?: 0,
            rarity = e.rarity,
            imageUrl = "https://tcgplayer-cdn.tcgplayer.com/product/${e.productId}_in_1000x1000.jpg",
            variants = variants,
            score = 1.0,
        )
    }

    private fun parse(game: Game, text: String): Index {
        val root = http.json.parseToJsonElement(text)
        val groups = root["groups"].obj().orEmpty().mapNotNull { (id, v) ->
            val a = v.arr() ?: return@mapNotNull null
            val gid = id.toIntOrNull() ?: return@mapNotNull null
            gid to Group(gid, a.getOrNull(0).str().orEmpty(), a.getOrNull(1).str().orEmpty(), a.getOrNull(2).int() ?: 0)
        }.toMap()
        val entries = root["cards"].arr().orEmpty().mapNotNull { c: JsonElement ->
            val a = c.arr() ?: return@mapNotNull null
            Entry(
                number = a.getOrNull(0).str() ?: return@mapNotNull null,
                name = a.getOrNull(1).str().orEmpty(),
                groupId = a.getOrNull(2).int() ?: 0,
                rarity = a.getOrNull(3).str(),
                productId = a.getOrNull(4).str()?.toLongOrNull() ?: return@mapNotNull null,
                prices = a.getOrNull(5).obj().orEmpty().mapNotNull { (k, v) -> v.dbl()?.let { k to it } }.toMap(),
            )
        }
        return Index(game, root["updated"].str().orEmpty(), groups, entries)
    }

    companion object {
        const val BASE = "https://raw.githubusercontent.com/monkaydee/TCG-Catalogue/data"
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
