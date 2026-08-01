package com.arny.habrrss.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "feed_items",
    indices = [
        Index(value = ["feedId"]),
    ]
)
data class FeedItemEntity(
    @PrimaryKey
    val id: String,
    val feedId: String,
    val title: String,
    val summary: String,
    val descriptionHtml: String?,
    val url: String,
    val imageUrl: String?,
    val authorName: String?,
    val authorProfileUrl: String?,
    val publishedAt: String?,
    val publishedAtEpoch: Long?,
    val tagsJson: String,
    val hubsJson: String,
    val rating: String?,
    val commentsCount: Int?,
    val cachedArticleJson: String? = null,
    val fetchedAt: Long,
)
