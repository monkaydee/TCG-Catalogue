package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.ui.AppStrings
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

    /** A file from the data branch, downloaded at most once a day; the old copy is kept if a download fails. */
    suspend fun dailyFile(name: String, maxAgeMs: Long = DAY): File? = mutex.withLock {
        val file = File(dir, name)
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() >= maxAgeMs) {
            runCatching {
                val text = http.getText("$BASE/$name", accept = "application/json")
                if (!text.isNullOrBlank()) withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    val tmp = File(dir, "$name.tmp")
                    tmp.writeText(text)
                    tmp.renameTo(file)
                }
            }
        }
        file.takeIf { it.exists() }
    }

    /** One Pokémon card in the name index: TCGdex id, number, and its names (English first). */
    data class NamedCard(val id: String, val number: String, val names: List<String>)

    @Volatile private var pokemonNames: List<NamedCard>? = null

    /**
     * Pokémon cards whose name in English, German, French, Italian, Spanish or Portuguese contains
     * [word] (from the daily name index; null when it isn't downloaded yet).
     */
    suspend fun pokemonByName(word: String): List<NamedCard>? {
        val all = pokemonNames ?: withContext(Dispatchers.IO) {
            val file = dailyFile("NAMES_POKEMON.json") ?: return@withContext null
            runCatching {
                http.json.parseToJsonElement(file.readText())["cards"].arr().orEmpty().mapNotNull { c ->
                    val a = c.arr() ?: return@mapNotNull null
                    NamedCard(a.getOrNull(0).str() ?: return@mapNotNull null, a.getOrNull(1).str().orEmpty(), a.drop(2).mapNotNull { it.str() })
                }
            }.getOrNull()
        }?.also { pokemonNames = it } ?: return null
        val w = word.lowercase()
        return all.filter { card -> card.names.any { it.lowercase().contains(w) } }
    }

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

    private val sealedLoaded = ConcurrentHashMap<Game, List<SealedProduct>>()

    /** The sealed products of [game] (booster boxes, packs, decks …) with their TCGplayer market prices. */
    suspend fun sealed(game: Game): List<SealedProduct> {
        val file = dailyFile("SEALED_${game.name}.json") ?: return emptyList()
        sealedLoaded[game]?.takeIf { file.lastModified() == sealedStamp[game] }?.let { return it }
        val list = withContext(Dispatchers.Default) {
            val root = http.json.parseToJsonElement(file.readText())
            val groups = root["groups"].obj().orEmpty().mapValues { it.value.str().orEmpty() }
            root["items"].arr().orEmpty().mapNotNull { row ->
                val a = row.arr() ?: return@mapNotNull null
                val id = a.getOrNull(0).str()?.toLongOrNull() ?: return@mapNotNull null
                val gid = a.getOrNull(2).str().orEmpty()
                SealedProduct(game, id, a.getOrNull(1).str().orEmpty(), groups[gid].orEmpty(), a.getOrNull(3).dbl(),
                    language = a.getOrNull(4).str() ?: "EN", source = a.getOrNull(5).str() ?: "TCGplayer price",
                    aliases = a.getOrNull(6).arr().orEmpty().mapNotNull { it.str() })
            }
        }
        sealedLoaded[game] = list
        sealedStamp[game] = file.lastModified()
        return list
    }

    private val sealedStamp = ConcurrentHashMap<Game, Long>()

    /** Sealed products whose name or set contains every word of [query]. */
    suspend fun regionalSealed(game: Game, language: String): List<SealedProduct> {
        if (game != Game.POKEMON && game != Game.ONE_PIECE) return emptyList()
        val file = dailyFile("SEALED_REGIONAL_${game.name}.json") ?: throw java.io.IOException("Regional sealed catalogue unavailable")
        return withContext(Dispatchers.Default) {
            val root = http.json.parseToJsonElement(file.readText())
            root["items"].arr().orEmpty().mapNotNull { row ->
                val id = row["productId"].str()?.toLongOrNull() ?: return@mapNotNull null
                SealedProduct(game, id, row["name"].str().orEmpty(), row["groupName"].str().orEmpty(), null,
                    language = language, currency = "EUR", source = "", referencePrice = row["referencePrice"].dbl(),
                    referenceCurrency = "EUR", referenceSource = "Cardmarket aggregate guide (not language-specific)",
                    aliases = row["aliases"].arr().orEmpty().mapNotNull { it.str() }, requiresLanguageConfirmation = true)
            }
        }
    }

    suspend fun searchSealed(game: Game, query: String, language: String = "EN", limit: Int = 80): List<SealedProduct> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        val regional = try { regionalSealed(game, language) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) { if (language == "EN") emptyList() else throw failure }
        val catalogue = (if (language == "EN" || language == "JA") sealed(game).filter { it.language == language } else emptyList()) + regional
        return catalogue.filter { p -> words.all { w -> p.name.lowercase().contains(w) || p.groupName.lowercase().contains(w) || p.aliases.any { it.lowercase().contains(w) } } }
            .distinctBy { it.productId }.take(limit)
    }

    suspend fun sealedProduct(game: Game, productId: Long, language: String = "EN"): SealedProduct? =
        (if (productId > 0) sealed(game).filter { it.language == language } else regionalSealed(game, language)).firstOrNull { it.productId == productId }

    /** Every card of a set (TCGplayer group), in number order. */
    suspend fun setChecklist(game: Game, groupId: Int): List<ChecklistEntry> {
        val index = index(game) ?: return emptyList()
        return index.entries.filter { it.groupId == groupId }.sortedBy { it.number }
            .map { ChecklistEntry(it.productId.toString(), it.number, it.name, "https://tcgplayer-cdn.tcgplayer.com/product/${it.productId}_in_200x200.jpg") }
    }

    /** Raw index entries for [code] (used to find One Piece's TCGplayer products). */
    suspend fun entries(game: Game, code: String): List<Entry> = index(game)?.byNumber?.get(code).orEmpty()

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
            Variant(
                subType, subType, mapOf(PriceSource.TCGPLAYER to price), tcgplayerId = e.productId, tcgplayerPrinting = subType,
                details = listOf(PricePoint(PriceSource.TCGPLAYER, AppStrings.get(R.string.price_label_market), price)),
            )
        }.ifEmpty { listOf(Variant("Normal", AppStrings.get(R.string.data_variant_normal), emptyMap(), tcgplayerId = e.productId, tcgplayerPrinting = "Normal")) }
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
