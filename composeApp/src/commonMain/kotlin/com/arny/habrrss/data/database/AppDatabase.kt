package com.arny.habrrss.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FeedItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
}
