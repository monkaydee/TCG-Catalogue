package com.monkaydee.tcgcatalogue.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID

/** Immutable currency and per-copy costs. Null means unknown; zero is an explicit zero cost. */
@Serializable
@Entity(tableName = "cost_lots", indices = [Index("cardRowId")])
data class CostLot(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val cardRowId: Long,
    val quantity: Int,
    val purchase: Double? = null,
    val grading: Double? = null,
    val shipping: Double? = null,
    val tax: Double? = null,
    val currency: String,
    val acquiredAt: Long = System.currentTimeMillis(),
    /** When the lot was last corrected (0 = never); a backup merge keeps the newer version. */
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "grading_submissions", indices = [Index("cardRowId")])
data class GradingSubmission(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val cardRowId: Long,
    val company: String,
    val reference: String = "",
    val status: String = "PREPARING",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "review_receipts")
data class ReviewReceipt(@PrimaryKey val token: String)

@Dao
interface ToolsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun receipt(receipt: ReviewReceipt): Long
    @Query("SELECT EXISTS(SELECT 1 FROM review_receipts WHERE token=:token)") suspend fun reviewed(token: String): Boolean
    @Query("SELECT * FROM cost_lots ORDER BY acquiredAt, id") fun observeLots(): Flow<List<CostLot>>
    @Query("SELECT * FROM cost_lots ORDER BY acquiredAt, id") suspend fun lots(): List<CostLot>
    @Query("SELECT * FROM cost_lots WHERE cardRowId = :row ORDER BY acquiredAt, id") suspend fun lots(row: Long): List<CostLot>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(lot: CostLot)
    @Query("DELETE FROM cost_lots WHERE id = :id") suspend fun removeLot(id: String)
    @Query("DELETE FROM cost_lots WHERE cardRowId = :row") suspend fun removeLots(row: Long)
    @Query("UPDATE cost_lots SET cardRowId = :to WHERE cardRowId = :from") suspend fun moveLots(from: Long, to: Long)
    @Query("DELETE FROM cost_lots") suspend fun clearLots()
    @Query("SELECT * FROM grading_submissions ORDER BY updatedAt DESC") fun observeSubmissions(): Flow<List<GradingSubmission>>
    @Query("SELECT * FROM grading_submissions") suspend fun submissions(): List<GradingSubmission>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(submission: GradingSubmission)
    @Query("UPDATE grading_submissions SET cardRowId = :to WHERE cardRowId = :from") suspend fun moveSubmissions(from: Long, to: Long)
    @Query("DELETE FROM grading_submissions WHERE id = :id") suspend fun removeSubmission(id: String)
    @Query("DELETE FROM grading_submissions WHERE cardRowId = :row") suspend fun removeSubmissions(row: Long)
    @Query("DELETE FROM grading_submissions") suspend fun clearSubmissions()
}

/** A binder of the user's own besides the main binder that holds every card. */
@Serializable
@Entity(tableName = "binders")
data class CardBinder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A preset cover design ([com.monkaydee.tcgcatalogue.data.CoverDesign] key). */
    val cover: String = "midnight",
    /** The user's own cover picture (a file in the app's storage), shown instead of the design. */
    val coverImage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A collection row placed in a binder; a card may sit in several binders. */
@Serializable
@Entity(tableName = "binder_cards", primaryKeys = ["binderId", "cardRowId"], indices = [Index("cardRowId")])
data class BinderCard(val binderId: Long, val cardRowId: Long)

@Dao
interface BinderDao {
    @Query("SELECT * FROM binders ORDER BY createdAt, id") fun observe(): kotlinx.coroutines.flow.Flow<List<CardBinder>>
    @Query("SELECT * FROM binders ORDER BY createdAt, id") suspend fun all(): List<CardBinder>
    @Query("SELECT * FROM binders WHERE id = :id") suspend fun get(id: Long): CardBinder?
    @Insert suspend fun insert(binder: CardBinder): Long
    @Update suspend fun update(binder: CardBinder)
    @Query("DELETE FROM binders WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM binders") suspend fun deleteAll()
    @Query("SELECT * FROM binder_cards") fun observeCards(): kotlinx.coroutines.flow.Flow<List<BinderCard>>
    @Query("SELECT * FROM binder_cards") suspend fun cards(): List<BinderCard>
    @Query("SELECT cardRowId FROM binder_cards WHERE binderId = :binder") suspend fun rows(binder: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(cards: List<BinderCard>)
    @Query("DELETE FROM binder_cards WHERE binderId = :binder") suspend fun clear(binder: Long)
    @Query("DELETE FROM binder_cards WHERE cardRowId = :row") suspend fun removeRow(row: Long)
    @Query("UPDATE OR IGNORE binder_cards SET cardRowId = :to WHERE cardRowId = :from") suspend fun moveRow(from: Long, to: Long)
    @Query("DELETE FROM binder_cards") suspend fun clearAll()
}
