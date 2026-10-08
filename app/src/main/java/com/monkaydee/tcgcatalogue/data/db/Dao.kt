package com.monkaydee.tcgcatalogue.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM owned_cards ORDER BY setName, number")
    fun observeAll(): Flow<List<OwnedCard>>

    @Query("SELECT * FROM owned_cards")
    suspend fun getAll(): List<OwnedCard>

    @Query("SELECT * FROM owned_cards WHERE game = :game AND setId = :setId")
    fun observeSet(game: Game, setId: String): Flow<List<OwnedCard>>

    @Query("SELECT * FROM owned_cards WHERE id = :id")
    fun observe(id: Long): Flow<OwnedCard?>

    @Query("SELECT * FROM owned_cards WHERE id = :id")
    suspend fun get(id: Long): OwnedCard?

    @Query("SELECT * FROM owned_cards WHERE game = :game AND cardId = :cardId AND variant = :variant AND condition = :condition AND language = :language AND copyKey = :copyKey LIMIT 1")
    suspend fun find(game: Game, cardId: String, variant: String, condition: String, language: String = "EN", copyKey: String = ""): OwnedCard?

    @Query("SELECT * FROM owned_cards WHERE UPPER(grader)=UPPER(:grader) AND TRIM(certNumber)=TRIM(:certificate) AND id!=:excluding LIMIT 1")
    suspend fun certificate(grader: String, certificate: String, excluding: Long = 0): OwnedCard?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: OwnedCard): Long

    @Update
    suspend fun update(card: OwnedCard)

    @Delete
    suspend fun delete(card: OwnedCard)

    @Query("DELETE FROM owned_cards")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<OwnedCard>)

    /** Adds [card] or bumps the quantity of the matching row. Returns the row id. */
    @Transaction
    suspend fun addOrIncrement(card: OwnedCard): Long {
        if (card.graded && !card.certNumber.isNullOrBlank()) check(certificate(card.grader!!, card.certNumber) == null) { com.monkaydee.tcgcatalogue.ui.AppStrings.get(com.monkaydee.tcgcatalogue.R.string.tools_duplicate_cert) }
        val existing = find(card.game, card.cardId, card.variant, card.condition, card.language, card.copyKey)
        return if (existing != null) {
            check(!card.graded || card.certNumber.isNullOrBlank()) { com.monkaydee.tcgcatalogue.ui.AppStrings.get(com.monkaydee.tcgcatalogue.R.string.tools_duplicate_cert) }
            update(
                existing.copy(
                    quantity = existing.quantity + card.quantity,
                    price = card.price ?: existing.price,
                    priceCurrency = if (card.price != null) card.priceCurrency else existing.priceCurrency,
                    priceSource = if (card.price != null) card.priceSource else existing.priceSource,
                    priceNote = if (card.price != null) card.priceNote else existing.priceNote,
                    priceUpdatedAt = if (card.price != null) card.priceUpdatedAt else existing.priceUpdatedAt,
                ),
            )
            existing.id
        } else {
            insert(card)
        }
    }
}

@Dao
interface SetDao {
    @Query("SELECT * FROM card_sets")
    fun observeAll(): Flow<List<CardSet>>

    @Query("SELECT * FROM card_sets WHERE game = :game AND setId = :setId")
    suspend fun get(game: Game, setId: String): CardSet?

    @Upsert
    suspend fun upsert(set: CardSet)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM portfolio_snapshots ORDER BY day")
    fun observeAll(): Flow<List<PortfolioSnapshot>>

    @Query("SELECT * FROM portfolio_snapshots ORDER BY day")
    suspend fun getAll(): List<PortfolioSnapshot>

    @Upsert
    suspend fun upsert(snapshot: PortfolioSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<PortfolioSnapshot>)

    @Query("DELETE FROM portfolio_snapshots")
    suspend fun deleteAll()
}

@Dao
interface PriceHistoryDao {
    @Query("SELECT * FROM price_history WHERE cardRowId = :cardRowId ORDER BY day")
    fun observe(cardRowId: Long): Flow<List<PriceHistory>>

    @Query("SELECT * FROM price_history")
    suspend fun getAll(): List<PriceHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<PriceHistory>)

    @Query("DELETE FROM price_history WHERE cardRowId = :cardRowId")
    suspend fun deleteFor(cardRowId: Long)

    @Query("DELETE FROM price_history")
    suspend fun deleteAll()
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WishCard>>

    @Query("SELECT * FROM wishlist")
    suspend fun getAll(): List<WishCard>

    @Query("SELECT * FROM wishlist WHERE game = :game AND cardId = :cardId AND variant = :variant LIMIT 1")
    suspend fun find(game: Game, cardId: String, variant: String): WishCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: WishCard): Long

    @Update
    suspend fun update(card: WishCard)

    @Delete
    suspend fun delete(card: WishCard)

    @Query("DELETE FROM wishlist")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<WishCard>)
}

@Dao
interface SoldDao {
    @Query("SELECT * FROM sold_cards ORDER BY soldAt DESC")
    fun observeAll(): Flow<List<SoldCard>>

    @Query("SELECT * FROM sold_cards")
    suspend fun getAll(): List<SoldCard>

    @Insert
    suspend fun insert(card: SoldCard): Long

    @Delete
    suspend fun delete(card: SoldCard)

    @Query("DELETE FROM sold_cards")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<SoldCard>)
}

@Dao
interface SealedDao {
    @Query("SELECT * FROM sealed_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SealedItem>>

    @Query("SELECT * FROM sealed_items")
    suspend fun getAll(): List<SealedItem>

    @Query("SELECT * FROM sealed_items WHERE id = :id")
    suspend fun get(id: Long): SealedItem?

    @Query("SELECT * FROM sealed_items WHERE id = :id")
    fun observe(id: Long): Flow<SealedItem?>

    @Query("SELECT * FROM sealed_items WHERE game = :game AND productId = :productId AND language = :language LIMIT 1")
    suspend fun find(game: Game, productId: Long, language: String = "EN"): SealedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SealedItem): Long

    @Update
    suspend fun update(item: SealedItem)

    @Delete
    suspend fun delete(item: SealedItem)

    @Query("DELETE FROM sealed_items")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SealedItem>)
}
