package com.monkaydee.tcgcatalogue.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        OwnedCard::class, CardSet::class, PortfolioSnapshot::class,
        PriceHistory::class, WishCard::class, SoldCard::class, SealedItem::class, CostLot::class, GradingSubmission::class, ReviewReceipt::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7, spec = SlabIdentityMigration::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tools(): ToolsDao
    abstract fun cards(): CardDao
    abstract fun sets(): SetDao
    abstract fun snapshots(): SnapshotDao
    abstract fun history(): PriceHistoryDao
    abstract fun wishlist(): WishDao
    abstract fun sold(): SoldDao
    abstract fun sealed(): SealedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg-catalogue.db").build()
    }
}

/** Preserve old slabs while giving every new physical certificate its own collection row. */
class SlabIdentityMigration : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("UPDATE owned_cards SET copyKey = CASE WHEN certNumber IS NOT NULL AND TRIM(certNumber) != '' THEN 'cert:' || TRIM(certNumber) ELSE 'slab:legacy:' || id END WHERE grader IS NOT NULL")
    }
}
