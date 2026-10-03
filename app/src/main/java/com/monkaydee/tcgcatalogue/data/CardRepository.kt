package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.db.CardSet
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot
import com.monkaydee.tcgcatalogue.data.db.PriceHistory
import com.monkaydee.tcgcatalogue.data.db.SealedItem
import com.monkaydee.tcgcatalogue.data.db.SoldCard
import com.monkaydee.tcgcatalogue.data.db.WishCard
import com.monkaydee.tcgcatalogue.data.remote.ChecklistEntry
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
import com.monkaydee.tcgcatalogue.data.remote.PriceTexts
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
import com.monkaydee.tcgcatalogue.ui.AppStrings
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
    val version: Int = 2,
    val cards: List<OwnedCard>,
    val snapshots: List<com.monkaydee.tcgcatalogue.data.db.PortfolioSnapshot>,
    val history: List<PriceHistory> = emptyList(),
    val wishlist: List<WishCard> = emptyList(),
    val sold: List<SoldCard> = emptyList(),
    val sealed: List<SealedItem> = emptyList(),
    /** When and on which install the backup was written (for sync between phones). */
    val savedAt: Long = 0,
    val device: String = "",
)

/** A price alert that went off during a refresh, for a notification. */
data class PriceAlert(val name: String, val detail: String, val cardRowId: Long? = null)

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
    /** Japanese Pokémon cards (ids prefixed "ja:"). */
    private val tcgdexJa: TcgDexApi = tcgdex.forLanguage("ja"),
) {
    /** The TCGdex client for a Pokémon card or set id (Japanese ids start with "ja:"). */
    private fun pokemonApi(id: String) = if (id.startsWith(tcgdexJa.idPrefix)) tcgdexJa else tcgdex
    val cards = db.cards().observeAll()
    val sets = db.sets().observeAll()
    val snapshots = db.snapshots().observeAll()
    val wishlist = db.wishlist().observeAll()
    val sold = db.sold().observeAll()
    val sealed = db.sealed().observeAll()

    /** Alerts that went off in the last price refresh (the background job shows them as notifications). */
    @Volatile
    var lastAlerts: List<PriceAlert> = emptyList()
        private set

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
        val warning = AppStrings.get(R.string.data_scan_name_mismatch)
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
        // A Japanese card: its printed set code names the set directly.
        hit.jaSet?.let { code ->
            val set = runCatching { tcgdexJa.sets() }.getOrDefault(emptyList())
                .firstOrNull { it.id.removePrefix(tcgdexJa.idPrefix).equals(code, ignoreCase = true) }
            val card = set?.let { s ->
                runCatching { tcgdexJa.cardInSet(s.id, hit.number) }.getOrNull()
                    ?: runCatching { tcgdexJa.cardInSet(s.id, localId) }.getOrNull()
            }
            if (card != null) return@coroutineScope listOf(card.copy(score = 1.0, preferredVariant = printingFor(card, hit.firstEdition)))
        }
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
            // Japanese names (kana/kanji) search the Japanese cards.
            Game.POKEMON -> if (q.any { Character.UnicodeScript.of(it.code) in JAPANESE }) tcgdexJa.searchByName(q) else tcgdex.searchByName(q)
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
    private suspend fun pokemonFixed(c: CardCandidate): CardCandidate =
        // The look-alike correction uses the English Cardmarket catalogue.
        if (c.cardId.startsWith(tcgdexJa.idPrefix)) c else attempt { cardmarketPokemon.fix(c) }.getOrDefault(c)

    suspend fun details(brief: CardBrief): CardCandidate? = brief.candidate ?: when (brief.game) {
        Game.POKEMON -> pokemonApi(brief.cardId).card(brief.cardId)?.let { pokemonFixed(it) }
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
        return Pricing.pick(variant, card.rarity, Pricing.sourceFor(card.game, s.pokemonSource), s.usdToEur, PriceTexts.App)
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
        grade.grader ?: return GradedResult(null, AppStrings.get(R.string.data_graded_choose_grader))
        grade.grade ?: return GradedResult(null, AppStrings.get(R.string.data_graded_choose_grade))
        return GradedResult(null, AppStrings.get(R.string.data_graded_no_source))
    }

    /** Raw price for a copy in [condition] (NM, LP, MP, HP, DMG), based on TCGplayer's sales per condition. */
    suspend fun conditionPrice(card: CardCandidate, variant: Variant, condition: String, s: AppSettings, listing: CardmarketApi.Listing? = null): Price? {
        val base = rawPrice(card, variant, s, listing)
        if (condition == "NM") return base
        val table = runCatching { tcgplayerProduct(card, variant)?.let { tcgplayer.conditionPrices(it) } }.getOrNull()
        return Pricing.forCondition(base, condition, table?.let { TcgPlayerApi.forPrinting(it, variant.tcgplayerPrinting) }, PriceTexts.App)
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
        // Grader and grade are set here, so a missing graded price means there is no graded price source.
        return result.price ?: rawPrice(card, variant, s, listing)?.copy(note = AppStrings.get(R.string.price_note_raw_no_graded_source))
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
            PriceGroup(AppStrings.get(R.string.data_overview_prices), emptyList(), AppStrings.get(R.string.data_overview_no_card_data)),
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
                l.trend?.let { PricePoint(PriceSource.CARDMARKET, AppStrings.get(R.string.price_label_trend), it) },
                l.low?.let { PricePoint(PriceSource.CARDMARKET, AppStrings.get(R.string.price_label_lowest_offer), it) },
                l.avg7?.let { PricePoint(PriceSource.CARDMARKET, AppStrings.get(R.string.price_label_avg7), it) },
                l.avg30?.let { PricePoint(PriceSource.CARDMARKET, AppStrings.get(R.string.price_label_avg30), it) },
            )
        } else {
            cm
        }
        groups += PriceGroup(
            "Cardmarket" + (l?.let { " · ${it.label}" } ?: ""),
            cmLines,
            if (cmLines.isEmpty()) AppStrings.get(R.string.data_overview_no_cardmarket) else null,
        )
        val tcg = variant.details.filter { it.source == PriceSource.TCGPLAYER }
        groups += PriceGroup("TCGplayer", tcg, if (tcg.isEmpty()) AppStrings.get(R.string.data_overview_no_tcgplayer) else null)
        val byCondition = conditions.await()
        groups += PriceGroup(
            AppStrings.get(R.string.data_overview_by_condition),
            TcgPlayerApi.CONDITION_NAMES.mapNotNull { (code, name) -> byCondition?.get(name)?.let { PricePoint(PriceSource.TCGPLAYER, code, it) } },
            if (byCondition.isNullOrEmpty()) AppStrings.get(R.string.data_overview_no_condition_sales) else null,
        )
        groups
    }

    /** Cardmarket listings for a card in the collection (One Piece only). */
    suspend fun cardmarketListings(card: OwnedCard): List<CardmarketApi.Listing> =
        if (card.game == Game.ONE_PIECE) runCatching { cardmarket.listings(card.number) }.getOrDefault(emptyList()) else emptyList()

    suspend fun delete(card: OwnedCard) {
        db.cards().delete(card)
        db.history().deleteFor(card.id)
        runCatching { snapshot() }
    }

    private suspend fun ensureSet(c: CardCandidate) {
        if (db.sets().get(c.game, c.setId) != null) return
        val set = when (c.game) {
            Game.POKEMON -> pokemonApi(c.setId).setDetails(c.setId)?.let { (s, release) ->
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
        Game.POKEMON -> pokemonApi(cardId).card(cardId)?.let { pokemonFixed(it) }
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
        val alerts = java.util.concurrent.ConcurrentLinkedQueue<PriceAlert>()
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
                            val next = row.copy(
                                price = price.amount,
                                priceCurrency = price.currency,
                                priceSource = price.source.label,
                                priceUpdatedAt = System.currentTimeMillis(),
                                priceNote = price.note,
                                rarity = fresh.rarity ?: row.rarity,
                            )
                            db.cards().update(next)
                            alertFor(row, next, s)?.let { alerts += it }
                            updated.incrementAndGet()
                        }
                    }
                    onProgress(done.incrementAndGet(), groups.size)
                }
            }
        }.forEach { it.await() }
        runCatching { recordHistory() }
        runCatching { refreshWishlist(s) }.getOrNull()?.let { alerts += it }
        runCatching { refreshSealed() }
        settings.setLastRefresh(System.currentTimeMillis())
        snapshot()
        lastAlerts = alerts.toList()
        updated.get()
    }

    /** Stores today's total value (overwriting an earlier snapshot from the same day). */
    suspend fun snapshot() {
        val rate = settings.current().usdToEur
        val owned = db.cards().getAll()
        val sealed = db.sealed().getAll()
        db.snapshots().upsert(
            PortfolioSnapshot(
                day = LocalDate.now().toEpochDay(),
                valueUsd = owned.sumOf { Money.value(it, "USD", rate) } + sealed.sumOf { Money.sealedValue(it, "USD", rate) },
                valueEur = owned.sumOf { Money.value(it, "EUR", rate) } + sealed.sumOf { Money.sealedValue(it, "EUR", rate) },
                cardCount = owned.sumOf { it.quantity },
            ),
        )
    }

    suspend fun exportBackup(device: String = ""): Backup = Backup(
        cards = db.cards().getAll(),
        snapshots = db.snapshots().getAll(),
        history = db.history().getAll(),
        wishlist = db.wishlist().getAll(),
        sold = db.sold().getAll(),
        sealed = db.sealed().getAll(),
        savedAt = System.currentTimeMillis(),
        device = device,
    )

    /** Replaces the whole collection with [backup]. */
    suspend fun importBackup(backup: Backup) {
        db.cards().deleteAll()
        db.cards().insertAll(backup.cards)
        db.snapshots().insertAll(backup.snapshots)
        db.history().deleteAll()
        db.history().upsertAll(backup.history)
        db.wishlist().deleteAll()
        db.wishlist().insertAll(backup.wishlist)
        db.sold().deleteAll()
        db.sold().insertAll(backup.sold)
        db.sealed().deleteAll()
        db.sealed().insertAll(backup.sealed)
        backup.cards.distinctBy { it.game to it.setId }.forEach { c ->
            runCatching {
                if (db.sets().get(c.game, c.setId) == null) {
                    fetch(c.game, c.cardId)?.let { ensureSet(it) }
                }
            }
        }
    }

    /**
     * Adds what [backup] has and this phone doesn't: cards are matched by card, printing and
     * condition (the higher quantity wins), wishlist entries by card and printing, sold cards and
     * sealed products by their details. Nothing on this phone is removed.
     */
    suspend fun mergeBackup(backup: Backup) {
        val mine = db.cards().getAll()
        for (c in backup.cards) {
            val match = mine.firstOrNull { it.game == c.game && it.cardId == c.cardId && it.variant == c.variant && it.condition == c.condition }
            if (match == null) db.cards().insert(c.copy(id = 0)) else if (c.quantity > match.quantity) db.cards().update(match.copy(quantity = c.quantity))
        }
        db.snapshots().insertAll(backup.snapshots.filter { s -> db.snapshots().getAll().none { it.day == s.day } })
        for (w in backup.wishlist) if (db.wishlist().find(w.game, w.cardId, w.variant) == null) db.wishlist().upsert(w.copy(id = 0))
        val sold = db.sold().getAll()
        for (x in backup.sold) if (sold.none { it.cardId == x.cardId && it.soldAt == x.soldAt && it.variant == x.variant }) db.sold().insert(x.copy(id = 0))
        for (x in backup.sealed) {
            val match = db.sealed().find(x.game, x.productId)
            if (match == null) db.sealed().upsert(x.copy(id = 0)) else if (x.quantity > match.quantity) db.sealed().update(match.copy(quantity = x.quantity))
        }
        runCatching { snapshot() }
    }

    // ---- Price history and alerts ----

    fun priceHistory(cardRowId: Long) = db.history().observe(cardRowId)

    /** Stores today's price per copy of every card, for the price history charts. */
    private suspend fun recordHistory() {
        val today = LocalDate.now().toEpochDay()
        db.history().upsertAll(
            db.cards().getAll().mapNotNull { c -> c.price?.let { PriceHistory(c.id, today, it, c.priceCurrency) } },
        )
    }

    /** Sets the price alerts of a card (in [currency]); null clears one. */
    suspend fun setAlerts(card: OwnedCard, above: Double?, below: Double?, currency: String) {
        db.cards().update(card.copy(alertAbove = above, alertBelow = below, alertCurrency = currency.takeIf { above != null || below != null }))
    }

    /** An alert when the value per copy crossed one of the card's alert prices with this refresh. */
    private fun alertFor(before: OwnedCard, after: OwnedCard, s: AppSettings): PriceAlert? {
        val cur = after.alertCurrency ?: return null
        val old = Money.unit(before, cur, s.usdToEur)
        val now = Money.unit(after, cur, s.usdToEur)
        if (now <= 0.0) return null
        after.alertAbove?.let { limit ->
            if (now >= limit && old < limit) return PriceAlert(after.name, com.monkaydee.tcgcatalogue.ui.AppStrings.get(R.string.alert_above, Money.format(now, cur), Money.format(limit, cur)), after.id)
        }
        after.alertBelow?.let { limit ->
            if (now <= limit && old > limit) return PriceAlert(after.name, com.monkaydee.tcgcatalogue.ui.AppStrings.get(R.string.alert_below, Money.format(now, cur), Money.format(limit, cur)), after.id)
        }
        return null
    }

    // ---- Trade list ----

    suspend fun setForTrade(card: OwnedCard, forTrade: Boolean) = db.cards().update(card.copy(forTrade = forTrade))

    // ---- Selling ----

    /**
     * Sells [quantity] copies of [card] for [pricePerCopy] (in [currency]): they move to the sold
     * list (with the purchase price, for the profit) and leave the collection.
     */
    suspend fun sell(card: OwnedCard, quantity: Int, pricePerCopy: Double, currency: String, soldAt: Long = System.currentTimeMillis()) {
        val n = quantity.coerceIn(1, card.quantity)
        db.sold().insert(
            SoldCard(
                game = card.game, cardId = card.cardId, variant = card.variant, variantLabel = card.variantLabel,
                name = card.name, number = card.number, setName = card.setName, imageUrl = card.imageUrl,
                quantity = n, condition = card.condition,
                purchasePrice = card.purchasePrice, purchaseCurrency = card.priceCurrency,
                salePrice = pricePerCopy, saleCurrency = currency, soldAt = soldAt,
            ),
        )
        if (n >= card.quantity) delete(card) else {
            db.cards().update(card.copy(quantity = card.quantity - n))
            runCatching { snapshot() }
        }
    }

    suspend fun deleteSold(card: SoldCard) = db.sold().delete(card)

    // ---- Wishlist ----

    suspend fun addToWishlist(card: CardCandidate, variant: Variant) {
        val s = settings.current()
        val price = rawPrice(card, variant, s)
        val existing = db.wishlist().find(card.game, card.cardId, variant.key)
        db.wishlist().upsert(
            WishCard(
                id = existing?.id ?: 0, game = card.game, cardId = card.cardId, variant = variant.key, variantLabel = variant.label,
                name = card.name, number = card.number, setId = card.setId, setName = card.setName, rarity = card.rarity,
                imageUrl = variant.imageUrl ?: card.imageUrl, price = price?.amount, priceCurrency = price?.currency ?: "USD",
                priceUpdatedAt = System.currentTimeMillis(), targetPrice = existing?.targetPrice, targetCurrency = existing?.targetCurrency,
                addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFromWishlist(card: WishCard) = db.wishlist().delete(card)

    /** Sets the price to be notified at when a wished card gets cheaper (null clears it). */
    suspend fun setWishTarget(card: WishCard, target: Double?, currency: String) =
        db.wishlist().update(card.copy(targetPrice = target, targetCurrency = currency.takeIf { target != null }))

    /** Refreshes wishlist prices; returns alerts for cards that reached their target price. */
    private suspend fun refreshWishlist(s: AppSettings): List<PriceAlert> {
        val out = mutableListOf<PriceAlert>()
        for (w in db.wishlist().getAll()) {
            val fresh = runCatching { fetch(w.game, w.cardId) }.getOrNull() ?: continue
            val variant = fresh.variants.firstOrNull { it.key == w.variant } ?: fresh.variants.first()
            val price = rawPrice(fresh, variant, s) ?: continue
            val next = w.copy(price = price.amount, priceCurrency = price.currency, priceUpdatedAt = System.currentTimeMillis())
            db.wishlist().update(next)
            val cur = w.targetCurrency ?: continue
            val target = w.targetPrice ?: continue
            val old = w.price?.let { Money.convert(it, w.priceCurrency, cur, s.usdToEur) }
            val now = Money.convert(price.amount, price.currency, cur, s.usdToEur)
            if (now <= target && (old == null || old > target)) {
                out += PriceAlert(w.name, com.monkaydee.tcgcatalogue.ui.AppStrings.get(R.string.alert_wish, Money.format(now, cur), Money.format(target, cur)))
            }
        }
        return out
    }

    // ---- Set checklist ----

    /** Every card of a set, to show which ones are still missing (empty when unknown or offline). */
    suspend fun setChecklist(game: Game, setId: String): List<ChecklistEntry> = runCatching {
        when (game) {
            Game.POKEMON -> pokemonApi(setId).setChecklist(setId)
            Game.ONE_PIECE -> onePiece.setChecklist(setId)
            Game.MAGIC -> scryfall.setChecklist(setId)
            else -> setId.toIntOrNull()?.let { cardIndex.setChecklist(game, it) }.orEmpty()
        }
    }.getOrDefault(emptyList())

    /** The full card behind a checklist entry, to add it or put it on the wishlist. */
    suspend fun checklistCard(game: Game, entry: ChecklistEntry): CardCandidate? = runCatching { fetch(game, entry.cardId) }.getOrNull()

    // ---- Sealed products ----

    fun observeSealed(id: Long) = db.sealed().observe(id)

    suspend fun searchSealed(game: Game, query: String) = runCatching { cardIndex.searchSealed(game, query) }.getOrDefault(emptyList())

    suspend fun addSealed(product: com.monkaydee.tcgcatalogue.data.remote.SealedProduct, quantity: Int, purchasePrice: Double?) {
        val s = settings.current()
        val existing = db.sealed().find(product.game, product.productId)
        if (existing != null) {
            db.sealed().update(existing.copy(quantity = existing.quantity + quantity))
        } else {
            db.sealed().upsert(
                SealedItem(
                    game = product.game, productId = product.productId, name = product.name, groupName = product.groupName,
                    imageUrl = product.imageUrl, quantity = quantity, price = product.price, priceCurrency = "USD",
                    priceUpdatedAt = System.currentTimeMillis(),
                    purchasePrice = purchasePrice?.let { Money.convert(it, s.currency, "USD", s.usdToEur) },
                ),
            )
        }
        runCatching { snapshot() }
    }

    suspend fun updateSealed(item: SealedItem) {
        if (item.quantity <= 0) db.sealed().delete(item) else db.sealed().update(item)
        runCatching { snapshot() }
    }

    private suspend fun refreshSealed() {
        for (item in db.sealed().getAll()) {
            val p = runCatching { cardIndex.sealedProduct(item.game, item.productId) }.getOrNull() ?: continue
            p.price?.let { db.sealed().update(item.copy(price = it, priceCurrency = "USD", priceUpdatedAt = System.currentTimeMillis())) }
        }
    }

    private companion object {
        val JAPANESE = setOf(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HAN)
    }
}
