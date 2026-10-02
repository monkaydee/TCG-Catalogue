package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.data.remote.CardBrief
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardIndexApi
import com.monkaydee.tcgcatalogue.data.remote.EbayApi
import com.monkaydee.tcgcatalogue.data.remote.FxApi
import com.monkaydee.tcgcatalogue.data.remote.OnePieceApi
import com.monkaydee.tcgcatalogue.data.remote.Price
import com.monkaydee.tcgcatalogue.data.remote.PriceChartingApi
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.data.remote.Pricing
import com.monkaydee.tcgcatalogue.data.remote.ScryfallApi
import com.monkaydee.tcgcatalogue.data.remote.TcgPlayerApi
import com.monkaydee.tcgcatalogue.data.remote.TcgDexApi
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.data.remote.toBrief
import com.monkaydee.tcgcatalogue.scan.CardTextParser
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.scan.ScanHit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class Backup(
    val version: Int = 1,
    val cards: List<OwnedCard>,
    val snapshots: List<com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot>,
)

class CardRepository(
    private val db: AppDatabase,
    private val tcgdex: TcgDexApi,
    private val onePiece: OnePieceApi,
    private val scryfall: ScryfallApi,
    private val cardIndex: CardIndexApi,
    private val priceCharting: PriceChartingApi,
    private val tcgplayer: TcgPlayerApi,
    private val ebay: EbayApi,
    private val fx: FxApi,
    val settings: SettingsStore,
) {
    val cards = db.cards().observeAll()
    val sets = db.sets().observeAll()
    val snapshots = db.snapshots().observeAll()

    fun observeSet(game: Game, setId: String) = db.cards().observeSet(game, setId)
    fun observeCard(id: Long) = db.cards().observe(id)

    // ---- Recognition ----

    /** Downloads (or refreshes daily) the card indexes of the enabled games, so the scanner can match their codes. */
    suspend fun prepareIndexes() {
        settings.current().enabledGames.filter { it.indexed }.forEach { runCatching { cardIndex.index(it) } }
    }

    /** Per indexed game, a check whether a code exists in its card index (only indexes already loaded). */
    fun indexMatchers(enabled: Set<Game>): Map<Game, (String) -> Boolean> =
        enabled.filter { it.indexed }.mapNotNull { g -> cardIndex.cached(g)?.let { idx -> g to { code: String -> idx.byNumber.containsKey(code) } } }
            .toMap()

    /** Resolves what the camera read into matching cards, best match first. */
    suspend fun resolve(hit: ScanHit): List<CardCandidate> = when (hit) {
        is ScanHit.OnePiece -> listOfNotNull(onePiece.card(hit.code))
        is ScanHit.Pokemon -> resolvePokemon(hit)
        is ScanHit.Magic -> resolveMagic(hit)
        is ScanHit.Indexed -> cardIndex.lookup(hit.game, hit.code)
    }

    private suspend fun resolveMagic(hit: ScanHit.Magic): List<CardCandidate> {
        if (hit.set != null && hit.number != null) {
            runCatching { scryfall.card(hit.set, hit.number) }.getOrNull()?.let { return listOf(it) }
        }
        val name = hit.nameGuess ?: return emptyList()
        return listOfNotNull(runCatching { scryfall.named(name, hit.set) }.getOrNull() ?: runCatching { scryfall.named(name) }.getOrNull())
    }

    private suspend fun resolvePokemon(hit: ScanHit.Pokemon): List<CardCandidate> = coroutineScope {
        val localId = if (hit.number.first().isDigit()) hit.number.trimStart('0').ifEmpty { "0" } else hit.number
        // Sets whose printed size matches the number after the slash.
        val sets = tcgdex.sets().filter { it.official == hit.total }
        val found = sets.map { set -> async { runCatching { tcgdex.cardInSet(set.id, localId) }.getOrNull() } }
            .mapNotNull { it.await() }
            .toMutableList()
        if (found.isEmpty() && hit.nameGuess != null) {
            // Fallback: search by name and keep cards whose number matches.
            val briefs = tcgdex.searchByName(hit.nameGuess)
                .filter { it.number.trimStart('0') == localId.trimStart('0') }
                .take(6)
            briefs.map { async { runCatching { tcgdex.card(it.cardId) }.getOrNull() } }.mapNotNullTo(found) { it.await() }
        }
        found.map { c ->
            val nameScore = hit.nameGuess?.let { CardTextParser.similarity(it, c.name) } ?: 0.5
            c.copy(score = nameScore)
        }.sortedByDescending { it.score }
    }

    /** True when the best match is clearly the right card, so it can be added without asking. */
    fun isConfident(candidates: List<CardCandidate>): Boolean =
        candidates.size == 1 || (candidates.size > 1 && candidates[0].score - candidates[1].score >= 0.25)

    // ---- Search ----

    /** Manual search. Pokémon: name; Magic: name or "SET 123"; One Piece and indexed games: code or name. */
    suspend fun search(game: Game, query: String): List<CardBrief> {
        val q = query.trim()
        return when (game) {
            Game.POKEMON -> tcgdex.searchByName(q)
            Game.ONE_PIECE -> CardTextParser.findOnePiece(listOf(com.monkaydee.tcgcatalogue.scan.OcrLine(q.uppercase())))
                ?.let { listOfNotNull(onePiece.card(it.code)?.toBrief()) }.orEmpty()
            Game.MAGIC -> {
                val m = Regex("""^([A-Za-z0-9]{3,5})\s+(\d+[a-z★]?)$""").find(q)
                val exact = m?.let { runCatching { scryfall.card(it.groupValues[1], it.groupValues[2]) }.getOrNull() }
                exact?.let { listOf(it.toBrief()) } ?: scryfall.search(q).map { it.toBrief() }
            }
            else -> cardIndex.search(game, q).map { it.toBrief() }
        }
    }

    suspend fun details(brief: CardBrief): CardCandidate? = brief.candidate ?: when (brief.game) {
        Game.POKEMON -> tcgdex.card(brief.cardId)
        Game.ONE_PIECE -> onePiece.card(brief.cardId)
        Game.MAGIC -> brief.cardId.split('/').takeIf { it.size == 2 }?.let { (set, n) -> scryfall.card(set, n) }
        else -> brief.cardId.toLongOrNull()?.let { cardIndex.byProduct(brief.game, it) }
    }

    // ---- Prices ----

    /** Raw-card market price of [variant], cross-checked between Cardmarket and TCGplayer. */
    fun rawPrice(card: CardCandidate, variant: Variant, s: AppSettings): Price? =
        Pricing.pick(variant, card.rarity, Pricing.sourceFor(card.game, s.pokemonSource), s.usdToEur)

    /**
     * Price of a graded copy: PriceCharting's sold prices for that company and grade, otherwise
     * the average of the last 5 eBay sales of the card in that grade. Null if neither has data.
     */
    suspend fun gradedPrice(card: CardCandidate, variant: Variant, grade: GradeInfo): Price? {
        val grader = grade.grader ?: return null
        val g = grade.grade ?: return null
        val table = priceCharting.table(card, variant)
        // PriceCharting's estimates for graders without their own column are a last resort.
        val pc = table?.let { PriceChartingApi.priceFor(it, grader, g, grade.qualifier) }
        if (pc != null && pc.second == null) return Price(pc.first, PriceSource.PRICECHARTING)
        val euro = settings.current().currency == "EUR"
        val sold = runCatching { ebay.gradedAverage(card, grader, g, grade.qualifier, euro) }.getOrNull()
        if (sold != null) {
            val source = if (sold.currency == "EUR") PriceSource.EBAY_DE else PriceSource.EBAY_US
            val label = GradeInfo(grader, g, grade.qualifier).label
            return Price(sold.average, source, "Average of the last ${sold.count} $label sales on ${sold.site}")
        }
        return pc?.let { (amount, note) -> Price(amount, PriceSource.PRICECHARTING, note) }
    }

    /** Raw price for a copy in [condition] (NM, LP, MP, HP, DMG), based on TCGplayer's sales per condition. */
    suspend fun conditionPrice(card: CardCandidate, variant: Variant, condition: String, s: AppSettings): Price? {
        val base = rawPrice(card, variant, s)
        if (condition == "NM") return base
        val table = runCatching { tcgplayerProduct(card, variant)?.let { tcgplayer.conditionPrices(it) } }.getOrNull()
        return Pricing.forCondition(base, condition, table?.let { TcgPlayerApi.forPrinting(it, variant.tcgplayerPrinting) })
    }

    /**
     * The TCGplayer product of a printing. One Piece's API doesn't link to TCGplayer, so its
     * printing is matched in the One Piece card index by number and closest price (the API's
     * prices come from TCGplayer, so the right product has the same price).
     */
    private suspend fun tcgplayerProduct(card: CardCandidate, variant: Variant): Long? {
        variant.tcgplayerId?.let { return it }
        if (card.game != Game.ONE_PIECE) return null
        val entries = cardIndex.entries(Game.ONE_PIECE, card.number)
        if (entries.size <= 1) return entries.firstOrNull()?.productId
        val target = variant.prices[PriceSource.TCGPLAYER] ?: return null
        fun distance(e: CardIndexApi.Entry) =
            e.prices.values.minOfOrNull { kotlin.math.abs(kotlin.math.ln(it.coerceAtLeast(0.01) / target)) } ?: Double.MAX_VALUE
        // Only trust a match within ±25 % of the price; otherwise the printing isn't in the index.
        return entries.minByOrNull(::distance)?.takeIf { distance(it) < kotlin.math.ln(1.25) }?.productId
    }

    /** Graded price if available, otherwise the price for the [condition]. */
    private suspend fun priceFor(card: CardCandidate, variant: Variant, grade: GradeInfo?, condition: String, s: AppSettings): Price? {
        if (grade?.grader == null || grade.grade == null) return conditionPrice(card, variant, condition, s)
        return gradedPrice(card, variant, grade)
            ?: rawPrice(card, variant, s)?.copy(note = "No graded sales found on PriceCharting or eBay; showing the raw price")
    }

    // ---- Collection ----

    suspend fun add(candidate: CardCandidate, variant: Variant, quantity: Int, condition: String, grade: GradeInfo? = null): Long {
        val s = settings.current()
        val graded = grade?.grader != null && grade.grade != null
        val price = runCatching { priceFor(candidate, variant, grade, condition, s) }.getOrNull()
        val id = db.cards().addOrIncrement(
            OwnedCard(
                game = candidate.game,
                cardId = candidate.cardId,
                variant = variant.key,
                variantLabel = variant.label,
                name = candidate.name,
                number = candidate.number,
                setId = candidate.setId,
                setName = candidate.setName,
                rarity = candidate.rarity,
                imageUrl = variant.imageUrl ?: candidate.imageUrl,
                quantity = quantity,
                condition = if (graded) grade!!.label else condition,
                price = price?.amount,
                priceCurrency = price?.currency ?: "USD",
                priceSource = price?.source?.label,
                priceUpdatedAt = System.currentTimeMillis(),
                grader = grade?.grader.takeIf { graded },
                grade = grade?.grade.takeIf { graded },
                gradeQualifier = grade?.qualifier.takeIf { graded },
                certNumber = grade?.cert?.takeIf { graded },
                priceNote = price?.note,
            ),
        )
        runCatching { ensureSet(candidate) }
        runCatching { snapshot() }
        return id
    }

    suspend fun update(card: OwnedCard) {
        if (card.quantity <= 0) db.cards().delete(card) else db.cards().update(card)
        runCatching { snapshot() }
    }

    /**
     * Changes the condition of a raw card and re-prices it for that condition. Merges into an
     * existing row when the same card is already owned in that condition.
     */
    suspend fun changeCondition(card: OwnedCard, condition: String) {
        if (card.graded || card.condition == condition) return
        val existing = db.cards().find(card.game, card.cardId, card.variant, condition)
        if (existing != null) {
            db.cards().update(existing.copy(quantity = existing.quantity + card.quantity))
            db.cards().delete(card)
            runCatching { snapshot() }
            return
        }
        val s = settings.current()
        val fresh = runCatching { fetch(card.game, card.cardId) }.getOrNull()
        val variant = fresh?.variants?.firstOrNull { it.key == card.variant }
        val price = if (fresh != null && variant != null) runCatching { conditionPrice(fresh, variant, condition, s) }.getOrNull() else null
        db.cards().update(
            if (price == null) {
                card.copy(condition = condition)
            } else {
                card.copy(
                    condition = condition,
                    price = price.amount,
                    priceCurrency = price.currency,
                    priceSource = price.source.label,
                    priceNote = price.note,
                    priceUpdatedAt = System.currentTimeMillis(),
                )
            },
        )
        runCatching { snapshot() }
    }

    suspend fun delete(card: OwnedCard) {
        db.cards().delete(card)
        runCatching { snapshot() }
    }

    private suspend fun ensureSet(c: CardCandidate) {
        if (db.sets().get(c.game, c.setId) != null) return
        val set = when (c.game) {
            Game.POKEMON -> tcgdex.setDetails(c.setId)?.let { (s, release) ->
                CardSet(Game.POKEMON, s.id, s.name, s.official, s.logoUrl, release)
            }
            Game.ONE_PIECE -> CardSet(Game.ONE_PIECE, c.setId, c.setName, onePiece.setSize(c.setId))
            Game.MAGIC -> scryfall.set(c.setId)?.let { (name, size) -> CardSet(Game.MAGIC, c.setId, name, size) }
            else -> CardSet(c.game, c.setId, c.setName, c.setTotal)
        } ?: return
        db.sets().upsert(set)
    }

    /** Fetches the current data of a card in the collection. */
    private suspend fun fetch(game: Game, cardId: String): CardCandidate? = when (game) {
        Game.POKEMON -> tcgdex.card(cardId)
        Game.ONE_PIECE -> onePiece.card(cardId)
        Game.MAGIC -> cardId.split('/').takeIf { it.size == 2 }?.let { (set, n) -> scryfall.card(set, n) }
        else -> cardId.toLongOrNull()?.let { cardIndex.byProduct(game, it) }
    }

    /**
     * Re-fetches prices for every card in the collection, refreshes the exchange
     * rate and records today's portfolio value. Returns the number of cards updated.
     */
    suspend fun refreshPrices(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int = coroutineScope {
        fx.runCatching { usdToEur() }.getOrNull()?.let { settings.setUsdToEur(it) }
        val s = settings.current()
        val owned = db.cards().getAll()
        val groups = owned.groupBy { it.game to it.cardId }
        val gate = Semaphore(4)
        val done = AtomicInteger()
        val updated = AtomicInteger()
        groups.map { (key, rows) ->
            async {
                gate.withPermit {
                    val (game, cardId) = key
                    val fresh = runCatching { fetch(game, cardId) }.getOrNull()
                    if (fresh != null) {
                        runCatching { ensureSet(fresh) }
                        for (row in rows) {
                            val variant = fresh.variants.firstOrNull { it.key == row.variant } ?: continue
                            val grade = if (row.graded) GradeInfo(row.grader, row.grade, row.gradeQualifier, row.certNumber) else null
                            val price = runCatching { priceFor(fresh, variant, grade, row.condition, s) }.getOrNull() ?: continue
                            db.cards().update(
                                row.copy(
                                    price = price.amount,
                                    priceCurrency = price.currency,
                                    priceSource = price.source.label,
                                    priceUpdatedAt = System.currentTimeMillis(),
                                    priceNote = price.note,
                                    rarity = fresh.rarity ?: row.rarity,
                                ),
                            )
                            updated.incrementAndGet()
                        }
                    }
                    onProgress(done.incrementAndGet(), groups.size)
                }
            }
        }.forEach { it.await() }
        settings.setLastRefresh(System.currentTimeMillis())
        snapshot()
        updated.get()
    }

    /** Stores today's total value (overwriting an earlier snapshot from the same day). */
    suspend fun snapshot() {
        val rate = settings.current().usdToEur
        val owned = db.cards().getAll()
        db.snapshots().upsert(
            PortfolioSnapshot(
                day = LocalDate.now().toEpochDay(),
                valueUsd = owned.sumOf { Money.value(it, "USD", rate) },
                valueEur = owned.sumOf { Money.value(it, "EUR", rate) },
                cardCount = owned.sumOf { it.quantity },
            ),
        )
    }

    suspend fun exportBackup(): Backup = Backup(cards = db.cards().getAll(), snapshots = db.snapshots().getAll())

    suspend fun importBackup(backup: Backup) {
        db.cards().deleteAll()
        db.cards().insertAll(backup.cards)
        db.snapshots().insertAll(backup.snapshots)
        backup.cards.distinctBy { it.game to it.setId }.forEach { c ->
            runCatching {
                if (db.sets().get(c.game, c.setId) == null) {
                    fetch(c.game, c.cardId)?.let { ensureSet(it) }
                }
            }
        }
    }
}
