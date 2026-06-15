package com.arny.habrrss.data.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-based implementation of FeedDao with persistent storage.
 * This implementation persists data across app restarts.
 */
class RoomFeedDao(private val feedDao: FeedDao) : FeedDao {

    override fun getByFeed(feedId: String): Flow<List<FeedItemEntity>> =
        feedDao.getByFeed(feedId)

    override suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity> =
        feedDao.getByFeedOnce(feedId)

    override suspend fun getById(id: String): FeedItemEntity? =
        feedDao.getById(id)

    override suspend fun getByUrl(url: String): FeedItemEntity? =
        feedDao.getByUrl(url)

    override fun getBookmarks(): Flow<List<FeedItemEntity>> =
        feedDao.getBookmarks()

    override suspend fun getBookmarksOnce(): List<FeedItemEntity> =
        feedDao.getBookmarksOnce()

    override suspend fun search(query: String): List<FeedItemEntity> =
        feedDao.search(query)

    override suspend fun insertAll(items: List<FeedItemEntity>) =
        feedDao.insertAll(items)

    override suspend fun update(item: FeedItemEntity) =
        feedDao.update(item)

    override suspend fun updateRead(id: String, isRead: Boolean) =
        feedDao.updateRead(id, isRead)

    override suspend fun updateBookmark(id: String, isBookmarked: Boolean) =
        feedDao.updateBookmark(id, isBookmarked)

    override suspend fun deleteOldByFeed(feedId: String, timestamp: Long) =
        feedDao.deleteOldByFeed(feedId, timestamp)

    override suspend fun deleteAll() =
        feedDao.deleteAll()
}