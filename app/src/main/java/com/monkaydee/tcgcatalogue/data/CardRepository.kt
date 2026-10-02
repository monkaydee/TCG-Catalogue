package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.data.remote.CardBrief
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.FxApi
import com.monkaydee.tcgcatalogue.data.remote.OnePieceApi
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.data.remote.TcgDexApi
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.scan.CardTextParser
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
    private val fx: FxApi,
    val settings: SettingsStore,
) {
    val cards = db.cards().observeAll()
    val sets = db.sets().observeAll()
    val snapshots = db.snapshots().observeAll()

    fun observeSet(game: Game, setId: String) = db.cards().observeSet(game, setId)
    fun observeCard(id: Long) = db.cards().observe(id)

    /** Resolves what the camera read into matching cards, best match first. */
    suspend fun resolve(hit: ScanHit): List<CardCandidate> = when (hit) {
        is ScanHit.OnePiece -> listOfNotNull(onePiece.card(hit.code))
        is ScanHit.Pokemon -> resolvePokemon(hit)
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

    suspend fun searchPokemon(name: String): List<CardBrief> = tcgdex.searchByName(name)

    suspend fun details(brief: CardBrief): CardCandidate? = when (brief.game) {
        Game.POKEMON -> tcgdex.card(brief.cardId)
        Game.ONE_PIECE -> onePiece.card(brief.cardId)
    }

    fun sourceFor(game: Game, s: AppSettings) = if (game == Game.POKEMON) s.pokemonSource else PriceSource.TCGPLAYER

    suspend fun add(candidate: CardCandidate, variant: Variant, quantity: Int, condition: String): Long {
        val s = settings.current()
        val price = variant.price(sourceFor(candidate.game, s))
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
                condition = condition,
                price = price?.amount,
                priceCurrency = price?.currency ?: "USD",
                priceSource = price?.source?.label,
                priceUpdatedAt = System.currentTimeMillis(),
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
        } ?: return
        db.sets().upsert(set)
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
                    val fresh = runCatching {
                        if (game == Game.POKEMON) tcgdex.card(cardId) else onePiece.card(cardId)
                    }.getOrNull()
                    if (fresh != null) {
                        runCatching { ensureSet(fresh) }
                        for (row in rows) {
                            val variant = fresh.variants.firstOrNull { it.key == row.variant } ?: continue
                            val price = variant.price(sourceFor(game, s)) ?: continue
                            db.cards().update(
                                row.copy(
                                    price = price.amount,
                                    priceCurrency = price.currency,
                                    priceSource = price.source.label,
                                    priceUpdatedAt = System.currentTimeMillis(),
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
                    when (c.game) {
                        Game.POKEMON -> tcgdex.setDetails(c.setId)?.let { (s, r) -> db.sets().upsert(CardSet(c.game, s.id, s.name, s.official, s.logoUrl, r)) }
                        Game.ONE_PIECE -> db.sets().upsert(CardSet(c.game, c.setId, c.setName, onePiece.setSize(c.setId)))
                    }
                }
            }
        }
    }
}
