package com.arny.habrrss.data.database

actual fun createPlatformFeedDao(): FeedDao {
    val database = getDatabaseBuilder()
        .addMigrations(MIGRATION_1_2)
        .build()
    return RoomFeedDao(database.feedDao())
}
