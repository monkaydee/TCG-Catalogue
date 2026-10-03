package com.monkaydee.tcgcatalogue.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OwnedCard::class, CardSet::class, PortfolioSnapshot::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun sets(): SetDao
    abstract fun snapshots(): SnapshotDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg-catalogue.db").build()
    }
}
