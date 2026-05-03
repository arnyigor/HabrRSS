package com.arny.habrrss.data.database

import java.io.File

actual fun createPlatformFeedDao(): FeedDao {
    val dir = File(System.getProperty("user.home"), ".habrrss")
    return FileBackedFeedDao(
        file = File(dir, "habr_feed_cache.json"),
    )
}
