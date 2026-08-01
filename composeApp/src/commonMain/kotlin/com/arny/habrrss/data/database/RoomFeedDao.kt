package com.arny.habrrss.data.database

import kotlinx.coroutines.flow.Flow

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

    override fun observeById(id: String): Flow<FeedItemEntity?> =
        feedDao.observeById(id)

    override suspend fun getByUrl(url: String): FeedItemEntity? =
        feedDao.getByUrl(url)

    override suspend fun search(query: String): List<FeedItemEntity> =
        feedDao.search(query)

    override suspend fun insertAll(items: List<FeedItemEntity>) =
        feedDao.insertAll(items)

    override suspend fun update(item: FeedItemEntity) =
        feedDao.update(item)

    override suspend fun deleteOldByFeed(feedId: String, timestamp: Long) =
        feedDao.deleteOldByFeed(feedId, timestamp)

    override suspend fun deleteAll() =
        feedDao.deleteAll()

    override fun getArticleLocalStates(): Flow<List<ArticleLocalStateEntity>> =
        feedDao.getArticleLocalStates()

    override suspend fun getArticleLocalStatesOnce(): List<ArticleLocalStateEntity> =
        feedDao.getArticleLocalStatesOnce()

    override suspend fun getArticleLocalState(articleId: String): ArticleLocalStateEntity? =
        feedDao.getArticleLocalState(articleId)

    override suspend fun upsertArticleLocalState(state: ArticleLocalStateEntity) =
        feedDao.upsertArticleLocalState(state)

    override fun getFavoriteArticles(): Flow<List<FavoriteArticleEntity>> =
        feedDao.getFavoriteArticles()

    override suspend fun getFavoriteArticlesOnce(): List<FavoriteArticleEntity> =
        feedDao.getFavoriteArticlesOnce()

    override suspend fun insertFavoriteArticle(favorite: FavoriteArticleEntity) =
        feedDao.insertFavoriteArticle(favorite)

    override suspend fun deleteFavoriteArticle(articleId: String) =
        feedDao.deleteFavoriteArticle(articleId)

    override fun getBookmarks(): Flow<List<FeedItemEntity>> =
        feedDao.getBookmarks()

    override suspend fun getBookmarksOnce(): List<FeedItemEntity> =
        feedDao.getBookmarksOnce()

    override fun getFavoriteTags(): Flow<List<FavoriteTagEntity>> =
        feedDao.getFavoriteTags()

    override suspend fun getFavoriteTagsOnce(): List<FavoriteTagEntity> =
        feedDao.getFavoriteTagsOnce()

    override suspend fun insertFavoriteTag(tag: FavoriteTagEntity) =
        feedDao.insertFavoriteTag(tag)

    override suspend fun deleteFavoriteTag(tagId: String) =
        feedDao.deleteFavoriteTag(tagId)

    override fun getFavoriteHubs(): Flow<List<FavoriteHubEntity>> =
        feedDao.getFavoriteHubs()

    override suspend fun getFavoriteHubsOnce(): List<FavoriteHubEntity> =
        feedDao.getFavoriteHubsOnce()

    override suspend fun insertFavoriteHub(hub: FavoriteHubEntity) =
        feedDao.insertFavoriteHub(hub)

    override suspend fun deleteFavoriteHub(hubId: String) =
        feedDao.deleteFavoriteHub(hubId)

}
