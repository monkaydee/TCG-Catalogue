package com.monkaydee.tcgcatalogue.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Game(val label: String, val short: String = label) {
    POKEMON("Pokémon"),
    ONE_PIECE("One Piece"),
    MAGIC("Magic: The Gathering", "Magic"),
    DRAGON_BALL_FW("Dragon Ball Fusion World", "DB Fusion World"),
    DRAGON_BALL_SUPER("Dragon Ball Super", "DB Super"),
    UNION_ARENA("Union Arena"),
    WEISS_SCHWARZ("Weiss Schwarz"),
    NARUTO("Naruto"),
    ;

    /** Looked up in the daily card index built from TCGplayer data (scripts/build_card_index.py). */
    val indexed: Boolean get() = this in INDEXED

    companion object {
        val INDEXED = setOf(DRAGON_BALL_FW, DRAGON_BALL_SUPER, UNION_ARENA, WEISS_SCHWARZ, NARUTO)
    }
}

/** One row per distinct (card, variant, condition) the user owns. */
@Serializable
@Entity(
    tableName = "owned_cards",
    indices = [Index(value = ["game", "cardId", "variant", "condition", "language", "copyKey"], unique = true), Index("setId")],
)
data class OwnedCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: Game,
    /** API id: TCGdex id ("sv03.5-025") or One Piece card id ("OP05-060"). */
    val cardId: String,
    /**
     * Pokémon: normal / holo / reverse / firstEdition. One Piece: card_image_id ("OP05-060_p1").
     * Magic: nonfoil / foil / etched. Indexed games: TCGplayer printing ("Normal", "Foil").
     */
    val variant: String,
    val variantLabel: String,
    val name: String,
    /** Printed number, e.g. "025/165" or "OP05-060". */
    val number: String,
    val setId: String,
    val setName: String,
    val rarity: String? = null,
    val imageUrl: String? = null,
    val quantity: Int = 1,
    /** "NM".."DMG" for raw cards, the slab label ("PSA 10", "BGS 10 Black Label") for graded ones. */
    val condition: String = "NM",
    /** Latest market price per copy, in [priceCurrency]. */
    val price: Double? = null,
    val priceCurrency: String = "USD",
    val priceSource: String? = null,
    val priceUpdatedAt: Long? = null,
    val purchasePrice: Double? = null,
    /** Language of the copy ("EN", "DE", "JA" …); prices are looked up for it. */
    @androidx.room.ColumnInfo(defaultValue = "EN") val language: String = "EN",
    @ColumnInfo(defaultValue = "''") val copyKey: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    /** Grading company ("PSA", "BGS", "CGC", ...) for slabbed cards, null for raw cards. */
    val grader: String? = null,
    /** "10", "9.5", ... */
    val grade: String? = null,
    /** "Black Label" (BGS) or "Pristine" (CGC) for the special 10s. */
    val gradeQualifier: String? = null,
    val certNumber: String? = null,
    /** Explains where an unusual price comes from, e.g. when the two markets disagreed. */
    val priceNote: String? = null,
    /** The Cardmarket product (exact print) this copy is priced from, for One Piece. */
    val marketProductId: Long? = null,
    /** "The Best · V.3" */
    val marketLabel: String? = null,
    /** The user's own value per copy, overriding the market price (e.g. for slabs without sales data). */
    val manualPrice: Double? = null,
    val manualCurrency: String? = null,
    /** Offered for trade (shown in the trade list). */
    @ColumnInfo(defaultValue = "0") val forTrade: Boolean = false,
    /** Price alerts per copy, in [alertCurrency]: notify when the value rises above / falls below. */
    val alertAbove: Double? = null,
    val alertBelow: Double? = null,
    val alertCurrency: String? = null,
) {
    val graded: Boolean get() = grader != null
}

/** A card's price per copy on one day, for its price history chart. */
@Serializable
@Entity(tableName = "price_history", primaryKeys = ["cardRowId", "day"])
data class PriceHistory(
    /** [OwnedCard.id] */
    val cardRowId: Long,
    /** LocalDate.toEpochDay() */
    val day: Long,
    val price: Double,
    val currency: String,
)

/** A card the user wants, with its market price and an optional target price to be notified at. */
@Serializable
@Entity(tableName = "wishlist", indices = [Index(value = ["game", "cardId", "variant"], unique = true)])
data class WishCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: Game,
    val cardId: String,
    val variant: String,
    val variantLabel: String,
    val name: String,
    val number: String,
    val setId: String,
    val setName: String,
    val rarity: String? = null,
    val imageUrl: String? = null,
    /** Near-mint market price, in [priceCurrency]. */
    val price: Double? = null,
    val priceCurrency: String = "USD",
    val priceUpdatedAt: Long? = null,
    /** Notify when the price drops to this or lower, in [targetCurrency]. */
    val targetPrice: Double? = null,
    val targetCurrency: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Copies the user sold, kept for the realised profit. */
@Serializable
@Entity(tableName = "sold_cards")
data class SoldCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: Game,
    val cardId: String,
    val variant: String,
    val variantLabel: String,
    val name: String,
    val number: String,
    val setName: String,
    val imageUrl: String? = null,
    val quantity: Int,
    /** Condition or slab label, as in [OwnedCard.condition]. */
    val condition: String,
    /** Paid per copy, in [purchaseCurrency], if known. */
    val purchasePrice: Double? = null,
    val purchaseCurrency: String = "USD",
    /** Sold for per copy, in [saleCurrency]. */
    val salePrice: Double,
    val saleCurrency: String,
    /** Total acquisition and grading costs of this sale, frozen in sale currency. */
    val totalBasis: Double? = null,
    @ColumnInfo(defaultValue = "0") val saleFees: Double = 0.0,
    val soldAt: Long = System.currentTimeMillis(),
)

/** Sealed products (booster boxes, ETBs, decks …) priced from TCGplayer via the daily index. */
@Serializable
@Entity(tableName = "sealed_items", indices = [Index(value = ["game", "productId", "language"], unique = true)])
data class SealedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: Game,
    /** TCGplayer product id. */
    val productId: Long,
    val name: String,
    /** The set or product line it belongs to. */
    val groupName: String,
    val imageUrl: String? = null,
    val quantity: Int = 1,
    /** Market price per item, in [priceCurrency]. */
    val price: Double? = null,
    val priceCurrency: String = "USD",
    val priceUpdatedAt: Long? = null,
    /** Paid per item, in [priceCurrency]. */
    val purchasePrice: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "'EN'") val language: String = "EN",
    @androidx.room.ColumnInfo(defaultValue = "''") val priceSource: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val priceScope: String = "",
    val referencePrice: Double? = null,
    val referenceCurrency: String? = null,
    val referenceSource: String? = null,

    val purchaseCurrency: String? = null,

)

/** Cached set metadata, used for completion percentages and logos. */
@Serializable
@Entity(tableName = "card_sets", primaryKeys = ["game", "setId"])
data class CardSet(
    val game: Game,
    val setId: String,
    val name: String,
    /** Number of cards in the base set (printed total), 0 if unknown. */
    val total: Int,
    val logoUrl: String? = null,
    val releaseDate: String? = null,
)

/** Daily portfolio value, stored in both currencies so the history survives a currency switch. */
@Serializable
@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshot(
    /** LocalDate.toEpochDay() */
    @PrimaryKey val day: Long,
    val valueUsd: Double,
    val valueEur: Double,
    val cardCount: Int,
)
