package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.data.remote.CardBrief
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.CardIndexApi
import com.monkaydee.tcgcatalogue.data.remote.CardmarketApi
import com.monkaydee.tcgcatalogue.data.remote.CardmarketPokemon
import com.monkaydee.tcgcatalogue.data.remote.FxApi
import com.monkaydee.tcgcatalogue.data.remote.OnePieceApi
import com.monkaydee.tcgcatalogue.data.remote.Price
import com.monkaydee.tcgcatalogue.data.remote.PricePoint
import com.monkaydee.tcgcatalogue.data.remote.PriceSource
import com.monkaydee.tcgcatalogue.data.remote.Pricing
import com.monkaydee.tcgcatalogue.data.remote.ScryfallApi
import com.monkaydee.tcgcatalogue.data.remote.TcgPlayerApi
import com.monkaydee.tcgcatalogue.data.remote.attempt
import com.monkaydee.tcgcatalogue.data.remote.TcgDexApi
import com.monkaydee.tcgcatalogue.data.remote.Variant
import com.monkaydee.tcgcatalogue.data.remote.toBrief
import com.monkaydee.tcgcatalogue.scan.CardTextParser
import com.monkaydee.tcgcatalogue.scan.GradeInfo
import com.monkaydee.tcgcatalogue.scan.ScanHit
import com.monkaydee.tcgcatalogue.ui.components.AddRequest
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
    private val tcgplayer: TcgPlayerApi,
    private val cardmarket: CardmarketApi,
    private val cardmarketPokemon: CardmarketPokemon,
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
        is ScanHit.OnePiece -> verified(Game.ONE_PIECE, hit.code, hit.texts) { code -> listOfNotNull(onePiece.card(code)) }
        is ScanHit.Pokemon -> resolvePokemon(hit)
        is ScanHit.Magic -> resolveMagic(hit)
        is ScanHit.Indexed -> verified(hit.game, hit.code, hit.texts) { code -> cardIndex.lookup(hit.game, code) }
    }

    /**
     * Checks that the card the code points to has the name printed on the scanned card. If not,
     * the code was probably misread (6 for 8, 1 for 7 ...): look-alike codes whose card name is on
     * the scan are offered first, the literal match last with a low score so it isn't added
     * automatically.
     */
    private suspend fun verified(game: Game, code: String, texts: List<String>, load: suspend (String) -> List<CardCandidate>): List<CardCandidate> {
        val direct = runCatching { load(code) }.getOrDefault(emptyList())
        if (texts.isEmpty()) return direct
        val (matching, other) = direct.partition { CardTextParser.nameOnCard(it.name, texts) }
        if (matching.isNotEmpty()) return matching.map { it.copy(score = 1.0) } + other.map { it.copy(score = 0.5) }
        val index = runCatching { cardIndex.index(game) }.getOrNull()
        val alternatives = CardTextParser.misreadVariants(code)
            .filter { alt -> index?.byNumber?.get(alt).orEmpty().any { CardTextParser.nameOnCard(it.name, texts) } }
            .take(3)
            .flatMap { alt -> runCatching { load(alt) }.getOrDefault(emptyList()) }
            .filter { CardTextParser.nameOnCard(it.name, texts) }
            .map { it.copy(score = 0.9) }
        val warning = "The name on the scanned card doesn't match this card — the number may have been misread. Check it, or cancel and type it in."
        return alternatives + direct.map { it.copy(score = 0.3, warning = warning) }
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
        // Sets that print a code ("PAL") next to the number: a matching code decides between sets of the same size.
        val codes = if (hit.setCode != null && found.size > 1) {
            found.map { c -> async { attempt { tcgdex.abbreviation(c.setId) }.getOrDefault("") } }.map { it.await() }
        } else {
            found.map { "" }
        }
        found.mapIndexed { i, c ->
            val nameScore = hit.nameGuess?.let { CardTextParser.similarity(it, c.name) } ?: 0.5
            val setScore = when {
                hit.setCode == null || codes[i].isEmpty() -> 0.0
                codes[i] == hit.setCode -> 0.5
                CardTextParser.similarity(codes[i], hit.setCode) >= 0.6 -> 0.2
                else -> -0.3
            }
            pokemonFixed(c).copy(score = nameScore + setScore, preferredVariant = printingFor(c, hit.firstEdition))
        }.sortedByDescending { it.score }
    }

    /**
     * The printing a scan shows: 1st Edition when the stamp was read, otherwise never 1st Edition
     * (it is the rare one). The other printings look the same on a photo, so the first stays.
     */
    private fun printingFor(c: CardCandidate, firstEdition: Boolean): String? {
        val first = c.variants.firstOrNull { it.key == "firstEdition" } ?: return null
        return if (firstEdition) first.key else c.variants.firstOrNull { it.key != first.key }?.key
    }

    /** True when the best match is clearly the right card, so it can be added without asking. */
    fun isConfident(candidates: List<CardCandidate>): Boolean {
        val top = candidates.firstOrNull() ?: return false
        // Never add a card automatically when the scan's name contradicts it.
        if (top.warning != null) return false
        // Alt arts share the number: only add on its own when the picture told which one it is.
        if (com.monkaydee.tcgcatalogue.scan.VisualMatcher.needsChoice(top)) return false
        return candidates.size == 1 || candidates[0].score - candidates[1].score >= 0.25
    }

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

    /** A Pokémon card with the Cardmarket prices of the right print (see [CardmarketPokemon]). */
    private suspend fun pokemonFixed(c: CardCandidate): CardCandidate = attempt { cardmarketPokemon.fix(c) }.getOrDefault(c)

    suspend fun details(brief: CardBrief): CardCandidate? = brief.candidate ?: when (brief.game) {
        Game.POKEMON -> tcgdex.card(brief.cardId)?.let { pokemonFixed(it) }
        Game.ONE_PIECE -> onePiece.card(brief.cardId)
        Game.MAGIC -> brief.cardId.split('/').takeIf { it.size == 2 }?.let { (set, n) -> scryfall.card(set, n) }
        else -> brief.cardId.toLongOrNull()?.let { cardIndex.byProduct(brief.game, it) }
    }

    // ---- Prices ----

    /**
     * Raw-card market price of [variant], cross-checked between Cardmarket and TCGplayer.
     * A chosen Cardmarket [listing] (One Piece) wins when Cardmarket is the preferred market.
     */
    fun rawPrice(card: CardCandidate, variant: Variant, s: AppSettings, listing: CardmarketApi.Listing? = null): Price? {
        if (s.pokemonSource == PriceSource.CARDMARKET) listing?.price?.let { return Price(it, PriceSource.CARDMARKET) }
        return Pricing.pick(variant, card.rarity, Pricing.sourceFor(card.game, s.pokemonSource), s.usdToEur)
    }

    /** Cardmarket's listings (every print) of a One Piece card, empty for other games. */
    suspend fun cardmarketListings(card: CardCandidate): List<CardmarketApi.Listing> =
        if (card.game == Game.ONE_PIECE) runCatching { cardmarket.listings(card.number) }.getOrDefault(emptyList()) else emptyList()

    /**
     * Best guess which Cardmarket listing a printing is: English prints first, the one whose price
     * is closest to the printing's TCGplayer price. The user can pick another one.
     */
    fun defaultListing(listings: List<CardmarketApi.Listing>, variant: Variant, s: AppSettings): CardmarketApi.Listing? {
        if (s.pokemonSource != PriceSource.CARDMARKET) return null
        val priced = listings.filter { it.price != null }
        val pool = priced.filterNot { it.nonEnglish }.ifEmpty { priced }
        val target = variant.prices[PriceSource.TCGPLAYER]?.times(s.usdToEur) ?: return pool.firstOrNull()
        return pool.minByOrNull { kotlin.math.abs(kotlin.math.ln(it.price!!.coerceAtLeast(0.01) / target.coerceAtLeast(0.01))) }
    }

    /** A graded price, or why there is none. */
    data class GradedResult(val price: Price?, val problem: String?)

    suspend fun gradedPrice(card: CardCandidate, variant: Variant, grade: GradeInfo): Price? = gradedLookup(card, variant, grade).price

    /**
     * Price of a graded copy. No free source publishes graded sales the app can read (PriceCharting
     * and eBay block apps), so graded copies carry the user's own value, helped by the price links.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun gradedLookup(card: CardCandidate, variant: Variant, grade: GradeInfo): GradedResult {
        grade.grader ?: return GradedResult(null, "choose the grading company")
        grade.grade ?: return GradedResult(null, "choose the grade")
        return GradedResult(null, "no graded price source — set your own value, or check the price links on the card page")
    }

    /** Raw price for a copy in [condition] (NM, LP, MP, HP, DMG), based on TCGplayer's sales per condition. */
    suspend fun conditionPrice(card: CardCandidate, variant: Variant, condition: String, s: AppSettings, listing: CardmarketApi.Listing? = null): Price? {
        val base = rawPrice(card, variant, s, listing)
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
    private suspend fun priceFor(
        card: CardCandidate,
        variant: Variant,
        grade: GradeInfo?,
        condition: String,
        s: AppSettings,
        listing: CardmarketApi.Listing? = null,
    ): Price? {
        if (grade?.grader == null || grade.grade == null) return conditionPrice(card, variant, condition, s, listing)
        val result = gradedLookup(card, variant, grade)
        return result.price ?: rawPrice(card, variant, s, listing)?.copy(note = "Showing the raw price — ${result.problem}")
    }

    private suspend fun listingOf(card: OwnedCard): CardmarketApi.Listing? =
        card.marketProductId?.let { runCatching { cardmarket.byProduct(it) }.getOrNull() }

    // ---- Collection ----

    suspend fun add(
        candidate: CardCandidate,
        variant: Variant,
        quantity: Int,
        condition: String,
        grade: GradeInfo? = null,
        listing: CardmarketApi.Listing? = null,
        purchasePrice: Double? = null,
        manualValue: Double? = null,
    ): Long {
        val s = settings.current()
        val graded = grade?.grader != null && grade.grade != null
        val chosen = listing ?: defaultListing(cardmarketListings(candidate), variant, s)
        val price = runCatching { priceFor(candidate, variant, grade, condition, s, chosen) }.getOrNull()
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
                marketProductId = chosen?.productId,
                marketLabel = chosen?.label,
                purchasePrice = purchasePrice?.let { Money.convert(it, s.currency, price?.currency ?: s.currency, s.usdToEur) },
                manualPrice = manualValue,
                manualCurrency = manualValue?.let { s.currency },
            ),
        )
        runCatching { ensureSet(candidate) }
        runCatching { snapshot() }
        return id
    }

    suspend fun add(r: AddRequest): Long = add(r.card, r.variant, r.quantity, r.condition, r.grade, r.listing, r.purchasePrice, r.manualValue)

    /** Current data of a card in the collection, for editing it (null when offline). */
    suspend fun candidateFor(card: OwnedCard): CardCandidate? = runCatching { fetch(card.game, card.cardId) }.getOrNull()

    /**
     * Saves an edited card in place: printing, condition or grade, Cardmarket listing, quantity,
     * purchase price, or even a different card. If the result is the same as another row of the
     * collection, the two are merged.
     */
    suspend fun saveEdit(original: OwnedCard, r: AddRequest) {
        val s = settings.current()
        val graded = r.grade?.grader != null && r.grade.grade != null
        val condition = if (graded) r.grade!!.label else r.condition
        val price = runCatching { priceFor(r.card, r.variant, r.grade, r.condition, s, r.listing) }.getOrNull()
        val edited = original.copy(
            game = r.card.game,
            cardId = r.card.cardId,
            variant = r.variant.key,
            variantLabel = r.variant.label,
            name = r.card.name,
            number = r.card.number,
            setId = r.card.setId,
            setName = r.card.setName,
            rarity = r.card.rarity,
            imageUrl = r.variant.imageUrl ?: r.card.imageUrl,
            quantity = r.quantity,
            condition = condition,
            price = price?.amount ?: original.price,
            priceCurrency = price?.currency ?: original.priceCurrency,
            priceSource = price?.source?.label ?: original.priceSource,
            priceNote = price?.note,
            priceUpdatedAt = System.currentTimeMillis(),
            grader = r.grade?.grader.takeIf { graded },
            grade = r.grade?.grade.takeIf { graded },
            gradeQualifier = r.grade?.qualifier.takeIf { graded },
            certNumber = r.grade?.cert?.takeIf { graded },
            marketProductId = r.listing?.productId,
            marketLabel = r.listing?.label,
            purchasePrice = r.purchasePrice?.let { Money.convert(it, s.currency, price?.currency ?: original.priceCurrency, s.usdToEur) },
            manualPrice = r.manualValue,
            manualCurrency = r.manualValue?.let { s.currency },
        )
        val clash = db.cards().find(edited.game, edited.cardId, edited.variant, edited.condition)?.takeIf { it.id != original.id }
        if (clash != null) {
            db.cards().update(clash.copy(quantity = clash.quantity + edited.quantity))
            db.cards().delete(original)
        } else {
            db.cards().update(edited)
        }
        runCatching { ensureSet(r.card) }
        runCatching { snapshot() }
    }

    suspend fun card(id: Long): OwnedCard? = db.cards().get(id)

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
        val price = if (fresh != null && variant != null) runCatching { conditionPrice(fresh, variant, condition, s, listingOf(card)) }.getOrNull() else null
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

    /** Prices a One Piece copy from another Cardmarket listing (the exact print the user owns). */
    suspend fun changeListing(card: OwnedCard, listing: CardmarketApi.Listing) {
        val s = settings.current()
        val fresh = runCatching { fetch(card.game, card.cardId) }.getOrNull()
        val variant = fresh?.variants?.firstOrNull { it.key == card.variant }
        val grade = if (card.graded) GradeInfo(card.grader, card.grade, card.gradeQualifier, card.certNumber) else null
        val price = if (fresh != null && variant != null) runCatching { priceFor(fresh, variant, grade, card.condition, s, listing) }.getOrNull() else null
        db.cards().update(
            card.copy(
                marketProductId = listing.productId,
                marketLabel = listing.label,
                price = price?.amount ?: card.price,
                priceCurrency = price?.currency ?: card.priceCurrency,
                priceSource = price?.source?.label ?: card.priceSource,
                priceNote = price?.note,
                priceUpdatedAt = System.currentTimeMillis(),
            ),
        )
        runCatching { snapshot() }
    }

    /** A block of the price overview, e.g. "TCGplayer by condition", or why it is empty. */
    data class PriceGroup(val title: String, val lines: List<PricePoint>, val problem: String? = null)

    /**
     * Every price the app can find for a card in the collection, from all sources, for the card
     * page's price overview. Each source fails on its own without hiding the others.
     */
    suspend fun priceOverview(card: OwnedCard): List<PriceGroup> = coroutineScope {
        val fresh = attempt { fetch(card.game, card.cardId) }.getOrNull() ?: return@coroutineScope listOf(
            PriceGroup("Prices", emptyList(), "couldn't load the card data — check your connection"),
        )
        val variant = fresh.variants.firstOrNull { it.key == card.variant } ?: fresh.variants.first()
        val listing = async { attempt { listingOf(card) }.getOrNull() }
        val conditions = async {
            attempt { tcgplayerProduct(fresh, variant)?.let { tcgplayer.conditionPrices(it) } }
                .getOrNull()?.let { TcgPlayerApi.forPrinting(it, variant.tcgplayerPrinting) }
        }

        val groups = mutableListOf<PriceGroup>()
        val cm = variant.details.filter { it.source == PriceSource.CARDMARKET }
        val l = listing.await()
        val cmLines = if (l != null) {
            listOfNotNull(
                l.trend?.let { PricePoint(PriceSource.CARDMARKET, "Trend", it) },
                l.low?.let { PricePoint(PriceSource.CARDMARKET, "Lowest offer", it) },
                l.avg7?.let { PricePoint(PriceSource.CARDMARKET, "7-day average", it) },
                l.avg30?.let { PricePoint(PriceSource.CARDMARKET, "30-day average", it) },
            )
        } else {
            cm
        }
        groups += PriceGroup(
            "Cardmarket" + (l?.let { " · ${it.label}" } ?: ""),
            cmLines,
            if (cmLines.isEmpty()) "no Cardmarket prices for this card" else null,
        )
        val tcg = variant.details.filter { it.source == PriceSource.TCGPLAYER }
        groups += PriceGroup("TCGplayer", tcg, if (tcg.isEmpty()) "no TCGplayer prices for this card" else null)
        val byCondition = conditions.await()
        groups += PriceGroup(
            "TCGplayer by condition (sales)",
            TcgPlayerApi.CONDITION_NAMES.mapNotNull { (code, name) -> byCondition?.get(name)?.let { PricePoint(PriceSource.TCGPLAYER, code, it) } },
            if (byCondition.isNullOrEmpty()) "no sales by condition" else null,
        )
        groups
    }

    /** Cardmarket listings for a card in the collection (One Piece only). */
    suspend fun cardmarketListings(card: OwnedCard): List<CardmarketApi.Listing> =
        if (card.game == Game.ONE_PIECE) runCatching { cardmarket.listings(card.number) }.getOrDefault(emptyList()) else emptyList()

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
        Game.POKEMON -> tcgdex.card(cardId)?.let { pokemonFixed(it) }
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
                            val price = runCatching { priceFor(fresh, variant, grade, row.condition, s, listingOf(row)) }.getOrNull() ?: continue
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
