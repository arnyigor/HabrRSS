package com.arny.habrrss.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FeedItemEntity::class,
        ArticleLocalStateEntity::class,
        FavoriteArticleEntity::class,
        FavoriteTagEntity::class,
        FavoriteHubEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
}
