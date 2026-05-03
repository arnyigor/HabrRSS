package com.arny.habrrss.data.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections

class FileBackedFeedDao(
    private val file: File,
) : FeedDao {
    private val json = Json { ignoreUnknownKeys = true }
    private val items = Collections.synchronizedList(loadItems().toMutableList())

    override fun getByFeed(feedId: String): Flow<List<FeedItemEntity>> =
        flowOf(items.byFeed(feedId))

    override suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity> =
        items.byFeed(feedId)

    override suspend fun getById(id: String): FeedItemEntity? =
        items.firstOrNull { it.id == id }

    override fun getBookmarks(): Flow<List<FeedItemEntity>> =
        flowOf(items.bookmarks())

    override suspend fun getBookmarksOnce(): List<FeedItemEntity> =
        items.bookmarks()

    override suspend fun search(query: String): List<FeedItemEntity> {
        val plainQuery = query.trim('%')
        if (plainQuery.isBlank()) return emptyList()
        return items.filter {
            it.title.contains(plainQuery, ignoreCase = true) ||
                it.summary.contains(plainQuery, ignoreCase = true) ||
                it.authorName?.contains(plainQuery, ignoreCase = true) == true
        }.sortedByDescending { it.publishedAt }
    }

    // FTS not supported in file-based storage - fallback to LIKE search
    override suspend fun searchFts(query: String): List<FeedItemEntity> = search(query)

    override suspend fun insertAll(items: List<FeedItemEntity>) {
        if (items.isEmpty()) return
        val newIds = items.map { it.id }.toHashSet()
        synchronized(this.items) {
            this.items.removeAll { it.id in newIds }
            this.items.addAll(items)
        }
        persist()
    }

    override suspend fun update(item: FeedItemEntity) {
        synchronized(items) {
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) items[index] = item
        }
        persist()
    }

    override suspend fun updateRead(id: String, isRead: Boolean) {
        updateById(id) { it.copy(isRead = isRead) }
    }

    override suspend fun updateBookmark(id: String, isBookmarked: Boolean) {
        updateById(id) { it.copy(isBookmarked = isBookmarked) }
    }

    override suspend fun deleteOldByFeed(feedId: String, timestamp: Long) {
        items.removeAll { it.feedId == feedId && it.fetchedAt < timestamp }
        persist()
    }

    override suspend fun deleteAll() {
        items.clear()
        persist()
    }

    private fun updateById(id: String, transform: (FeedItemEntity) -> FeedItemEntity) {
        synchronized(items) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = transform(items[index])
        }
        persist()
    }

    private fun loadItems(): List<FeedItemEntity> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FeedItemEntity>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val snapshot = synchronized(items) { items.toList() }
        file.writeText(json.encodeToString(snapshot))
    }

    private fun List<FeedItemEntity>.byFeed(feedId: String): List<FeedItemEntity> =
        filter { it.feedId == feedId }.sortedByDescending { it.publishedAt }

    private fun List<FeedItemEntity>.bookmarks(): List<FeedItemEntity> =
        filter { it.isBookmarked }.sortedByDescending { it.publishedAt }
}
