package com.arny.habrrss.domain.source

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.PageCursor

/**
 * Unified reference to an article - either by ID or URL.
 * Clarifies the contract between FeedSource.getArticle() and ArticleContentSource.
 */
sealed interface ArticleRef {
    data class ById(val value: String) : ArticleRef
    data class ByUrl(val value: String) : ArticleRef
}

interface FeedSource {
    suspend fun getFeeds(): List<FeedDescriptor>
    suspend fun getItems(feedId: String, page: PageCursor?): FeedPage
    /**
     * @deprecated Use ArticleContentSource.getArticle() instead.
     * This method kept for backward compatibility.
     */
    @Deprecated("Use ArticleContentSource", ReplaceWith("getArticleByUrl(url)"))
    suspend fun getArticle(articleId: String): ArticleContent
    suspend fun getComments(articleId: String): List<CommentNode>
}

/**
 * Dedicated source for fetching full article content.
 * Separates concerns: FeedSource handles RSS feeds, this handles article HTML.
 */
interface ArticleContentSource {
    /**
     * Fetch full article content by URL (canonical URL from RSS item).
     */
    suspend fun getArticleByUrl(url: String): ArticleContent
}

class SourceUnavailableException(message: String) : RuntimeException(message)
