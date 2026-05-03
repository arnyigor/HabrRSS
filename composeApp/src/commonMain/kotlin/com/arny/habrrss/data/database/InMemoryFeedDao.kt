package com.arny.habrrss.data.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Collections

/**
 * In-memory feed storage with thread-safety.
 * Note: This implementation loses data on app restart. For production, use Room-based FeedDao.
 */
class InMemoryFeedDao : FeedDao {
    // Thread-safe synchronized list
    private val items = Collections.synchronizedList(mutableListOf<FeedItemEntity>())
    
    override fun getByFeed(feedId: String): Flow<List<FeedItemEntity>> = 
        flowOf(items.filter { it.feedId == feedId }.sortedByDescending { it.publishedAt })
    
    override suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity> = 
        items.filter { it.feedId == feedId }.sortedByDescending { it.publishedAt }
    
    override suspend fun getById(id: String): FeedItemEntity? = 
        items.find { it.id == id }
    
    override fun getBookmarks(): Flow<List<FeedItemEntity>> = 
        flowOf(items.filter { it.isBookmarked }.sortedByDescending { it.publishedAt })
    
    override suspend fun getBookmarksOnce(): List<FeedItemEntity> = 
        items.filter { it.isBookmarked }.sortedByDescending { it.publishedAt }
    
    override suspend fun search(query: String): List<FeedItemEntity> = items.filter {
        it.title.contains(query, ignoreCase = true) || it.summary.contains(query, ignoreCase = true)
    }

    // FTS not supported in memory - fallback to LIKE search
    override suspend fun searchFts(query: String): List<FeedItemEntity> = search(query)
    
    // Fixed: O(n) instead of O(n²) - use HashSet for lookup
    // Note: parameter name 'items' matches FeedDao interface for compatibility
    override suspend fun insertAll(items: List<FeedItemEntity>) {
        if (items.isEmpty()) return
        val newIds = items.map { it.id }.toHashSet()
        this.items.removeAll { it.id in newIds }
        this.items.addAll(items)
    }
    
    override suspend fun update(item: FeedItemEntity) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            synchronized(items) {
                items[index] = item
            }
        }
    }
    
    override suspend fun updateRead(id: String, isRead: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            synchronized(items) {
                items[index] = items[index].copy(isRead = isRead)
            }
        }
    }
    
    override suspend fun updateBookmark(id: String, isBookmarked: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            synchronized(items) {
                items[index] = items[index].copy(isBookmarked = isBookmarked)
            }
        }
    }
    
    override suspend fun deleteOldByFeed(feedId: String, timestamp: Long) {
        items.removeAll { it.feedId == feedId && it.fetchedAt < timestamp }
    }
    
    override suspend fun deleteAll() {
        items.clear()
    }
}