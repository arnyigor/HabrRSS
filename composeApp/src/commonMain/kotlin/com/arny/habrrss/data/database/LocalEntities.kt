package com.arny.habrrss.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Local per-article state. Kept separately from server feed_items rows so refreshes never overwrite
 * read progress.
 */
@Serializable
@Entity(tableName = "article_local_state")
data class ArticleLocalStateEntity(
    @PrimaryKey val articleId: String,
    val isRead: Boolean = false,
    val lastOpenedAt: Long? = null,
    val scrollPosition: Int = 0,
)

/** Local bookmarks. Server article data stays in feed_items; this table stores only user choice. */
@Serializable
@Entity(
    tableName = "favorite_articles",
    indices = [Index(value = ["createdAt"])]
)
data class FavoriteArticleEntity(
    @PrimaryKey val articleId: String,
    val createdAt: Long = 0L,
)

/** Favorite tags selected by user. */
@Serializable
@Entity(tableName = "favorite_tags")
data class FavoriteTagEntity(
    @PrimaryKey val tagId: String,
    val title: String? = null,
    val createdAt: Long = 0L,
)

/** Favorite hubs selected by user. */
@Serializable
@Entity(tableName = "favorite_hubs")
data class FavoriteHubEntity(
    @PrimaryKey val hubId: String,
    val title: String? = null,
    val createdAt: Long = 0L,
)

/** Progress and resume state for long-running remote imports. */
@Serializable
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val sourceKey: String,
    val mode: String,
    val status: String,
    val nextPage: Int,
    val pagesCountSnapshot: Int?,
    val pagesProcessed: Int,
    val receivedCount: Long,
    val uniqueCount: Long,
    val failedPage: Int?,
    val errorCode: String?,
    val startedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)
