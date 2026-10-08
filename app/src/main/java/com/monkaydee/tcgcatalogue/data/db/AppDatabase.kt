package com.monkaydee.tcgcatalogue.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        OwnedCard::class, CardSet::class, PortfolioSnapshot::class,
        PriceHistory::class, WishCard::class, SoldCard::class, SealedItem::class, CostLot::class, GradingSubmission::class, ReviewReceipt::class, CardBinder::class, BinderCard::class, Deck::class, DeckCard::class,
    ],
    version = 13,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7, spec = SlabIdentityMigration::class), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 11, to = 12), AutoMigration(from = 12, to = 13),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tools(): ToolsDao
    abstract fun binders(): BinderDao
    abstract fun decks(): DeckDao
    abstract fun cards(): CardDao
    abstract fun sets(): SetDao
    abstract fun snapshots(): SnapshotDao
    abstract fun history(): PriceHistoryDao
    abstract fun wishlist(): WishDao
    abstract fun sold(): SoldDao
    abstract fun sealed(): SealedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg-catalogue.db").addMigrations(MIGRATION_10_11).build()
    }
}

/** Preserve old slabs while giving every new physical certificate its own collection row. */
class SlabIdentityMigration : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("UPDATE owned_cards SET copyKey = CASE WHEN certNumber IS NOT NULL AND TRIM(certNumber) != '' THEN 'cert:' || TRIM(certNumber) ELSE 'slab:legacy:' || id END WHERE grader IS NOT NULL")
    }
}

/**
 * Binder entries become pockets with a position: each binder's cards keep their order (by row,
 * as they were shown) in pockets 0, 1, 2 …
 */
val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `binder_slots` (`binderId` INTEGER NOT NULL, `cardRowId` INTEGER NOT NULL, `slot` INTEGER NOT NULL, PRIMARY KEY(`binderId`, `slot`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_binder_slots_cardRowId` ON `binder_slots` (`cardRowId`)")
        val next = HashMap<Long, Int>()
        db.query("SELECT binderId, cardRowId FROM binder_cards ORDER BY binderId, cardRowId").use { c ->
            while (c.moveToNext()) {
                val binder = c.getLong(0)
                val slot = next.getOrDefault(binder, 0)
                next[binder] = slot + 1
                db.execSQL("INSERT INTO binder_slots (binderId, cardRowId, slot) VALUES (?, ?, ?)", arrayOf<Any>(binder, c.getLong(1), slot))
            }
        }
        db.execSQL("DROP TABLE IF EXISTS `binder_cards`")
    }
}
