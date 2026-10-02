package com.monkaydee.tcgcatalogue.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Game(val label: String) {
    POKEMON("Pokémon"),
    ONE_PIECE("One Piece"),
}

/** One row per distinct (card, variant, condition) the user owns. */
@Serializable
@Entity(
    tableName = "owned_cards",
    indices = [Index(value = ["game", "cardId", "variant", "condition"], unique = true), Index("setId")],
)
data class OwnedCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: Game,
    /** API id: TCGdex id ("sv03.5-025") or One Piece card id ("OP05-060"). */
    val cardId: String,
    /** Pokémon: normal / holo / reverse / firstEdition. One Piece: card_image_id ("OP05-060_p1"). */
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
    val condition: String = "NM",
    /** Latest market price per copy, in [priceCurrency]. */
    val price: Double? = null,
    val priceCurrency: String = "USD",
    val priceSource: String? = null,
    val priceUpdatedAt: Long? = null,
    val purchasePrice: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
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
