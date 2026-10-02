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

    @Query("SELECT * FROM owned_cards WHERE game = :game AND cardId = :cardId AND variant = :variant AND condition = :condition LIMIT 1")
    suspend fun find(game: Game, cardId: String, variant: String, condition: String): OwnedCard?

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
        val existing = find(card.game, card.cardId, card.variant, card.condition)
        return if (existing != null) {
            update(
                existing.copy(
                    quantity = existing.quantity + card.quantity,
                    price = card.price ?: existing.price,
                    priceCurrency = if (card.price != null) card.priceCurrency else existing.priceCurrency,
                    priceSource = card.priceSource ?: existing.priceSource,
                    priceUpdatedAt = card.priceUpdatedAt ?: existing.priceUpdatedAt,
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
}
