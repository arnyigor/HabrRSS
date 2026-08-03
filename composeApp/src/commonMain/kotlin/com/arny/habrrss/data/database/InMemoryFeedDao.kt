package com.arny.habrrss.data.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory feed storage with thread-safety.
 * Note: This implementation loses data on app restart. For production, use Room-based FeedDao.
 */
class InMemoryFeedDao : FeedDao {
    private val items = mutableListOf<FeedItemEntity>()
    private val localStates = mutableMapOf<String, ArticleLocalStateEntity>()
    private val favoriteArticles = mutableMapOf<String, FavoriteArticleEntity>()
    private val favoriteTags = mutableMapOf<String, FavoriteTagEntity>()
    private val favoriteHubs = mutableMapOf<String, FavoriteHubEntity>()
    private val syncStates = mutableMapOf<String, SyncStateEntity>()
    private val version = MutableStateFlow(0)

    override fun getByFeed(feedId: String): Flow<List<FeedItemEntity>> =
        version.map { items.byFeed(feedId) }

    override suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity> =
        items.byFeed(feedId)

    override fun getAllCached(): Flow<List<FeedItemEntity>> =
        version.map { items.sortedByDescending { item -> item.publishedAtEpoch ?: item.fetchedAt } }

    override suspend fun getAllCachedOnce(): List<FeedItemEntity> =
        items.sortedByDescending { it.publishedAtEpoch ?: it.fetchedAt }

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

    override suspend fun search(query: String): List<FeedItemEntity> = items.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.summary.contains(query, ignoreCase = true) ||
            it.descriptionHtml?.contains(query, ignoreCase = true) == true ||
            it.tagsJson.contains(query, ignoreCase = true) ||
            it.hubsJson.contains(query, ignoreCase = true) ||
            it.authorName?.contains(query, ignoreCase = true) == true
    }

    override suspend fun insertAll(items: List<FeedItemEntity>) {
        if (items.isEmpty()) return
        val newIds = items.map { it.id }.toHashSet()
        this.items.removeAll { it.id in newIds }
        this.items.addAll(items)
        notifyChanged()
    }

    override suspend fun update(item: FeedItemEntity) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            items[index] = item
            notifyChanged()
        }
    }

    override suspend fun deleteOldByFeed(feedId: String, timestamp: Long) {
        items.removeAll { it.feedId == feedId && it.fetchedAt < timestamp }
        notifyChanged()
    }

    override suspend fun deleteAll() {
        items.clear()
        notifyChanged()
    }

    override fun getArticleLocalStates(): Flow<List<ArticleLocalStateEntity>> =
        version.map { localStates.values.toList() }

    override suspend fun getArticleLocalStatesOnce(): List<ArticleLocalStateEntity> =
        localStates.values.toList()

    override suspend fun getArticleLocalState(articleId: String): ArticleLocalStateEntity? =
        localStates[articleId]

    override suspend fun upsertArticleLocalState(state: ArticleLocalStateEntity) {
        localStates[state.articleId] = state
        notifyChanged()
    }

    override fun getFavoriteArticles(): Flow<List<FavoriteArticleEntity>> =
        version.map { favoriteArticles.values.toList() }

    override suspend fun getFavoriteArticlesOnce(): List<FavoriteArticleEntity> =
        favoriteArticles.values.toList()

    override suspend fun insertFavoriteArticle(favorite: FavoriteArticleEntity) {
        favoriteArticles[favorite.articleId] = favorite
        notifyChanged()
    }

    override suspend fun deleteFavoriteArticle(articleId: String) {
        favoriteArticles.remove(articleId)
        notifyChanged()
    }

    override fun getFavoriteTags(): Flow<List<FavoriteTagEntity>> =
        version.map { favoriteTags.values.toList() }

    override suspend fun getFavoriteTagsOnce(): List<FavoriteTagEntity> =
        favoriteTags.values.toList()

    override suspend fun insertFavoriteTag(tag: FavoriteTagEntity) {
        favoriteTags[tag.tagId] = tag
        notifyChanged()
    }

    override suspend fun deleteFavoriteTag(tagId: String) {
        favoriteTags.remove(tagId)
        notifyChanged()
    }

    override fun getFavoriteHubs(): Flow<List<FavoriteHubEntity>> =
        version.map { favoriteHubs.values.toList() }

    override suspend fun getFavoriteHubsOnce(): List<FavoriteHubEntity> =
        favoriteHubs.values.toList()

    override suspend fun insertFavoriteHub(hub: FavoriteHubEntity) {
        favoriteHubs[hub.hubId] = hub
        notifyChanged()
    }

    override suspend fun deleteFavoriteHub(hubId: String) {
        favoriteHubs.remove(hubId)
        notifyChanged()
    }

    override suspend fun getSyncState(sourceKey: String): SyncStateEntity? =
        syncStates[sourceKey]

    override fun observeSyncState(sourceKey: String): Flow<SyncStateEntity?> =
        version.map { syncStates[sourceKey] }

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        syncStates[state.sourceKey] = state
        notifyChanged()
    }

    private fun notifyChanged() {
        version.value += 1
    }

    private fun List<FeedItemEntity>.byFeed(feedId: String): List<FeedItemEntity> =
        filter { it.feedId == feedId }.sortedByDescending { it.publishedAtEpoch ?: it.fetchedAt }

    private fun List<FeedItemEntity>.bookmarks(): List<FeedItemEntity> =
        filter { it.id in favoriteArticles }.sortedByDescending { favoriteArticles[it.id]?.createdAt ?: it.fetchedAt }
}
