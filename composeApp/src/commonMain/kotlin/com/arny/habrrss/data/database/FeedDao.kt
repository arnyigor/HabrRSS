package com.arny.habrrss.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items WHERE feedId = :feedId ORDER BY COALESCE(publishedAtEpoch, 0) DESC")
    fun getByFeed(feedId: String): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE feedId = :feedId ORDER BY COALESCE(publishedAtEpoch, 0) DESC")
    suspend fun getByFeedOnce(feedId: String): List<FeedItemEntity>

    @Query("SELECT * FROM feed_items WHERE id = :id")
    suspend fun getById(id: String): FeedItemEntity?

    @Query("SELECT * FROM feed_items WHERE url = :url OR rtrim(url, '/') = rtrim(:url, '/') LIMIT 1")
    suspend fun getByUrl(url: String): FeedItemEntity?

    @Query("SELECT * FROM feed_items WHERE isBookmarked = 1 ORDER BY COALESCE(publishedAtEpoch, 0) DESC")
    fun getBookmarks(): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE isBookmarked = 1 ORDER BY COALESCE(publishedAtEpoch, 0) DESC")
    suspend fun getBookmarksOnce(): List<FeedItemEntity>

    // Fallback LIKE search for compatibility
    @Query("SELECT * FROM feed_items WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR descriptionHtml LIKE '%' || :query || '%' OR authorName LIKE '%' || :query || '%' OR tagsJson LIKE '%' || :query || '%' OR hubsJson LIKE '%' || :query || '%' ORDER BY COALESCE(publishedAtEpoch, 0) DESC")
    suspend fun search(query: String): List<FeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedItemEntity>)

    @Update
    suspend fun update(item: FeedItemEntity)

    @Query("UPDATE feed_items SET isRead = :isRead WHERE id = :id")
    suspend fun updateRead(id: String, isRead: Boolean)

    @Query("UPDATE feed_items SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmark(id: String, isBookmarked: Boolean)

    @Query("DELETE FROM feed_items WHERE feedId = :feedId AND fetchedAt < :timestamp")
    suspend fun deleteOldByFeed(feedId: String, timestamp: Long)

    @Query("DELETE FROM feed_items")
    suspend fun deleteAll()
}