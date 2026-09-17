package com.arny.habrrss.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    // ---------- Server/cache data ----------

    /**
     * Feed-list queries deliberately exclude the cachedArticleJson TEXT column (full article body
     * JSON) and cap the row count:
     *
     * - cachedArticleJson is only needed by the reader (getById/observeById), never by the list.
     *   Loading it for every row of a hub archive made the feed pipeline hold tens of MB of extra
     *   strings per emission.
     * - The LIMIT bounds the in-memory feed list. Hub archives grow to 10k+ articles and both the
     *   mapped domain list and the intermediate pipeline copies multiplied by that size exhausted
     *   the 256 MB Java heap limit during "Загрузить все страницы" (OutOfMemoryError).
     *   The complete archive stays browsable through the SQL-paged "Все загруженные" feed.
     */

    @Query(
        """
        SELECT id, feedId, title, summary, descriptionHtml, url, imageUrl, authorName,
               authorProfileUrl, publishedAt, publishedAtEpoch, tagsJson, hubsJson, rating,
               commentsCount, NULL AS cachedArticleJson, fetchedAt, sourceOrder
        FROM feed_items WHERE feedId = :feedId
        ORDER BY CASE WHEN sourceOrder IS NULL THEN 1 ELSE 0 END ASC, sourceOrder ASC,
                 COALESCE(publishedAtEpoch, fetchedAt) DESC
        LIMIT $FEED_LIST_LIMIT
        """
    )
    fun getByFeed(feedId: String): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE feedId = :feedId ORDER BY CASE WHEN sourceOrder IS NULL THEN 1 ELSE 0 END ASC, sourceOrder ASC, COALESCE(publishedAtEpoch, fetchedAt) DESC")
    suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity>

    @Query("SELECT MAX(fetchedAt) FROM feed_items WHERE feedId = :feedId")
    suspend fun getNewestFetchedAtByFeed(feedId: String): Long?

    /**
     * Total rows stored for a feed. The feed list itself is capped by [FEED_LIST_LIMIT], so this
     * count is what the UI reports as the real archive size while only the newest rows are held
     * in memory.
     */
    @Query("SELECT COUNT(*) FROM feed_items WHERE feedId = :feedId")
    suspend fun countByFeed(feedId: String): Int

    @Query(
        """
        SELECT id, feedId, title, summary, descriptionHtml, url, imageUrl, authorName,
               authorProfileUrl, publishedAt, publishedAtEpoch, tagsJson, hubsJson, rating,
               commentsCount, NULL AS cachedArticleJson, fetchedAt, sourceOrder
        FROM feed_items
        ORDER BY COALESCE(publishedAtEpoch, fetchedAt) DESC
        LIMIT $FEED_LIST_LIMIT
        """
    )
    fun getAllCached(): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items ORDER BY COALESCE(publishedAtEpoch, fetchedAt) DESC")
    suspend fun getAllCachedOnce(): List<FeedItemEntity>

    /**
     * Paged snapshot of the whole local cache ("Все загруженные"). Optional filters are pushed to
     * SQL so a huge archive can be browsed page by page without mapping every row to domain.
     */
    @Query(
        """
        SELECT * FROM feed_items
        WHERE (:hubFilter IS NULL OR hubsJson LIKE '%' || :hubFilter || '%')
          AND (:tagFilter IS NULL OR tagsJson LIKE '%' || :tagFilter || '%')
          AND (:query IS NULL OR title LIKE '%' || :query || '%'
               OR summary LIKE '%' || :query || '%' OR authorName LIKE '%' || :query || '%'
               OR descriptionHtml LIKE '%' || :query || '%'
               OR tagsJson LIKE '%' || :query || '%' OR hubsJson LIKE '%' || :query || '%')
          AND (:hideRead = 0 OR NOT EXISTS (
              SELECT 1 FROM article_local_state als WHERE als.articleId = feed_items.id AND als.isRead = 1
          ))
        ORDER BY COALESCE(publishedAtEpoch, fetchedAt) DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getAllCachedPaged(
        hubFilter: String?,
        tagFilter: String?,
        query: String?,
        hideRead: Boolean,
        limit: Int,
        offset: Int,
    ): Flow<List<FeedItemEntity>>

    /**
     * Total number of rows matching the same filters as [getAllCachedPaged]; used to decide whether
     * another page exists.
     */
    @Query(
        """
        SELECT COUNT(*) FROM feed_items
        WHERE (:hubFilter IS NULL OR hubsJson LIKE '%' || :hubFilter || '%')
          AND (:tagFilter IS NULL OR tagsJson LIKE '%' || :tagFilter || '%')
          AND (:query IS NULL OR title LIKE '%' || :query || '%'
               OR summary LIKE '%' || :query || '%' OR authorName LIKE '%' || :query || '%'
               OR descriptionHtml LIKE '%' || :query || '%'
               OR tagsJson LIKE '%' || :query || '%' OR hubsJson LIKE '%' || :query || '%')
          AND (:hideRead = 0 OR NOT EXISTS (
              SELECT 1 FROM article_local_state als WHERE als.articleId = feed_items.id AND als.isRead = 1
          ))
        """
    )
    suspend fun countAllCachedPaged(
        hubFilter: String?,
        tagFilter: String?,
        query: String?,
        hideRead: Boolean,
    ): Int

    @Query("SELECT * FROM feed_items WHERE id = :id")
    suspend fun getById(id: String): FeedItemEntity?

    @Query("SELECT * FROM feed_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<FeedItemEntity>

    @Query("SELECT id FROM feed_items WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Query("SELECT id FROM feed_items WHERE id = :id")
    suspend fun hasId(id: String): String?

    @Query("SELECT * FROM feed_items WHERE id = :id")
    fun observeById(id: String): Flow<FeedItemEntity?>

    @Query("SELECT * FROM feed_items WHERE url = :url OR rtrim(url, '/') = rtrim(:url, '/') LIMIT 1")
    suspend fun getByUrl(url: String): FeedItemEntity?

    @Query("SELECT * FROM feed_items WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR descriptionHtml LIKE '%' || :query || '%' OR authorName LIKE '%' || :query || '%' OR tagsJson LIKE '%' || :query || '%' OR hubsJson LIKE '%' || :query || '%' ORDER BY COALESCE(publishedAtEpoch, fetchedAt) DESC")
    suspend fun search(query: String): List<FeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedItemEntity>)

    @Update
    suspend fun update(item: FeedItemEntity)

    @Query("DELETE FROM feed_items WHERE feedId = :feedId AND fetchedAt < :timestamp")
    suspend fun deleteOldByFeed(feedId: String, timestamp: Long)

    @Query("DELETE FROM feed_items WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: String)

    /** Clears only server/cache rows. Local user state is stored in separate tables. */
    @Query("DELETE FROM feed_items")
    suspend fun deleteAll()

    // ---------- Local user state ----------

    @Query("SELECT * FROM article_local_state")
    fun getArticleLocalStates(): Flow<List<ArticleLocalStateEntity>>

    @Query("SELECT * FROM article_local_state")
    suspend fun getArticleLocalStatesOnce(): List<ArticleLocalStateEntity>

    @Query("SELECT * FROM article_local_state WHERE articleId = :articleId")
    suspend fun getArticleLocalState(articleId: String): ArticleLocalStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticleLocalState(state: ArticleLocalStateEntity)

    @Query("SELECT * FROM favorite_articles")
    fun getFavoriteArticles(): Flow<List<FavoriteArticleEntity>>

    @Query("SELECT * FROM favorite_articles")
    suspend fun getFavoriteArticlesOnce(): List<FavoriteArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteArticle(favorite: FavoriteArticleEntity)

    @Query("DELETE FROM favorite_articles WHERE articleId = :articleId")
    suspend fun deleteFavoriteArticle(articleId: String)

    @Query(
        """
        SELECT feed_items.id, feed_items.feedId, feed_items.title, feed_items.summary,
               feed_items.descriptionHtml, feed_items.url, feed_items.imageUrl, feed_items.authorName,
               feed_items.authorProfileUrl, feed_items.publishedAt, feed_items.publishedAtEpoch,
               feed_items.tagsJson, feed_items.hubsJson, feed_items.rating, feed_items.commentsCount,
               NULL AS cachedArticleJson, feed_items.fetchedAt, feed_items.sourceOrder
        FROM feed_items INNER JOIN favorite_articles ON favorite_articles.articleId = feed_items.id
        ORDER BY favorite_articles.createdAt DESC, COALESCE(feed_items.publishedAtEpoch, feed_items.fetchedAt) DESC
        LIMIT $FEED_LIST_LIMIT
        """
    )
    fun getBookmarks(): Flow<List<FeedItemEntity>>

    @Query("SELECT feed_items.* FROM feed_items INNER JOIN favorite_articles ON favorite_articles.articleId = feed_items.id ORDER BY favorite_articles.createdAt DESC, COALESCE(feed_items.publishedAtEpoch, feed_items.fetchedAt) DESC")
    suspend fun getBookmarksOnce(): List<FeedItemEntity>

    @Query("SELECT * FROM favorite_tags")
    fun getFavoriteTags(): Flow<List<FavoriteTagEntity>>

    @Query("SELECT * FROM favorite_tags")
    suspend fun getFavoriteTagsOnce(): List<FavoriteTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteTag(tag: FavoriteTagEntity)

    @Query("DELETE FROM favorite_tags WHERE tagId = :tagId")
    suspend fun deleteFavoriteTag(tagId: String)

    @Query("SELECT * FROM favorite_hubs")
    fun getFavoriteHubs(): Flow<List<FavoriteHubEntity>>

    @Query("SELECT * FROM favorite_hubs")
    suspend fun getFavoriteHubsOnce(): List<FavoriteHubEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteHub(hub: FavoriteHubEntity)

    @Query("DELETE FROM favorite_hubs WHERE hubId = :hubId")
    suspend fun deleteFavoriteHub(hubId: String)

    // ---------- Remote sync state ----------

    @Query("SELECT * FROM sync_state WHERE sourceKey = :sourceKey")
    suspend fun getSyncState(sourceKey: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE sourceKey = :sourceKey")
    fun observeSyncState(sourceKey: String): Flow<SyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

}

/**
 * Max number of rows materialized into the in-memory feed list (see the note on [FeedDao.getByFeed]).
 * 3000 rows keeps the mapped domain list plus its intermediate pipeline copies comfortably inside
 * the 256 MB Java heap; larger hub archives overflowed it and crashed the app with OutOfMemoryError.
 */
internal const val FEED_LIST_LIMIT = 3000
