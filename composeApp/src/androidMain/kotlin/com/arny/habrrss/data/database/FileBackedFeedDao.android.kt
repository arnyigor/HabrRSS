package com.arny.habrrss.data.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections

class FileBackedFeedDao(
    private val file: File,
) : FeedDao {
    private val json = Json { ignoreUnknownKeys = true }
    private val store = loadStore()
    private val items = Collections.synchronizedList(store.items.toMutableList())
    private val localStates = store.localStates.associateBy { it.articleId }.toMutableMap()
    private val favoriteArticles = store.favoriteArticles.associateBy { it.articleId }.toMutableMap()
    private val favoriteTags = store.favoriteTags.associateBy { it.tagId }.toMutableMap()
    private val favoriteHubs = store.favoriteHubs.associateBy { it.hubId }.toMutableMap()
    private val syncStates = store.syncStates.associateBy { it.sourceKey }.toMutableMap()
    private val version = MutableStateFlow(0)

    override fun getByFeed(feedId: String): Flow<List<FeedItemEntity>> =
        version.map { items.byFeed(feedId) }

    override suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity> =
        items.byFeed(feedId)

    override suspend fun getById(id: String): FeedItemEntity? =
        items.firstOrNull { it.id == id }

    override fun observeById(id: String): Flow<FeedItemEntity?> =
        version.map { items.firstOrNull { item -> item.id == id } }

    override suspend fun getByUrl(url: String): FeedItemEntity? =
        items.firstOrNull { it.url == url || it.url.trimEnd('/') == url.trimEnd('/') }

    override fun getBookmarks(): Flow<List<FeedItemEntity>> =
        version.map { items.bookmarks() }

    override suspend fun getBookmarksOnce(): List<FeedItemEntity> =
        items.bookmarks()

    override suspend fun search(query: String): List<FeedItemEntity> {
        val plainQuery = query.trim('%')
        if (plainQuery.isBlank()) return emptyList()
        return items.filter {
            it.title.contains(plainQuery, ignoreCase = true) ||
                it.summary.contains(plainQuery, ignoreCase = true) ||
                it.descriptionHtml?.contains(plainQuery, ignoreCase = true) == true ||
                it.tagsJson.contains(plainQuery, ignoreCase = true) ||
                it.hubsJson.contains(plainQuery, ignoreCase = true) ||
                it.authorName?.contains(plainQuery, ignoreCase = true) == true
        }.sortedByDescending { it.publishedAtEpoch ?: 0L }
    }

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

    override suspend fun deleteOldByFeed(feedId: String, timestamp: Long) {
        items.removeAll { it.feedId == feedId && it.fetchedAt < timestamp }
        persist()
    }

    override suspend fun deleteAll() {
        items.clear()
        persist()
    }

    override fun getArticleLocalStates(): Flow<List<ArticleLocalStateEntity>> =
        version.map { localStates.values.toList() }

    override suspend fun getArticleLocalStatesOnce(): List<ArticleLocalStateEntity> =
        localStates.values.toList()

    override suspend fun getArticleLocalState(articleId: String): ArticleLocalStateEntity? =
        localStates[articleId]

    override suspend fun upsertArticleLocalState(state: ArticleLocalStateEntity) {
        localStates[state.articleId] = state
        persist()
    }

    override fun getFavoriteArticles(): Flow<List<FavoriteArticleEntity>> =
        version.map { favoriteArticles.values.toList() }

    override suspend fun getFavoriteArticlesOnce(): List<FavoriteArticleEntity> =
        favoriteArticles.values.toList()

    override suspend fun insertFavoriteArticle(favorite: FavoriteArticleEntity) {
        favoriteArticles[favorite.articleId] = favorite
        persist()
    }

    override suspend fun deleteFavoriteArticle(articleId: String) {
        favoriteArticles.remove(articleId)
        persist()
    }

    override fun getFavoriteTags(): Flow<List<FavoriteTagEntity>> =
        version.map { favoriteTags.values.toList() }

    override suspend fun getFavoriteTagsOnce(): List<FavoriteTagEntity> =
        favoriteTags.values.toList()

    override suspend fun insertFavoriteTag(tag: FavoriteTagEntity) {
        favoriteTags[tag.tagId] = tag
        persist()
    }

    override suspend fun deleteFavoriteTag(tagId: String) {
        favoriteTags.remove(tagId)
        persist()
    }

    override fun getFavoriteHubs(): Flow<List<FavoriteHubEntity>> =
        version.map { favoriteHubs.values.toList() }

    override suspend fun getFavoriteHubsOnce(): List<FavoriteHubEntity> =
        favoriteHubs.values.toList()

    override suspend fun insertFavoriteHub(hub: FavoriteHubEntity) {
        favoriteHubs[hub.hubId] = hub
        persist()
    }

    override suspend fun deleteFavoriteHub(hubId: String) {
        favoriteHubs.remove(hubId)
        persist()
    }

    override suspend fun getSyncState(sourceKey: String): SyncStateEntity? =
        syncStates[sourceKey]

    override fun observeSyncState(sourceKey: String): Flow<SyncStateEntity?> =
        version.map { syncStates[sourceKey] }

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        syncStates[state.sourceKey] = state
        persist()
    }

    private fun loadStore(): FeedStore {
        if (!file.exists()) return FeedStore()
        val text = file.readText()
        return runCatching { json.decodeFromString<FeedStore>(text) }
            .getOrElse {
                FeedStore(items = runCatching { json.decodeFromString<List<FeedItemEntity>>(text) }.getOrDefault(emptyList()))
            }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val snapshot = synchronized(items) {
            FeedStore(
                items = items.toList(),
                localStates = localStates.values.toList(),
                favoriteArticles = favoriteArticles.values.toList(),
                favoriteTags = favoriteTags.values.toList(),
                favoriteHubs = favoriteHubs.values.toList(),
                syncStates = syncStates.values.toList(),
            )
        }
        file.writeText(json.encodeToString(snapshot))
        version.value += 1
    }

    private fun List<FeedItemEntity>.byFeed(feedId: String): List<FeedItemEntity> =
        filter { it.feedId == feedId }.sortedByDescending { it.publishedAtEpoch ?: 0L }

    private fun List<FeedItemEntity>.bookmarks(): List<FeedItemEntity> =
        filter { it.id in favoriteArticles }.sortedByDescending { favoriteArticles[it.id]?.createdAt ?: 0L }
}

@Serializable
private data class FeedStore(
    val items: List<FeedItemEntity> = emptyList(),
    val localStates: List<ArticleLocalStateEntity> = emptyList(),
    val favoriteArticles: List<FavoriteArticleEntity> = emptyList(),
    val favoriteTags: List<FavoriteTagEntity> = emptyList(),
    val favoriteHubs: List<FavoriteHubEntity> = emptyList(),
    val syncStates: List<SyncStateEntity> = emptyList(),
)
