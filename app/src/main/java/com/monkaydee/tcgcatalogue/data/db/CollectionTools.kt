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
    @Query("DELETE FROM grading_submissions") suspend fun clearSubmissions()
}
