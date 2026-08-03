package com.arny.habrrss.data.database

actual fun createPlatformFeedDao(): FeedDao {
    val database = getDatabaseBuilder()
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
    return RoomFeedDao(database.feedDao())
}
