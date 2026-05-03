package com.arny.habrrss.data.database

import java.io.File

actual fun createPlatformFeedDao(): FeedDao {
    if (!isAppContextInitialized()) return InMemoryFeedDao()
    return FileBackedFeedDao(
        file = File(appContext.filesDir, "habr_feed_cache.json"),
    )
}
